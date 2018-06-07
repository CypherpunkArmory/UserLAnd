package tech.ula

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tech.ula.database.models.Session
import tech.ula.utils.NotificationUtility
import tech.ula.utils.ServerUtility

class ServerService : Service() {
    private val activeSessions: MutableMap<Long, Session> = HashMap()
    private val serverManager: ServerUtility by lazy {
        ServerUtility(this)
    }

    private val notificationManager: NotificationUtility by lazy {
        NotificationUtility(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val intentType = intent.getStringExtra("type")
        val session: Session = intent.getParcelableExtra("session")
        when(intentType) {
            "start" -> startSession(session)
            "kill" -> killSession(session)
        }
        return Service.START_STICKY
    }

    private fun startSession(session: Session) {
        val pid = serverManager.startServer(session)
        activeSessions[pid] = session
        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification())
    }

    private fun killSession(session: Session) {
        val pid = session.pid
        activeSessions.remove(pid)
        if(activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }
}