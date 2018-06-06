package tech.ula.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import tech.ula.R
import tech.ula.SessionListActivity

class NotificationUtility(val context: Context) {
    private val serviceNotificationId = 1000
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

    fun startPersistentServiceNotification {
        val sessionListIntent = Intent(context, SessionListActivity::class.java)
        val pendingSessionListIntent = PendingIntent
                .getActivity(context, 0, sessionListIntent, 0)

        val builder = NotificationCompat.Builder(context, serviceNotificationChannelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(serviceNotificationTitle)
                .setContentText(serviceNotificationDescription)
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("UserLAnd is currently running a background service."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .setContentIntent(pendingSessionListIntent)

        notificationManager.notify(serviceNotificationId, builder.build())
    }
}