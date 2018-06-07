package tech.ula.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import tech.ula.database.models.Session
import tech.ula.utils.*

class ClientUtility(private val context: Context) {

    fun startClient(session: Session) {
        when(session.clientType) {
            "ConnectBot" -> startConnectBotClient(session)
            "bVNC" -> return // TODO
            "xsdl" -> return // TODO
            else -> return
        }
    }

    private fun startConnectBotClient(session: Session) {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:${session.port}/#userland")
        context.startActivity(connectBotIntent)
    }


}