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
import android.widget.Toast
import kotlinx.coroutines.experimental.delay
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import tech.ula.model.AppDatabase
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

    private val downloadList = ArrayList<Long>()

    private val downloadedList = ArrayList<Long>()

    private val downloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            downloadedId?.let { downloadedList.add(it) }
        }
    }

    private lateinit var downloadUtility: DownloadUtility

    private val notificationManager: NotificationUtility by lazy {
        NotificationUtility(this)
    }

    private val fileUtility by lazy {
        FileUtility(this.filesDir.path)
    }

    private val execUtility by lazy {
        ExecUtility(fileUtility, DefaultPreferenceUtility(this.defaultSharedPreferences))
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(execUtility, fileUtility, BuildUtility())
    }

    private val serverUtility by lazy {
        ServerUtility(execUtility, fileUtility)
    }

    private fun initializeDownloadUtility(
        session: Session = lastActivatedSession,
        filesystem: Filesystem = lastActivatedFilesystem
    ): DownloadUtility {
        val downloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val prefs = this.getSharedPreferences("file_timestamps", Context.MODE_PRIVATE)
        val applicationFilesDirPath = this.filesDir.path

        return DownloadUtility(session, filesystem,
                downloadManager, TimestampPreferenceUtility(prefs),
                applicationFilesDirPath, ConnectionUtility(), RequestUtility(), EnvironmentUtility())
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
            "continue" -> continueStartSession()
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
            "forceAssetUpdate" -> {
                val session: Session = intent.getParcelableExtra("session")
                val filesystem: Filesystem = intent.getParcelableExtra("filesystem")
                forceAssetUpdate(session, filesystem)
            }
            "isProgressBarActive" -> isProgressBarActive()
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

    private fun addSession(session: Session) {
        activeSessions[session.pid] = session
        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification())
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
        lastActivatedSession = session
        lastActivatedFilesystem = filesystem

        downloadUtility = initializeDownloadUtility()

        val filesystemDirectoryName = session.filesystemId.toString()
        session.isExtracted = fileUtility
                .statusFileExists(filesystemDirectoryName, ".success_filesystem_extraction")

        filesystem.isDownloaded = fileUtility.distributionAssetsExist(filesystemDirectoryName)


        // TODO
//        if (!downloadUtility.internetIsAccessible()) {
//            if (session.isExtracted || filesystem.isDownloaded) {
//                continueStartSession()
//                return
//            }
            val resultIntent = Intent(SERVER_SERVICE_RESULT)
            resultIntent.putExtra("type", "networkUnavailable")
            broadcaster.sendBroadcast(resultIntent)
            return
//        }

        // TODO move to control utility
//        if (downloadUtility.largeAssetRequiredAndNoWifi()) {
//            displayNetworkChoices()
//        } else {
//            continueStartSession()
//        }
    }

    private fun continueStartSession() {
        val filesystemDirectoryName = lastActivatedSession.filesystemId.toString()
        var assetsWereDownloaded = false

        launchAsync {
            startProgressBar()

            try {
                asyncAwait {
                    assetsWereDownloaded = downloadAssets()
                }
            } catch (err: Exception) {
                if (err.message == "Error getting asset list") {
                    val resultIntent = Intent(SERVER_SERVICE_RESULT)
                    resultIntent.putExtra("type", "assetListFailure")
                    broadcaster.sendBroadcast(resultIntent)
                    killProgressBar()
                    return@launchAsync
                }
            }

            updateProgressBar(getString(R.string.progress_setting_up), "")
            if (assetsWereDownloaded || !filesystemUtility.assetsArePresent(filesystemDirectoryName)) {
                asyncAwait {
                    val distType = lastActivatedFilesystem.distributionType
                    fileUtility.copyDistributionAssetsToFilesystem(filesystemDirectoryName, distType)
                    if (!lastActivatedSession.isExtracted) {
                        filesystemUtility.extractFilesystem(filesystemDirectoryName, filesystemExtractLogger)
                    }
                }

                if (!fileUtility.statusFileExists(filesystemDirectoryName, ".success_filesystem_extraction")) {
                    Toast.makeText(this@ServerService, R.string.filesystem_extraction_failed, Toast.LENGTH_LONG).show()
                    killProgressBar()
                    return@launchAsync
                }
                filesystemUtility.removeRootfsFilesFromFilesystem(filesystemDirectoryName)
                lastActivatedSession.isExtracted = true
            }

            updateProgressBar(getString(R.string.progress_starting), "")
            asyncAwait {

                lastActivatedSession.pid = serverUtility.startServer(lastActivatedSession)
                addSession(lastActivatedSession)

                while (!serverUtility.isServerRunning(lastActivatedSession)) {
                    delay(500)
                }
            }

            asyncAwait {
                startClient(lastActivatedSession)
            }

            lastActivatedSession.active = true
            updateSession(lastActivatedSession)

            killProgressBar()
        }
    }

    private suspend fun downloadAssets(updateIsBeingForced: Boolean = false): Boolean {
        updateProgressBar(getString(R.string.progress_downloading),
                getString(R.string.progress_downloading_check_updates))

        var assetsWereDownloaded = false
        downloadList.clear()
        downloadedList.clear()
        downloadList.addAll(downloadUtility.downloadRequirements(updateIsBeingForced))

        if (downloadList.isNotEmpty())
            assetsWereDownloaded = true

        while (downloadList.size != downloadedList.size) {
            updateProgressBar(getString(R.string.progress_downloading),
                    getString(R.string.progress_downloading_out_of,
                            downloadedList.size,
                            downloadList.size))
            delay(500)
        }

        if (assetsWereDownloaded) {
            fileUtility.moveAssetsToCorrectSharedDirectory()
            fileUtility.correctFilePermissions(lastActivatedFilesystem.distributionType)
        }

        return assetsWereDownloaded
    }

    private fun startClient(session: Session) {
        when (session.clientType) {
            "ConnectBot" -> startSshClient(session, "org.connectbot")
            "bVNC" -> startVncClient(session, "com.iiordanov.freebVNC")
//            "xsdl" -> return // TODO
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

    private fun forceAssetUpdate(session: Session, filesystem: Filesystem) {
        lastActivatedSession = session
        lastActivatedFilesystem = filesystem

        downloadUtility = initializeDownloadUtility(session, filesystem)
        var assetsWereDownloaded = true
        launchAsync {
            try {
                asyncAwait { assetsWereDownloaded = downloadAssets(updateIsBeingForced = true) }
            } catch (err: Exception) {
                if (err.message == "Error getting asset list") {
                    val resultIntent = Intent(SERVER_SERVICE_RESULT)
                    resultIntent.putExtra("type", "assetListFailure")
                    broadcaster.sendBroadcast(resultIntent)
                    killProgressBar()
                    return@launchAsync
                }
            }
            killProgressBar()
            if (!assetsWereDownloaded) {
                sendToastBroadcast(R.string.no_assets_need_updating)
                return@launchAsync
            }
            fileUtility.copyDistributionAssetsToFilesystem(filesystem.id.toString(), filesystem.distributionType)
        }
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

    private fun displayNetworkChoices() {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "displayNetworkChoices")
        broadcaster.sendBroadcast(intent)
    }

    private fun sendToastBroadcast(id: Int) {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("id", id)
        broadcaster.sendBroadcast(intent)
    }
}