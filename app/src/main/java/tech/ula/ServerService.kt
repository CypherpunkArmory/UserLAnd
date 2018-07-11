package tech.ula

import android.app.DownloadManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import kotlinx.coroutines.experimental.delay
import org.jetbrains.anko.doAsync
import tech.ula.model.AppDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.*

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

    private lateinit var downloadManager: DownloadUtility

    private val notificationManager: NotificationUtility by lazy {
        NotificationUtility(this)
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(this)
    }

    private val fileManager by lazy {
        FileUtility(this)
    }


    private val serverUtility by lazy {
        ServerUtility(this)
    }

    private val clientUtility by lazy {
        ClientUtility(this)
    }

    private val filesystemExtractLogger = { line: String -> Int
        updateProgressBar(getString(R.string.progress_setting_up),getString(R.string.progress_setting_up_extract_text,line))
        0
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
        if(activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun startSession(session: Session, filesystem: Filesystem) {
        lastActivatedSession = session
        lastActivatedFilesystem = filesystem
        val archType = filesystemUtility.getArchType()
        val distType = lastActivatedFilesystem.distributionType

        downloadManager = DownloadUtility(this@ServerService, archType, distType)
        if(!downloadManager.isNetworkAvailable()) {
            val resultIntent = Intent(SERVER_SERVICE_RESULT)
            resultIntent.putExtra("type", "networkUnavailable")
            return
        }

        if (downloadManager.checkIfLargeRequirement()) {
            displayNetworkChoices()
        } else {
            continueStartSession()
        }
    }

    private fun continueStartSession() {
        var assetsWereDownloaded = false
        val filesystemDirectoryName = lastActivatedSession.filesystemId.toString()

        launchAsync {
            startProgressBar()

            updateProgressBar(getString(R.string.progress_downloading),"")
            asyncAwait {
                downloadList.clear()
                downloadedList.clear()
                downloadList.addAll(downloadManager.downloadRequirements())

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
                    fileManager.moveAssetsToCorrectSharedDirectory()
                    fileManager.correctFilePermissions()
                }
            }

            updateProgressBar(getString(R.string.progress_setting_up),"")
            asyncAwait {
                // TODO only copy when newer versions have been downloaded (and skip rootfs)
                val distType = lastActivatedFilesystem.distributionType
                fileManager.copyDistributionAssetsToFilesystem(filesystemDirectoryName, distType)
                if (!fileManager.statusFileExists(filesystemDirectoryName, ".success_filesystem_extraction")) {
                    filesystemUtility.extractFilesystem(filesystemDirectoryName, filesystemExtractLogger)
                }
            }

            updateProgressBar(getString(R.string.progress_starting),"")
            asyncAwait {

                lastActivatedSession.pid = serverUtility.startServer(lastActivatedSession)
                addSession(lastActivatedSession)

                while (!serverUtility.isServerRunning(lastActivatedSession)) {
                    delay(500)
                }
            }

            asyncAwait {
                clientUtility.startClient(lastActivatedSession)
            }

            lastActivatedSession.active = true
            updateSession(lastActivatedSession)

            killProgressBar()
        }
    }

    private fun killSession(session: Session) {
        serverUtility.stopService(session)
        removeSession(session)
        session.active = false
        updateSession(session)
    }

    private fun cleanUpFilesystem(filesystemId: Long) {
        if(filesystemId == (-1).toLong()) {
            throw Exception("Did not receive filesystemId")
        }

        activeSessions.values.filter { it.filesystemId == filesystemId }
                .forEach { killSession(it) }

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

    private fun updateSession(session: Session) {
        doAsync { AppDatabase.getInstance(this@ServerService).sessionDao().updateSession(session) }
    }

    private fun displayNetworkChoices() {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "displayNetworkChoices")
        broadcaster.sendBroadcast(intent)
    }

}