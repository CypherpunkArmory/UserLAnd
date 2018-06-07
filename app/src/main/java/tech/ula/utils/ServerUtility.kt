package tech.ula.utils

import android.content.Context
import tech.ula.database.models.Session
import tech.ula.utils.*

class ServerUtility(private val context: Context) {

    fun Process.pid(): Long {
        return this.toString().substringAfter("=").substringBefore(",").trim().toLong()
    }

    private val execUtility by lazy {
        ExecUtility(context)
    }

    private val fileUtility by lazy {
        FileUtility(context)
    }

    fun startServer(session: Session): Long {
        return when(session.serviceType) {
            "ssh" -> startSSHServer(session)
            "vnc" -> 0 // TODO
            "xsdl" -> 0 // TODO
            else -> 0
        }
    }

    private fun startSSHServer(session: Session): Long {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "../support/execInProot.sh /bin/bash -c /support/startSSHServer.sh"
        val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, false)
        return process.pid()
    }

    fun stopService(session: Session) {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "${fileUtility.getSupportDirPath()}/killProcTree.sh ${session.pid}"
        execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
    }

    fun isServerRunning(session: Session): Boolean {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "${fileUtility.getSupportDirPath()}/isServerInProcTree.sh ${session.pid}"
        val process = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
        if (process.exitValue() == 1)  //isServerInProcTree returns a 1 if it did't find a server
            return false
        return true
    }

}