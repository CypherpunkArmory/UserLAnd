package tech.ula.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import tech.ula.database.models.Session
import tech.ula.utils.*

class ClientUtility(private val context: Context) {

    fun startClient(session: Session) {
        if (session.clientType == "none") {
            return
        } else if (session.clientType == "ConnectBot") {
            return startConnectBotClient(session)
        } else if (session.clientType == "bVNC") {
            //TODO: support vnc server
            return
        } else if (session.serviceType == "xsdl") {
            //TODO: support xsdl server
            return
        }
        return
    }

    private fun startConnectBotClient(session: Session) {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://" + session.username + "@localhost:" + session.port.toString() + "/#userland")
        context.startActivity(connectBotIntent)
    }


}