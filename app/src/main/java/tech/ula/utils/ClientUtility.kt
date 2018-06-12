package tech.ula.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import tech.ula.database.models.Session

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
        // Build the intent
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:${session.port}/#userland")

        // Verify it resolves
        val activities = context.packageManager.queryIntentActivities(connectBotIntent, 0)
        val isIntentSafe = activities.size > 0

        // Start an activity if it's safe
        if (isIntentSafe) {
            context.startActivity(connectBotIntent)
        } else {
            val appPackageName = "org.connectbot"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        }
    }


}