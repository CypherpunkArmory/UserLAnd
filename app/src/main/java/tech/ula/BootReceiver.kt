package tech.ula

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent?.action) {
            val serviceIntent = Intent(context, ServerService::class.java)
            serviceIntent.action = "autostart"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context?.startForegroundService(serviceIntent)
            else
                context?.startService(serviceIntent)
        }
    }
}