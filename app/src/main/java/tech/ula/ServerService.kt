package tech.ula

import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.anko.defaultSharedPreferences
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class ServerService : Service() {

    companion object {
        const val SERVER_SERVICE_RESULT = "tech.ula.ServerService.RESULT"
        const val WAKELOCK_TAG = "Ula:ServerService"
    }

    private val activeSessions: MutableMap<Long, Session> = mutableMapOf()

    private lateinit var broadcaster: LocalBroadcastManager

    private val wakeLock: PowerManager.WakeLock by lazy {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
    }

    private val wifiLock: WifiManager.WifiLock by lazy {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WAKELOCK_TAG)
    }

    private val notificationManager: NotificationUtility by lazy {
        NotificationUtility(this)
    }

    private val busyboxExecutor by lazy {
        val externalStorage = Environment.getExternalStorageDirectory()
        BusyboxExecutor(this.filesDir, externalStorage, DefaultPreferences(this.defaultSharedPreferences))
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(this.filesDir.path, busyboxExecutor)
    }

    private val serverUtility by lazy {
        ServerUtility(this.filesDir.path, busyboxExecutor)
    }

    override fun onCreate() {
        broadcaster = LocalBroadcastManager.getInstance(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val intentType = intent?.getStringExtra("type")
        when (intentType) {
            "start" -> {
                val coroutineScope = CoroutineScope(Dispatchers.Default)
                val session: Session = intent.getParcelableExtra("session")
                coroutineScope.launch { startSession(session) }
            }
            "stopApp" -> {
                val app: App = intent.getParcelableExtra("app")
                stopApp(app)
            }
            "restartRunningSession" -> {
                val session: Session = intent.getParcelableExtra("session")
                startClient(session)
            }
            "kill" -> {
                val session: Session = intent.getParcelableExtra("session")
                killSession(session)
            }
            "filesystemIsBeingDeleted" -> {
                val filesystemId: Long = intent.getLongExtra("filesystemId", -1)
                cleanUpFilesystem(filesystemId)
            }
        }

        when (intent?.action) {
            "autostart" -> autoStartSession()
            "exit" -> stopServiceAndSessions()
            "wakelock" -> acquireWakeLocks()
            "wakerelease" -> releaseWakeLocks()
        }
        return Service.START_STICKY
    }

    /**
     * Get the startOnBoot session and start it
     */
    private fun autoStartSession() {
        CoroutineScope(Dispatchers.Default).launch {
            val sessionName = SessionPreferences(defaultSharedPreferences).getStartOnBootSession()
            if (sessionName.isNotEmpty()) {
                val session = UlaDatabase.getInstance(this@ServerService).sessionDao().getSessionByName(sessionName)
                startSession(session)
                acquireWakeLocks()
            }
        }
    }

    fun acquireWakeLocks() {
        wakeLock.acquire()
        wifiLock.acquire()
        notificationManager.updateNotification(wakeLockHeld = wakeLock.isHeld)
    }

    fun releaseWakeLocks() {
        if (wakeLock.isHeld)
            wakeLock.release()
        if (wifiLock.isHeld)
            wifiLock.release()
        notificationManager.updateNotification(wakeLockHeld = wakeLock.isHeld)
    }

    private fun stopServiceAndSessions() {
        releaseWakeLocks()
        for (session in activeSessions)
            killSession(session.value)
        stopForeground(true)
        stopSelf()
    }

    /**
     * Used in conjunction with manifest attribute `android:stopWithTask="false"`
     * to clean up when app is swiped away.
     * Terminate service if no wakelock is acquired
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!wakeLock.isHeld) {
            stopServiceAndSessions()
        }
    }

    private fun removeSession(session: Session) {
        activeSessions.remove(session.pid)
        if (activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateSession(session: Session) = CoroutineScope(Dispatchers.Default).launch {
        UlaDatabase.getInstance(this@ServerService).sessionDao().updateSession(session)
    }

    private fun killSession(session: Session) {
        if (session.pid > 0)
            serverUtility.stopService(session)
        removeSession(session)
        session.active = false
        updateSession(session)
    }

    private suspend fun startSession(session: Session) {
        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification(wakeLockHeld = wakeLock.isHeld))

        if (session.startServer) {
            session.pid = serverUtility.startServer(session)
            while (!serverUtility.isServerRunning(session)) {
                delay(500)
            }
        } else
            session.pid = 0

        serverUtility.executeStartCommand(session)

        session.active = true
        updateSession(session)

        if (session.startClient)
            startClient(session)

        activeSessions[session.pid] = session
        sendSessionActivatedBroadcast()
    }

    private fun stopApp(app: App) {
        val appSessions = activeSessions.filter { (_, session) ->
            session.name == app.name
        }
        appSessions.forEach { (_, session) ->
            killSession(session)
        }
    }

    private fun startClient(session: Session) {
        when (session.serviceType) {
            "ssh" -> startSshClient(session, "org.connectbot")
            "vnc" -> startVncClient(session, "com.iiordanov.freebVNC")
            "xsdl" -> startXsdlClient("x.org.server")
            else -> sendDialogBroadcast("unhandledSessionServiceType")
        }
    }

    private fun startSshClient(session: Session, packageName: String) {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:${session.port}/#userland")
        connectBotIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        startActivity(connectBotIntent)
    }

    private fun startVncClient(session: Session, packageName: String) {
        val bVncIntent = Intent()
        bVncIntent.action = "android.intent.action.VIEW"
        bVncIntent.type = "application/vnd.vnc"
        bVncIntent.data = Uri.parse("vnc://127.0.0.1:5951/?VncUsername=${session.username}&VncPassword=${session.vncPassword}")
        bVncIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (clientIsPresent(bVncIntent)) {
            this.startActivity(bVncIntent)
        } else {
            getClient(packageName)
        }
    }

    private fun startXsdlClient(packageName: String) {
        val xsdlIntent = Intent()
        xsdlIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        xsdlIntent.data = Uri.parse("x11://give.me.display:4721")

        if (clientIsPresent(xsdlIntent)) {
            startActivity(xsdlIntent)
        } else {
            getClient(packageName)
        }
    }

    private fun clientIsPresent(intent: Intent): Boolean {
        val activities = packageManager.queryIntentActivities(intent, 0)
        return (activities.size > 0)
    }

    private fun getClient(packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            this.startActivity(intent)
        } catch (err: ActivityNotFoundException) {
            sendDialogBroadcast("playStoreMissingForClient")
        }
    }

    private fun cleanUpFilesystem(filesystemId: Long) {
        // TODO This could potentially be handled by the main activity (viewmodel) now
        if (filesystemId == (-1).toLong()) {
            val exception = IllegalStateException("Did not receive filesystemId")
            AcraWrapper().logException(exception)
            throw exception
        }

        activeSessions.values.filter { it.filesystemId == filesystemId }
                .forEach { killSession(it) }

        CoroutineScope(Dispatchers.Main).launch { filesystemUtility.deleteFilesystem(filesystemId) }
    }

    private fun sendSessionActivatedBroadcast() {
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "sessionActivated")
        broadcaster.sendBroadcast(intent)
    }

    private fun sendDialogBroadcast(type: String) {
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "dialog")
                .putExtra("dialogType", type)
        broadcaster.sendBroadcast(intent)
    }
}