package tech.ula.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import tech.ula.database.models.Session

class ClientUtility(private val context: Context) {

    fun startClient(session: Session) {
        when(session.clientType) {
            "ConnectBot" -> startConnectBotClient(session)
            "bVNC" -> startBVncClient(session)
            "xsdl" -> return // TODO
            else -> return
        }
    }

    private fun startConnectBotClient(session: Session) {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:${session.port}/#userland")

        if (clientIsPresent(connectBotIntent)) {
            context.startActivity(connectBotIntent)
        } else {
            getClient("org.connectbot")
        }
    }

    private fun startBVncClient(session: Session) {
        val bVncIntent = Intent()
        bVncIntent.action = "android.intent.action.VIEW"
        bVncIntent.type = "application/vnd.vnc"
        bVncIntent.data = Uri.parse("vnc://127.0.0.1:5951/?VncPassword=${session.password}")

        if(clientIsPresent(bVncIntent)) {
            context.startActivity(bVncIntent)
        }
        else {
            getClient("com.iiordanov.freebVNC")
        }
    }

    private fun clientIsPresent(intent: Intent): Boolean {
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        return(activities.size > 0)
    }

    private fun getClient(appPackageName: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
    }


}