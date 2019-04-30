package tech.ula.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import tech.ula.MainActivity
import tech.ula.R
import tech.ula.ServerService

class NotificationUtility(val context: Context) {

    companion object {
        const val serviceNotificationId = 1000
    }

    private val serviceNotificationChannelId = context.getString(R.string.services_notification_channel_id)

    private val serviceNotificationTitle = context.getString(R.string.service_notification_title)
    private val serviceNotificationDescription = context.getString(R.string.service_notification_description)

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.services_notification_channel_name)
            val description = context.getString(R.string.services_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(serviceNotificationChannelId, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildPersistentServiceNotification(wakeLockHeld : Boolean = false): Notification {
        val sessionListIntent = Intent(context, MainActivity::class.java)
        val pendingSessionListIntent = PendingIntent
                .getActivity(context, 0, sessionListIntent, 0)

        var title = serviceNotificationTitle
        if (wakeLockHeld) title += " (Wake Locked)"

        val builder = NotificationCompat.Builder(context, serviceNotificationChannelId)
                .setSmallIcon(R.drawable.ic_stat_icon)
                .setContentTitle(title)
                .setContentText(serviceNotificationDescription)
                .setPriority(if (wakeLockHeld) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(false)
                .setContentIntent(pendingSessionListIntent)

        val exitIntent = Intent(context, ServerService::class.java).setAction("exit")
        builder.addAction(android.R.drawable.ic_delete,"Exit", PendingIntent.getService(context, 0, exitIntent, 0))

        val newWakeAction = if (wakeLockHeld) "wakerelease" else "wakelock"
        val toggleWakeLockIntent = Intent(context, ServerService::class.java).setAction(newWakeAction)
        val actionTitle = if (wakeLockHeld) "Release Wakelock" else "Acquire Wakelock"
        val actionIcon = if (wakeLockHeld) android.R.drawable.ic_lock_idle_lock else android.R.drawable.ic_lock_lock
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(context, 0, toggleWakeLockIntent, 0))

        return builder.build()
    }

    fun updateNotification(wakeLockHeld : Boolean = false){
        notificationManager.notify(serviceNotificationId, buildPersistentServiceNotification(wakeLockHeld))
    }
}