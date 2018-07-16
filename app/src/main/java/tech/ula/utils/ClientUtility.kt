package tech.ula.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.jetbrains.anko.longToast
import org.jetbrains.anko.runOnUiThread
import tech.ula.R
import tech.ula.model.entities.Session

class ClientUtility(private val context: Context) {

    fun startClient(session: Session) {
        when(session.clientType) {
            "ConnectBot" -> startSshClient(session, "org.connectbot")
            "bVNC" -> startVncClient(session, "com.iiordanov.freebVNC")
            "xsdl" -> return // TODO
            else -> return
        }
    }

    private fun startSshClient(session: Session, packageName: String) {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:${session.port}")
        connectBotIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (clientIsPresent(connectBotIntent)) {
            context.startActivity(connectBotIntent)
        } else {
            getClient(packageName)
        }
    }

    private fun startVncClient(session: Session, packageName: String) {
        val bVncIntent = Intent()
        bVncIntent.action = "android.intent.action.VIEW"
        bVncIntent.type = "application/vnd.vnc"
        bVncIntent.data = Uri.parse("vnc://127.0.0.1:5951/?VncPassword=${session.password}")
        bVncIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (clientIsPresent(bVncIntent)) {
            context.startActivity(bVncIntent)
        }
        else {
            getClient(packageName)
        }
    }

    private fun clientIsPresent(intent: Intent): Boolean {
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        return(activities.size > 0)
    }

    private fun getClient(packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.runOnUiThread { longToast(R.string.download_client_app) }
        context.startActivity(intent)
    }
}