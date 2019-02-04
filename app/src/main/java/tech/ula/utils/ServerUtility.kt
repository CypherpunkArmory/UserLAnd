package tech.ula.utils

import tech.ula.model.entities.Session
import java.io.File

class ServerUtility(
    private val applicationFilesDirPath: String,
    private val execUtility: ExecUtility,
    private val logger: LogUtility = LogUtility()
) {

    fun Process.pid(): Long {
        return this.toString().substringAfter("pid=").substringBefore(",").substringBefore("]").trim().toLong()
    }

    fun Session.pidRelativeFilePath(): String {
        return when (this.serviceType) {
            "ssh" -> "/run/dropbear.pid"
            "vnc" -> "/home/${this.username}/.vnc/localhost:${this.port}.pid"
            "xsdl" -> "/tmp/xsdl.pidfile"
            else -> "error"
        }
    }

    fun Session.pidFilePath(): String {
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

    private fun startSSHServer(session: Session): Long {
        val targetDirectoryName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "../support/execInProot.sh /bin/bash -c /support/startSSHServer.sh"
        return try {
            val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, doWait = false)
            process.pid()
        } catch (err: Exception) {
            logger.logRuntimeErrorForCommand(functionName = "startSSHServer", command = command, err = err)
            -1
        }
    }

    private fun startVNCServer(session: Session): Long {
        val targetDirectoryName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "../support/execInProot.sh /bin/bash -c /support/startVNCServer.sh"
        return try {
            val env = HashMap<String, String>()
            env["INITIAL_USERNAME"] = session.username
            env["INITIAL_VNC_PASSWORD"] = session.vncPassword
            val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, doWait = false, environmentVars = env)
            process.pid()
        } catch (err: Exception) {
            logger.logRuntimeErrorForCommand(functionName = "startVNCServer", command = command, err = err)
            -1
        }
    }

    private fun setDisplayNumberAndStartTwm(session: Session): Long {
        val targetDirectoryName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "../support/execInProot.sh /bin/bash -c '/support/startXSDLServer.sh'"
        return try {
            val env = HashMap<String, String>()
            env["INITIAL_USERNAME"] = session.username
            env["DISPLAY"] = ":4721"
            env["PULSE_SERVER"] = "127.0.0.1:4721"
            val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, doWait = false, environmentVars = env)
            process.pid()
        } catch (err: Exception) {
            logger.logRuntimeErrorForCommand(functionName = "startXSDLServer", command = command, err = err)
            -1
        }
    }

    fun stopService(session: Session) {
        val targetDirectoryName = session.filesystemId.toString()

        val command = "../support/killProcTree.sh ${session.pid} ${session.pid()}"
        try {
            execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
        } catch (err: Exception) {
            logger.logRuntimeErrorForCommand(functionName = "stopService", command = command, err = err)
        }
    }

    fun isServerRunning(session: Session): Boolean {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "../support/isServerInProcTree.sh ${session.pid()}"
        try {
            if (session.serviceType == "xsdl")
                return true
            val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
            if (process.exitValue() != 0) // isServerInProcTree returns a 1 if it didn't find a server
                return false
        } catch (err: Exception) {
            logger.logRuntimeErrorForCommand(functionName = "isServerRunning", command = command, err = err)
        }
        return true
    }
}