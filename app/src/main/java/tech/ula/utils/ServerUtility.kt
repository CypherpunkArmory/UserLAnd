package tech.ula.utils

import tech.ula.model.entities.Session
import java.io.File

class ServerUtility(private val execUtility: ExecUtility, private val fileUtility: FileUtility) {

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
        return fileUtility.getFilesDirPath() + "/" + this.filesystemId.toString() + this.pidRelativeFilePath()
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
        val targetDirectoryName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "../support/execInProot.sh /bin/bash -c /support/startSSHServer.sh"
        val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, doWait = false)
        return process.pid()
    }

    private fun startVNCServer(session: Session): Long {
        val targetDirectoryName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "../support/execInProot.sh /bin/bash -c /support/startVNCServer.sh"
        val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, doWait = false)
        return process.pid()
    }

    fun stopService(session: Session) {
        val targetDirectoryName = session.filesystemId.toString()

        val command = "../support/killProcTree.sh ${session.pid} ${session.pid()}"
        execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
    }

    fun isServerRunning(session: Session): Boolean {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "../support/isServerInProcTree.sh ${session.pid()}"
        val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
        if (process.exitValue() != 0) // isServerInProcTree returns a 1 if it did't find a server
            return false
        return true
    }
}