package tech.ula

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    private val testAction = "tech.ula.intent.action.TEST_BOOT_COMPLETED"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent?.action || testAction == intent?.action) {
            val i = Intent(context, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_CLEAR_TASK
            i.putExtra("autostart", true)
            context?.startActivity(i)
        }
    }
}