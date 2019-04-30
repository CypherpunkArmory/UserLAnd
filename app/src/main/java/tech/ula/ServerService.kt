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
import tech.ula.utils.*

class ServerService : Service() {

    companion object {
        const val SERVER_SERVICE_RESULT = "tech.ula.ServerService.RESULT"
    }

    private val activeSessions: MutableMap<Long, Session> = mutableMapOf()

    private lateinit var broadcaster: LocalBroadcastManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

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

        val action = intent?.action
        when (action) {
            "autostart" -> {
                autoStartSessions()
            }
            "exit" -> {
                stopServiceAndSessions()
            }
            "wakelock" -> {
                acquireWakeLocks()
            }
            "wakerelease" -> {
                releaseWakeLocks()
            }
        }
        return START_STICKY
    }

    /**
     * Fetch all sessions marked to start on boot and start them if they are not already active.
     */
    private fun autoStartSessions() {
        CoroutineScope(Dispatchers.Default).launch {
            val sessions = UlaDatabase.getInstance(this@ServerService).sessionDao().getSessionsAutoStart()
            var startedAnySession = false
            for (session in sessions)
                if (!session.active) {
                    startSession(session, false)
                    startedAnySession = true
                }
            if (startedAnySession)
                acquireWakeLocks()
        }
    }

    fun acquireWakeLocks() {
        // Keep the CPU awake.
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ula:Service")
        wakeLock?.acquire()
        // Keep the wifi awake.
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ula:Service")
        wifiLock?.acquire()
        notificationManager.updateNotification(true)
    }

    fun releaseWakeLocks() {
        try {
            wakeLock?.release()
            wifiLock?.release()
        } catch (ignore: Exception) {
        }
        wakeLock = null
        wifiLock = null
        notificationManager.updateNotification(false)
    }

    fun stopServiceAndSessions() {
        releaseWakeLocks()
        for (session in activeSessions)
            killSession(session.value)
        stopForeground(true)
        stopSelf()
    }

    /**
     * Used in conjunction with manifest attribute `android:stopWithTask="true"`
     * to clean up when app is swiped away.
     *
     * Warning : stopWithTask on the manifest is now false because if true, this method is not called
     *
     * Check official docs at
     * https://developer.android.com/reference/android/app/Service.html#onTaskRemoved(android.content.Intent)
     *
     * If you have set ServiceInfo.FLAG_STOP_WITH_TASK then you will not receive this callback;
     * instead, the service will simply be stopped.
     *
     * When the task is stopped, kill the server only if there are no startOnBoot sessions running.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        var activeBootSessions = false
        for (session in activeSessions)
            if (session.value.startOnBoot) {
                activeBootSessions = true
                break
            }
        if (!activeBootSessions) {
            stopForeground(true)
            stopSelf()
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
        serverUtility.stopService(session)
        removeSession(session)
        session.active = false
        updateSession(session)
    }

    private suspend fun startSession(session: Session, autoStartClient: Boolean = true) {
        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification(wakeLock != null))
        session.pid = serverUtility.startServer(session)

        // Only wait for the server if we are interested in launching the client
        if (autoStartClient) {
            while (!serverUtility.isServerRunning(session)) {
                delay(500)
            }
        }

        session.active = true
        updateSession(session)

        if (autoStartClient)
            startClient(session)

        activeSessions[session.pid] = session
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
        sendSessionActivatedBroadcast()
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