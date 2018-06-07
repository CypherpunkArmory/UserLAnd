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

    fun startServer(session: Session): Long {
        if (session.serviceType == "ssh") {
            return startSSHServer(session)
        } else if (session.serviceType == "vnc") {
            //TODO: support vnc server
            return 0
        } else if (session.serviceType == "xsdl") {
            //TODO: support xsdl server
            return 0
        }
        return 0
    }

    private fun startSSHServer(session: Session): Long {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "../support/execInProot /bin/bash -c /support/startSSHServer.sh"
        val proc = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, false)
        return proc.pid()
    }

    fun stopService(session: Session) {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "/data/user/0/tech.userland.userland/files/support/killProcTree.sh " + session.pid.toString()
        execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
    }

    fun isServerRunning(session: Session): Boolean {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "/data/user/0/tech.userland.userland/files/support/isServerInProcTree.sh " + session.pid.toString()
        val proc = execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
        if (proc.exitValue() == 1)  //isServerInProcTree returns a 1 if it did't find a server
            return false
        return true
    }

}