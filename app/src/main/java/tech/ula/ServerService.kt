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
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
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

    private fun initDownloadUtility(): DownloadUtility {
        val downloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return DownloadUtility(downloadManager, timestampPreferences,
                RequestUtility(), EnvironmentUtility().getDownloadsDirectory(), this.filesDir)
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
                startSession(session, filesystem, forceDownloads = false)
            }
            "forceDownloads" -> {
                startSession(lastActivatedSession, lastActivatedFilesystem, forceDownloads = true)
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

    private fun startSession(session: Session, filesystem: Filesystem, forceDownloads: Boolean) {
        lastActivatedSession = session
        lastActivatedFilesystem = filesystem
        launch(CommonPool) {

            startProgressBar()

            if (session.isExtracted) {
                activateSession(session)
                killProgressBar()
                return@launch
            }

            val assetListUtility = AssetListUtility(BuildUtility().getArchType(), filesystem.distributionType,
                    assetListPreferenceUtility, ConnectionUtility())
            val assetLists: List<List<Asset>>

            updateProgressBar(getString(R.string.progress_fetching_asset_lists), "")
            if (!networkUtility.networkIsActive()) {
                assetLists = assetListUtility.getCachedAssetLists()
                if (assetLists.any { it.isEmpty() }) {
                    sendDialogBroadcast("errorFetchingAssetLists")
                    killProgressBar()
                    return@launch
                }
            } else {
                assetLists = asyncAwait {
                    assetListUtility.retrieveAllRemoteAssetLists(networkUtility.httpsIsAccessible())
                }
            }

            // TODO is the case where the filesystem is downloaded but not extracted handled?
            updateProgressBar(getString(R.string.progress_checking_for_required_updates), "")
            var wifiRequired = false
            val requiredDownloads: List<Asset> = assetLists.map { assetList ->
                assetList.filter { asset ->
                    assetUpdateChecker.doesAssetNeedToUpdated(asset)
                    if (asset.isLarge && !networkUtility.wifiIsEnabled() && !forceDownloads) {
                        wifiRequired = true
                        sendDialogBroadcast("wifiRequired")
                        return@map listOf<Asset>()
                    }
                    assetUpdateChecker.doesAssetNeedToUpdated(asset)
                }
            }.flatten()

            if (wifiRequired) {
                killProgressBar()
                return@launch
            }

            if (requiredDownloads.isNotEmpty()) {
                downloadedIds.clear()
                asyncAwait {
                    val downloadUtility = initDownloadUtility()
                    val downloadIds = downloadUtility.downloadRequirements(requiredDownloads)
                    while (downloadIds.size != downloadedIds.size) {
                        updateProgressBar(getString(R.string.progress_downloading),
                                getString(R.string.progress_downloading_out_of,
                                        downloadedIds.size, downloadIds.size))
                        delay(500)
                    }
                    downloadUtility.moveAssetsToCorrectLocalDirectory()
                }
            }

            updateProgressBar(getString(R.string.progress_setting_up), "")
            if (!session.isExtracted) {
                val filesystemDirectoryName = "${filesystem.id}"
                filesystemUtility.copyDistributionAssetsToFilesystem(filesystemDirectoryName, filesystem.distributionType)
                val timeout = currentTimeSeconds() + (60 * 10)
                val extractionSuccess = asyncAwait {
                    filesystemUtility.extractFilesystem(filesystemDirectoryName, filesystemExtractLogger)
                    while (!filesystemUtility.isExtractionComplete(filesystemDirectoryName) &&
                            currentTimeSeconds() < timeout) {
                        delay(500)
                    }
                    if (filesystemUtility.didExtractionFail(filesystemDirectoryName)) {
                        return@asyncAwait false
                    }
                    true
                }
                if (!extractionSuccess) {
                    sendDialogBroadcast("extractionFailed")
                    killProgressBar()
                    return@launch
                }
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
                .putExtra("type", "toast")
                .putExtra("id", id)
        broadcaster.sendBroadcast(intent)
    }

    private fun sendDialogBroadcast(type: String) {
        val intent = Intent(SERVER_SERVICE_RESULT)
                .putExtra("type", "dialog")
                .putExtra("dialogType", type)
        broadcaster.sendBroadcast(intent)
    }
}