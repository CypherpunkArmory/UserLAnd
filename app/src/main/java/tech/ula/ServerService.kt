package tech.ula

import android.app.DownloadManager
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class ServerService : Service() {

    companion object {
        const val SERVER_SERVICE_RESULT = "tech.ula.ServerService.RESULT"
    }

    private val activeSessions: MutableMap<Long, Session> = mutableMapOf()

    private lateinit var broadcaster: LocalBroadcastManager


    private val notificationManager: NotificationUtility by lazy {
        NotificationUtility(this)
    }

    private val execUtility by lazy {
        val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        ExecUtility(this.filesDir.path, externalStoragePath, DefaultPreferences(this.defaultSharedPreferences))
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(this.filesDir.path, execUtility)
    }

    private val serverUtility by lazy {
        ServerUtility(this.filesDir.path, execUtility)
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
                val session: Session = intent.getParcelableExtra("session")
                startSession(session)
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

        return Service.START_STICKY
    }

    // Used in conjunction with manifest attribute `android:stopWithTask="true"`
    // to clean up when app is swiped away.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(true)
        stopSelf()
    }

    private fun removeSession(session: Session) {
        activeSessions.remove(session.pid)
        if (activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateSession(session: Session) {
        doAsync { UlaDatabase.getInstance(this@ServerService).sessionDao().updateSession(session) }
    }

    private fun killSession(session: Session) {
        serverUtility.stopService(session)
        removeSession(session)
        session.active = false
        updateSession(session)
    }

    private fun startSession(session: Session) = runBlocking {
        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification())
        val updatedSession = asyncAwait {
            session.pid = serverUtility.startServer(session)

            while (!serverUtility.isServerRunning(session)) {
                delay(500)
            }

            session
        }

        updatedSession.active = true
        updateSession(updatedSession)
        startClient(updatedSession)
        activeSessions[updatedSession.pid] = updatedSession
//        lastActivatedSession = session
//        lastActivatedFilesystem = filesystem
//
//        progressBarUpdater(getString(R.string.progress_bar_start_step), "")
//        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification())
//
//        val assetRepository = AssetRepository(BuildWrapper().getArchType(),
//                filesystem.distributionType,
//                this.filesDir.path,
//                timestampPreferences,
//                assetPreferences)
//
//        val sessionController = SessionController(assetRepository, filesystemUtility, assetPreferences)
//
//        launch(CommonPool) {
//
//            progressBarUpdater(getString(R.string.progress_fetching_asset_lists), "")
//            val assetLists = asyncAwait {
//                sessionController.getAssetLists()
//            }
//            if (assetLists.any { it.isEmpty() }) {
//                dialogBroadcaster("errorFetchingAssetLists")
//                return@launch
//            }
//
//            val downloadRequirementsResult = sessionController
//                    .getDownloadRequirements(filesystem, assetLists, forceDownloads, networkUtility)
//
//            val requiredDownloads: List<Asset>
//            when (downloadRequirementsResult) {
//                is RequiresWifiResult -> {
//                    dialogBroadcaster("wifiRequired")
//                    return@launch
//                }
//                is RequiredAssetsResult -> requiredDownloads = downloadRequirementsResult.assetList
//            }
//
//            if (requiredDownloads.isNotEmpty() && !ConnectionUtility().httpsHostIsReachable("github.com")) {
//                dialogBroadcaster("networkTooWeakForDownloads")
//                return@launch
//            }
//            asyncAwait {
//                sessionController.downloadRequirements(filesystem.distributionType, requiredDownloads, downloadBroadcastReceiver,
//                        initDownloadUtility(), progressBarUpdater, resources)
//            }
//
//            progressBarUpdater(getString(R.string.progress_setting_up), "")
//            val wasExtractionSuccessful = asyncAwait {
//                sessionController.extractFilesystemIfNeeded(filesystem, filesystemExtractLogger)
//            }
//            if (!wasExtractionSuccessful) {
//                dialogBroadcaster("extractionFailed")
//                return@launch
//            }
//
//            sessionController.ensureFilesystemHasRequiredAssets(filesystem)
//
//            if (session.isAppsSession) {
//                // TODO handle file not downloaded/found case
//                // TODO determine if moving the script to profile.d before extraction is harmful
//                // TODO better error handling for renamed apps sessions and filesystems
//                if (!appsList.contains(session.name) || session.filesystemName != "apps") {
//                    killProgressBar()
//                    sendToastBroadcast(R.string.error_apps_renamed)
//                    return@launch
//                }
//
//                filesystemUtility.moveAppScriptToRequiredLocations(session.name, filesystem)
//            }
//
//            val updatedSession = asyncAwait { sessionController.activateSession(session, serverUtility) }
//
//            updatedSession.active = true
//            updateSession(updatedSession)
//            killProgressBar()
//            startClient(updatedSession)
//            activeSessions[updatedSession.pid] = updatedSession
//        }
    }

    private fun stopApp(app: App) {
        val appSessions = activeSessions.filter {
            (_, session) ->
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
            else -> sendToastBroadcast(R.string.client_not_found)
        }
        sendSessionActivatedBroadcast()
    }

    private fun startSshClient(session: Session, packageName: String) {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:${session.port}/#userland")
        connectBotIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (clientIsPresent(connectBotIntent)) {
            this.startActivity(connectBotIntent)
        } else {
            getClient(packageName)
        }
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

    private fun clientIsPresent(intent: Intent): Boolean {
        val activities = packageManager.queryIntentActivities(intent, 0)
        return(activities.size > 0)
    }

    private fun getClient(packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        sendToastBroadcast(R.string.download_client_app)
        try {
            this.startActivity(intent)
        } catch (err: ActivityNotFoundException) {
            dialogBroadcaster("playStoreMissingForClient")
        }
    }

    private fun cleanUpFilesystem(filesystemId: Long) {
        if (filesystemId == (-1).toLong()) {
            throw Exception("Did not receive filesystemId")
        }

        activeSessions.values.filter { it.filesystemId == filesystemId }
                .forEach { killSession(it) }

        filesystemUtility.deleteFilesystem(filesystemId)
    }

    private fun sendSessionActivatedBroadcast() {
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "sessionActivated")
        broadcaster.sendBroadcast(intent)
    }

    private fun sendToastBroadcast(id: Int) {
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "toast")
                .putExtra("id", id)
        broadcaster.sendBroadcast(intent)
    }

    private val dialogBroadcaster: (String) -> Unit = {
        type: String ->
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "dialog")
                .putExtra("dialogType", type)
        broadcaster.sendBroadcast(intent)
    }
}