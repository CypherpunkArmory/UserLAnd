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
            "xsdl" -> "error" // TODO
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
            "xsdl" -> 0 // TODO
            else -> 0
        }
    }

    private fun deletePidFile(session: Session) {
        val pidFile = File(session.pidFilePath())
        if (pidFile.exists()) pidFile.delete()
    }

    private fun startSSHServer(session: Session): Long {
        var processPid = -1L
        val targetDirectoryName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "../support/execInProot.sh /bin/bash -c /support/startSSHServer.sh"
        try {
            val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, doWait = false)
            processPid = process.pid()
        } catch (err: Exception) {
            logger.runtimeErrorForCommand(functionName = "startSSHServer", command = command, err = err)
        }
        return processPid
    }

    private fun startVNCServer(session: Session): Long {
        var processPid = -1L
        val targetDirectoryName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "../support/execInProot.sh /bin/bash -c /support/startVNCServer.sh"
        try {
            val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, doWait = false)
            processPid = process.pid()
        } catch (err: Exception) {
            logger.runtimeErrorForCommand(functionName = "startVNCServer", command = command, err = err)
        }
        return processPid
    }

    fun stopService(session: Session) {
        val targetDirectoryName = session.filesystemId.toString()

        val command = "../support/killProcTree.sh ${session.pid} ${session.pid()}"
        try {
            execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
        } catch (err: Exception) {
            logger.runtimeErrorForCommand(functionName = "stopService", command = command, err = err)
        }
    }

    fun isServerRunning(session: Session): Boolean {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "../support/isServerInProcTree.sh ${session.pid()}"
        try {
            val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
            if (process.exitValue() != 0) // isServerInProcTree returns a 1 if it didn't find a server
                return false
        } catch (err: Exception) {
            logger.runtimeErrorForCommand(functionName = "isServerRunning", command = command, err = err)
        }
        return true
    }
}