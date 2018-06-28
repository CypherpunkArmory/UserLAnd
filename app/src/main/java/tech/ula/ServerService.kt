package tech.ula

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tech.ula.utils.NotificationUtility

class ServerService : Service() {
    private val activeSessions: ArrayList<Long> = ArrayList()

    private val notificationManager: NotificationUtility by lazy {
        NotificationUtility(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val intentType = intent.getStringExtra("type")
        val pid = intent.getLongExtra("pid", -1)
        if(pid != (-1).toLong()) {
            when (intentType) {
                "start" -> startSession(pid)
                "kill" -> killSession(pid)
            }
        }
        return Service.START_STICKY
    }

    // Used in conjunction with manifest attribute `android:stopWithTask="true"`
    // to clean up when app is swiped away.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(true)
        stopSelf()
    }

    private fun startSession(pid: Long) {
        activeSessions.add(pid)
        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification())
    }

    private fun killSession(pid: Long) {
        activeSessions.remove(pid)
        if(activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }
}