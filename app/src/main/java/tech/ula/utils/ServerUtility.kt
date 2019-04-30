package tech.ula.utils

import tech.ula.model.entities.Session
import java.io.File

class ServerUtility(
    private val applicationFilesDirPath: String,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: LogUtility = LogUtility()
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
            "ssh" -> "/run/dropbear.pid"
            "vnc" -> "/home/${this.username}/.vnc/localhost:${this.port}.pid"
            "xsdl" -> "/tmp/xsdl.pidfile"
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
        if (session.startOnBoot)
            runBootScript(session)
        return when (session.serviceType) {
            "ssh" -> startSSHServer(session)
            "vnc" -> startVNCServer(session)
            "xsdl" -> setDisplayNumberAndStartTwm(session)
            else -> 0
        }
    }

    private fun deletePidFile(session: Session) {
        val pidFile = File(session.pidFilePath())
        if (pidFile.exists()) pidFile.delete()
    }

    /**
     * Execute the boot script, should be called only when the session has the startOnBoot flag
     * Users can use this to add their own custom commands to this script.
     */
    private fun runBootScript(session: Session) {
        val filesystemDirName = session.filesystemId.toString()
        // TODO create new session parameter and turn this into a user-defined command
        val command = "/support/autostart.sh"
        busyboxExecutor.executeProotCommand(command, filesystemDirName, false)
    }

    private fun startSSHServer(session: Session): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "/support/startSSHServer.sh"
        val result = busyboxExecutor.executeProotCommand(command, filesystemDirName, false)
        return when (result) {
            is OngoingExecution -> result.process.pid()
            is FailedExecution -> {
                logger.logRuntimeErrorForCommand(functionName = "startSSHServer", command = command, err = result.reason)
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
                logger.logRuntimeErrorForCommand(functionName = "startVNCServer", command = command, err = result.reason)
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
                logger.logRuntimeErrorForCommand(functionName = "setDisplayNumberAndStartTwm", command = command, err = result.reason)
                -1
            }
            else -> -1
        }
    }

    fun stopService(session: Session) {
        val command = "support/killProcTree.sh ${session.pid} ${session.pid()}"
        val result = busyboxExecutor.executeCommand(command)
        if (result is FailedExecution) {
            logger.logRuntimeErrorForCommand(functionName = "stopService", command = command, err = result.reason)
        }
    }

    fun isServerRunning(session: Session): Boolean {
        val command = "support/isServerInProcTree.sh ${session.pid()}"
        // The server itself is run by a third-party, so we can consider this to always be true.
        // The third-party app is responsible for handling errors starting their server.
        if (session.serviceType == "xsdl") return true
        val result = busyboxExecutor.executeCommand(command)
        return when (result) {
            is SuccessfulExecution -> true
            is FailedExecution -> {
                logger.logRuntimeErrorForCommand(functionName = "isServerRunning", command = command, err = result.reason)
                false
            }
            else -> false
        }
    }
}