package tech.ula.utils

import android.content.Context
import tech.ula.database.models.Session
import tech.ula.utils.*

class ServerUtility(private val context: Context) {

    fun Process.pid(): Long {
        return this.toString().substringAfter("=").substringBefore(",").trim().toLong()
    }

    private val fileManager by lazy {
        FileUtility(context)
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
//        val targetDirectoryName = session.filesystemId.toString()
//        val command = "../support/execInProot /bin/bash -c /support/startSSHServer.sh"
//        val proc = fileManager.wrapWithBusyboxAndExecute(targetDirectoryName, command)
//        return proc.pid()
        val command = arrayOf("../support/execInProot", "/bin/bash", "-c", "/support/startSSHServer.sh")
        val env = arrayOf<String>()
        val dir = fileManager.createAndGetDirectory(session.filesystemId.toString())
        return Runtime.getRuntime().exec(command, env, dir).pid()
    }

    fun stopService(session: Session) {
        val targetDirectoryName = session.filesystemId.toString()
        val command = "/data/user/0/tech.userland.userland/files/support/killProcTree.sh " + session.pid.toString()
        fileManager.wrapWithBusyboxAndExecute(targetDirectoryName, command)
    }

}