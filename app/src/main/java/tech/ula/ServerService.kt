package tech.ula

import android.app.DownloadManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import kotlinx.coroutines.experimental.delay
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import tech.ula.model.AppDatabase
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class ServerService : Service() {

    companion object {
        const val SERVER_SERVICE_RESULT = "tech.ula.ServerService.RESULT"
    }

    private val activeSessions: MutableMap<Long, Session> = mutableMapOf()
    private var progressBarActive = false
    private lateinit var lastActivatedSession: Session
    private lateinit var lastActivatedFilesystem: Filesystem

    private lateinit var broadcaster: LocalBroadcastManager

    private val downloadedIds = ArrayList<Long>()
    private val downloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            downloadedId?.let { downloadedIds.add(it) }
        }
    }

    private lateinit var downloadUtility: DownloadUtility

    private val notificationManager: NotificationUtility by lazy {
        NotificationUtility(this)
    }

    private val timestampPreferences by lazy {
        TimestampPreferenceUtility(this.getSharedPreferences("file_timestamps", Context.MODE_PRIVATE))
    }

    private val assetListPreferenceUtility by lazy {
        AssetListPreferenceUtility(this.getSharedPreferences("assetLists", Context.MODE_PRIVATE))
    }

    private val networkUtility by lazy {
        val connectivityManager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        NetworkUtility(connectivityManager, ConnectionUtility())
    }

    private val assetUpdateChecker by lazy {
        AssetUpdateChecker(this.filesDir.path, timestampPreferences)
    }

    private val fileUtility by lazy {
        FileUtility(this.filesDir.path)
    }

    private val execUtility by lazy {
        ExecUtility(fileUtility, DefaultPreferenceUtility(this.defaultSharedPreferences))
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(this.filesDir.path, execUtility)
    }

    private val serverUtility by lazy {
        ServerUtility(execUtility, fileUtility)
    }

    private val filesystemExtractLogger = { line: String -> Unit
        updateProgressBar(getString(R.string.progress_setting_up),
                getString(R.string.progress_setting_up_extract_text, line))
    }

    override fun onCreate() {
        broadcaster = LocalBroadcastManager.getInstance(this)
        registerReceiver(downloadBroadcastReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onDestroy() {
        unregisterReceiver(downloadBroadcastReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val intentType = intent.getStringExtra("type")
        when (intentType) {
            "start" -> {
                val session: Session = intent.getParcelableExtra("session")
                val filesystem: Filesystem = intent.getParcelableExtra("filesystem")
                startSession(session, filesystem)
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
            "isProgressBarActive" -> isProgressBarActive()
        }

        // TODO this is causing crashes. should potentially be START_REDELIVER_INTENT
        return Service.START_STICKY
    }

    // Used in conjunction with manifest attribute `android:stopWithTask="true"`
    // to clean up when app is swiped away.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForeground(true)
        stopSelf()
    }

    private suspend fun activateSession(session: Session) {
        asyncAwait {
            session.pid = serverUtility.startServer(lastActivatedSession)
            activeSessions[session.pid] = session
            lastActivatedSession = session
            startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification())

            while (!serverUtility.isServerRunning(lastActivatedSession)) {
                delay(500)
            }
        }
        startClient(session)
    }

    private fun removeSession(session: Session) {
        activeSessions.remove(session.pid)
        if (activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateSession(session: Session) {
        doAsync { AppDatabase.getInstance(this@ServerService).sessionDao().updateSession(session) }
    }

    private fun killSession(session: Session) {
        serverUtility.stopService(session)
        removeSession(session)
        session.active = false
        updateSession(session)
    }

    private fun startSession(session: Session, filesystem: Filesystem) {
        launchAsync {
            //  if session is activateable
            //      activate
            //  handle errors TODO

            startProgressBar()

            if (session.isExtracted) {
                activateSession(session)
                return@launchAsync
            }

            //  if session needs assets
            //      fetch asset lists
            //      handle errors
            //      download assets and update UI as downloaded TODO UI
            //      handle errors
            val assetListUtility = AssetListUtility(BuildUtility().getArchType(), filesystem.distributionType,
                    assetListPreferenceUtility, ConnectionUtility())
            val assetLists: List<List<Asset>>

            // check network availability
            if (!networkUtility.networkIsActive()) {
                assetLists = assetListUtility.getCachedAssetLists()
                if (assetLists.any { it.isEmpty() }) {
                    sendDialogBroadcast("errorFetchingAssetLists")
                    return@launchAsync
                }
            } else {
                assetLists = assetListUtility.retrieveAllRemoteAssetLists(networkUtility.httpsIsAccessible())
            }

            // TODO is the case where the filesystem is downloaded but not extracted handled?
            var wifiRequired = false
            val requiredDownloads: List<Asset> = assetLists.map { assetList ->
                assetList.filter { asset ->
                    assetUpdateChecker.doesAssetNeedToUpdated(asset)
                    if (asset.isLarge && !networkUtility.wifiIsEnabled()) {
                        wifiRequired = true
                        sendDialogBroadcast("wifiRequired")
                        return@map listOf<Asset>()
                    }
                    assetUpdateChecker.doesAssetNeedToUpdated(asset)
                }
            }.flatten()

            if (wifiRequired) return@launchAsync

            if (requiredDownloads.isNotEmpty()) {
                downloadedIds.clear()
                asyncAwait {
                    val downloadIds = downloadUtility.downloadRequirements(requiredDownloads)
                    while (downloadIds.size != downloadedIds.size) {
                        updateProgressBar(getString(R.string.progress_downloading),
                                getString(R.string.progress_downloading_out_of,
                                        downloadedIds.size, downloadIds.size))
                        delay(500)
                    }
                }
            }

            //  extract and update ui
            //  handle errors

            if (!session.isExtracted) {
                val timeout = currentTimeSeconds() + (60 * 10)
                val extractionSuccess = asyncAwait {
                    val filesystemDirectoryName = "${filesystem.id}"
                    filesystemUtility.extractFilesystem(filesystemDirectoryName, filesystemExtractLogger)
                    while (!filesystemUtility.isExtractionComplete(filesystemDirectoryName) &&
                            currentTimeSeconds() < timeout) {
                        delay(500)
                    }
                    if (filesystemUtility.didExtractionFail(filesystemDirectoryName)) {
                        sendDialogBroadcast("extractionFailed")
                        return@asyncAwait false
                    }
                    true
                }
                if (!extractionSuccess) return@launchAsync
            }

            activateSession(session)
            killProgressBar()
        }
    }

    private fun startClient(session: Session) {
        when (session.clientType) {
            "ConnectBot" -> startSshClient(session, "org.connectbot")
            "bVNC" -> startVncClient(session, "com.iiordanov.freebVNC")
            else -> sendToastBroadcast(R.string.client_not_found)
        }
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
        bVncIntent.data = Uri.parse("vnc://127.0.0.1:5951/?VncPassword=${session.password}")
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
        this.startActivity(intent)
    }

    private fun cleanUpFilesystem(filesystemId: Long) {
        if (filesystemId == (-1).toLong()) {
            throw Exception("Did not receive filesystemId")
        }

        activeSessions.values.filter { it.filesystemId == filesystemId }
                .forEach { killSession(it) }

        // TODO this is causing ANRs. stick in coroutine
        filesystemUtility.deleteFilesystem(filesystemId)
    }

    private fun startProgressBar() {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "startProgressBar")
        broadcaster.sendBroadcast(intent)

        progressBarActive = true
    }

    private fun killProgressBar() {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "killProgressBar")
        broadcaster.sendBroadcast(intent)

        progressBarActive = false
    }

    private fun updateProgressBar(step: String, details: String) {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "updateProgressBar")
        intent.putExtra("step", step)
        intent.putExtra("details", details)
        broadcaster.sendBroadcast(intent)
    }

    private fun isProgressBarActive() {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "isProgressBarActive")
        intent.putExtra("isProgressBarActive", progressBarActive)
        broadcaster.sendBroadcast(intent)
    }

    private fun sendToastBroadcast(id: Int) {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("id", id)
        broadcaster.sendBroadcast(intent)
    }

    private fun sendDialogBroadcast(type: String) {
        // TODO
    }
}