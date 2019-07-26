package tech.ula.utils

import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.Session
import java.io.File

class LocalServerManager(
    private val applicationFilesDirPath: String,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: Logger = SentryLogger()
) {

    fun Process.pid(): Long {
        return this.toString()
                .substringAfter("pid=")
                .substringBefore(",")
                .substringBefore("]")
                .trim().toLong()
    }

    private fun Session.pidRelativeFilePath(): String {
        return when (this.serviceType) {
            ServiceType.Ssh -> "/run/dropbear.pid"
            ServiceType.Vnc -> "/home/${this.username}/.vnc/localhost:${this.port}.pid"
            ServiceType.Xsdl -> "/tmp/xsdl.pidfile"
            else -> "error"
        }
    }

    private fun Session.pidFilePath(): String {
        return "$applicationFilesDirPath/${this.filesystemId}${this.pidRelativeFilePath()}"
    }

    fun Session.pid(): Long {
        val pidFile = File(this.pidFilePath())
        if (!pidFile.exists()) return -1
        return try {
            pidFile.readText().trim().toLong()
        } catch (e: Exception) {
            -1
        }
    }

    fun startServer(session: Session): Long {
        return when (session.serviceType) {
            ServiceType.Ssh -> startSSHServer(session)
            ServiceType.Vnc -> startVNCServer(session)
            ServiceType.Xsdl -> setDisplayNumberAndStartTwm(session)
            else -> 0
        }
    }

    private fun deletePidFile(session: Session) {
        val pidFile = File(session.pidFilePath())
        if (pidFile.exists()) pidFile.delete()
    }

    private fun startSSHServer(session: Session): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "/support/startSSHServer.sh"
        val result = busyboxExecutor.executeProotCommand(command, filesystemDirName, false)
        return when (result) {
            is OngoingExecution -> result.process.pid()
            is FailedExecution -> {
                val details = "func: startSshServer err: ${result.reason}"
                val breadcrumb = UlaBreadcrumb("LocalServerManager", BreadcrumbType.RuntimeError, details)
                logger.addBreadcrumb(breadcrumb)
                -1
            }
            else -> -1
        }
    }

    private fun startVNCServer(session: Session): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "/support/startVNCServer.sh"
        val env = HashMap<String, String>()
        env["INITIAL_USERNAME"] = session.username
        env["INITIAL_VNC_PASSWORD"] = session.vncPassword
        env["DIMENSIONS"] = session.geometry

        val result = busyboxExecutor.executeProotCommand(
                command,
                filesystemDirName,
                commandShouldTerminate = false,
                env = env)
        return when (result) {
            is OngoingExecution -> result.process.pid()
            is FailedExecution -> {
                val details = "func: startVncServer err: ${result.reason}"
                val breadcrumb = UlaBreadcrumb("LocalServerManager", BreadcrumbType.RuntimeError, details)
                logger.addBreadcrumb(breadcrumb)
                -1
            }
            else -> -1
        }
    }

    private fun setDisplayNumberAndStartTwm(session: Session): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "/support/startXSDLServer.sh"
        val env = HashMap<String, String>()
        env["INITIAL_USERNAME"] = session.username
        env["DISPLAY"] = ":4721"
        env["PULSE_SERVER"] = "127.0.0.1:4721"
        val result = busyboxExecutor.executeProotCommand(
                command,
                filesystemDirName,
                commandShouldTerminate = false,
                env = env)
        return when (result) {
            is OngoingExecution -> result.process.pid()
            is FailedExecution -> {
                val details = "func: setDisplayNumberAndStartTwm err: ${result.reason}"
                val breadcrumb = UlaBreadcrumb("LocalServerManager", BreadcrumbType.RuntimeError, details)
                logger.addBreadcrumb(breadcrumb)
                -1
            }
            else -> -1
        }
    }

    fun stopService(session: Session) {
        val command = "support/killProcTree.sh ${session.pid} ${session.pid()}"
        val result = busyboxExecutor.executeScript(command)
        if (result is FailedExecution) {
            val details = "func: stopService err: ${result.reason}"
            val breadcrumb = UlaBreadcrumb("LocalServerManager", BreadcrumbType.RuntimeError, details)
            logger.addBreadcrumb(breadcrumb)
        }
    }

    fun isServerRunning(session: Session): Boolean {
        val command = "support/isServerInProcTree.sh ${session.pid()}"
        // The server itself is run by a third-party, so we can consider this to always be true.
        // The third-party app is responsible for handling errors starting their server.
//        if (session.serviceType == "xsdl") return true
        val result = busyboxExecutor.executeScript(command)
        return when (result) {
            is SuccessfulExecution -> true
            is FailedExecution -> {
                val details = "func: isServerRunning err: ${result.reason}"
                val breadcrumb = UlaBreadcrumb("LocalServerManager", BreadcrumbType.RuntimeError, details)
                logger.addBreadcrumb(breadcrumb)
                false
            }
            else -> false
        }
    }
}