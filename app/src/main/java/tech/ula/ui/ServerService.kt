package tech.ula.ui

import android.app.DownloadManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import android.view.View
import android.view.animation.AlphaAnimation
import kotlinx.android.synthetic.main.activity_session_list.*
import kotlinx.coroutines.experimental.delay
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.*

class ServerService : Service() {

    companion object {
        val SERVER_SERVICE_RESULT = "tech.ula.ServerService.RESULT"
    }

    private val activeSessions: ArrayList<Long> = ArrayList()

    private lateinit var broadcaster: LocalBroadcastManager


    private val downloadList = ArrayList<Long>()

    private val downloadedList = ArrayList<Long>()

    private val downloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadedId != null)
                downloadedList.add(downloadedId)
        }
    }

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

    private val FILESYSTEM_EXTRACT_LOGGER = { line: String -> Int
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
            "start" -> startSession(intent)
            "continue" -> continueStartSession(intent)
            "kill" -> killSession(intent)
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

    private fun addSession(pid: Long) {
        activeSessions.add(pid)
        startForeground(NotificationUtility.serviceNotificationId, notificationManager.buildPersistentServiceNotification())
    }

    private fun removeSession(pid: Long) {
        activeSessions.remove(pid)
        if(activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun killSession(intent: Intent) {
        val session = intent.getParcelableExtra<Session>("session")
        serverUtility.stopService(session)
        removeSession(session.pid)
        session.active = false
        updateSession(session)

    }

    private fun continueStartSession(intent: Intent) {
        var session = intent.getParcelableExtra<Session>("session")
        val filesystem = intent.getParcelableExtra<Filesystem>("filesystem")
        val archType = filesystemUtility.getArchType()
        val distType = filesystem!!.distributionType
        val downloadManager = DownloadUtility(this@ServerService, archType, distType)
        var assetsWereDownloaded = false
        val filesystemDirectoryName = session.filesystemId.toString()
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
                    updateProgressBar(getString(R.string.progress_downloading),getString(R.string.progress_downloading_out_of,downloadedList.size,downloadList.size))
                }
                if (assetsWereDownloaded) {
                    fileManager.moveDownloadedAssetsToSharedSupportDirectory()
                    fileManager.correctFilePermissions()
                }
            }
            updateProgressBar(getString(R.string.progress_setting_up),"")
            asyncAwait {
                // TODO only copy when newer versions have been downloaded (and skip rootfs)
                fileManager.copyDistributionAssetsToFilesystem(filesystemDirectoryName, distType)
                if (!fileManager.statusFileExists(filesystemDirectoryName, ".success_filesystem_extraction")) {
                    filesystemUtility.extractFilesystem(filesystemDirectoryName,FILESYSTEM_EXTRACT_LOGGER)
                }
            }
            updateProgressBar(getString(R.string.progress_starting),"")
            asyncAwait {

                session.pid = serverUtility.startServer(session)
                addSession(session.pid)

                while (!serverUtility.isServerRunning(session)) {
                    delay(500)
                }
            }
            asyncAwait {
                clientUtility.startClient(session)
            }
            session.active = true
            updateSession(session)

            killProgressBar()
        }
    }

    private fun startSession(intent: Intent) {
        var session = intent.getParcelableExtra<Session>("session")
        val filesystem = intent.getParcelableExtra<Filesystem>("filesystem")
        val archType = filesystemUtility.getArchType()
        val distType = filesystem!!.distributionType
        val downloadManager = DownloadUtility(this@ServerService, archType, distType)
        if (downloadManager.checkIfLargeRequirement()) {
            displayWifiChoices(session)
        } else {
            continueStartSession(intent)
        }
    }

    private fun startProgressBar() {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "startProgressBar")
        broadcaster.sendBroadcast(intent)
    }

    private fun killProgressBar() {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "killProgressBar")
        broadcaster.sendBroadcast(intent)
    }

    private fun updateProgressBar(step: String, details: String) {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "updateProgressBar")
        intent.putExtra("step", step)
        intent.putExtra("details", details)
        broadcaster.sendBroadcast(intent)
    }

    private fun updateSession(session: Session) {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "updateSession")
        intent.putExtra("session", session)
        broadcaster.sendBroadcast(intent)
    }

    private fun displayWifiChoices(session: Session) {
        val intent = Intent(SERVER_SERVICE_RESULT)
        intent.putExtra("type", "displayWifiChoices")
        intent.putExtra("session", session)
        broadcaster.sendBroadcast(intent)
    }

}