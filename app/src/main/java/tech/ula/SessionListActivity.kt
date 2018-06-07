package tech.ula

import android.Manifest
import android.app.DownloadManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_session_list.*
import kotlinx.android.synthetic.main.list_item_session.view.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import tech.ula.database.models.Session
import tech.ula.ui.SessionListAdapter
import tech.ula.ui.SessionViewModel
import tech.ula.utils.*

class SessionListActivity : AppCompatActivity() {

    private lateinit var sessionList: List<Session>
    private lateinit var sessionAdapter: SessionListAdapter

    private var activeSessions = false

    private val sessionViewModel: SessionViewModel by lazy {
        ViewModelProviders.of(this).get(SessionViewModel::class.java)
    }

    private val sessionChangeObserver = Observer<List<Session>> {
        it?.let {
            sessionList = it

            activeSessions = sessionList.any { it.active }

            sessionAdapter = SessionListAdapter(this, sessionList)
            list_sessions.adapter = sessionAdapter
        }
    }

    private val downloadList: ArrayList<Long> = ArrayList()

    private val downloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            downloadList.remove(downloadId)
        }
    }

    private val fileManager by lazy {
        FileUtility(this)
    }

    private val notificationManager by lazy {
        NotificationUtility(this)
    }

    private val serverUtility by lazy {
        ServerUtility(this)
    }

    private val clientUtility by lazy {
        ClientUtility(this)
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_list)
        setSupportActionBar(toolbar)
        supportActionBar?.setTitle(R.string.sessions)
        notificationManager.createServiceNotificationChannel() // Android O requirement

        sessionViewModel.getAllSessions().observe(this, sessionChangeObserver)

        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            _, view, position, _ ->
            val session = sessionList[position]
            if(!session.active) {
                if (!activeSessions) {
                    startSession(session, view)
                } else {
                    longToast(R.string.single_session_supported)
                }
            }
            else {
                clientUtility.startClient(session)
            }
        }

        registerReceiver(downloadBroadcastReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
        }
        fab.setOnClickListener { navigateToSessionEdit(Session(0, filesystemId = 0)) }

        progress_bar_session_list.visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //TODO handle cases appropriately
        when(requestCode) {
            0 -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    toast("yay!")
                }
                else {
                    toast("boo!")
                }
                return
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_file_system_management -> navigateToFilesystemManagement()
            R.id.menu_item_settings -> navigateToSettings()
            R.id.menu_item_help -> navigateToHelp()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.context_menu_sessions, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val session = sessionList[position]
        return when(item.itemId) {
            R.id.menu_item_session_kill_service -> stopService(session)
            R.id.menu_item_session_edit -> navigateToSessionEdit(session)
            R.id.menu_item_session_delete -> deleteSession(session)
            else -> super.onContextItemSelected(item)
        }
    }

    fun stopService(session: Session): Boolean {
        // TODO update all sessions relying on service
        // TODO more granular service killing
        if(session.active) {
            session.active = false
            sessionViewModel.updateSession(session)
            val view = list_sessions.getChildAt(sessionList.indexOf(session))
            view.image_list_item_active.setImageResource(R.drawable.ic_block_red_24dp)

            val serviceIntent = Intent(this, ServerService::class.java)
            serviceIntent.putExtra("type", "kill")
            serviceIntent.putExtra("session", session)

            startService(serviceIntent)
        }
        return true
    }

    fun deleteSession(session: Session): Boolean {
        sessionViewModel.deleteSessionById(session.id)
        return true
    }

    private fun navigateToFilesystemManagement(): Boolean {
        val intent = Intent(this, FilesystemListActivity::class.java)
        startActivity(intent)
        return true
    }

    private fun navigateToSettings(): Boolean {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        return true
    }

    private fun navigateToHelp(): Boolean {
        val intent = Intent(this, HelpActivity::class.java)
        startActivity(intent)
        return true
    }

    private fun navigateToSessionEdit(session: Session?): Boolean {
        val intent = Intent(this, SessionEditActivity::class.java)
        session?.let { intent.putExtra("session", session) }
        startActivity(intent)
        return true
    }

    private fun startSession(session: Session, view: View) {
        val filesystemDirectoryName = session.filesystemId.toString()
        var assetsWereDownloaded = false
        launchAsync {
            val inAnimation = AlphaAnimation(0f, 1f)
            inAnimation.duration = 200
            layout_progress.animation = inAnimation
            layout_progress.visibility = View.VISIBLE

            val downloadManager = DownloadUtility(this@SessionListActivity)
            // TODO adjust requirements dynamically
            downloadManager.addRequirements("debian")
            if (downloadManager.checkIfLargeRequirement()) {
                val result = downloadManager.displayWifiChoices()
                when (result) {
                    DownloadUtility.TURN_ON_WIFI -> {
                        startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                        return@launchAsync
                    }
                    DownloadUtility.CANCEL -> {
                        return@launchAsync
                    }

                }
            }

            text_session_list_progress_update.setText(R.string.progress_downloading)
            asyncAwait {
                downloadList.addAll(downloadManager.downloadRequirements())
                while (downloadList.isNotEmpty()) {
                    assetsWereDownloaded = true
                    delay(500)
                }
                if (assetsWereDownloaded) {
                    fileManager.moveDownloadedAssetsToSharedSupportDirectory()
                    fileManager.correctFilePermissions()
                }
            }

            text_session_list_progress_update.setText(R.string.progress_setting_up)
            asyncAwait {
                // TODO support multiple distribution types
                // TODO only copy when newer versions have been downloaded (and skip rootfs)
                fileManager.copyDistributionAssetsToFilesystem(filesystemDirectoryName, "debian")
                if (!fileManager.statusFileExists(filesystemDirectoryName, ".success_filesystem_extraction")) {
                    filesystemUtility.extractFilesystem(filesystemDirectoryName)
                }
            }

            // TODO some check to determine if service is started
            text_session_list_progress_update.setText(R.string.progress_starting)
            val serviceIntent = Intent(this@SessionListActivity, ServerService::class.java)
            serviceIntent.putExtra("type", "start")
            serviceIntent.putExtra("session", session)
            startService(serviceIntent)
//            asyncAwait {
//                notificationManager.startPersistentServiceNotification()
//                session.pid = serverUtility.startServer(session)
//                delay(500)
//            }

            text_session_list_progress_update.setText(R.string.progress_connecting)
            asyncAwait {
                clientUtility.startClient(session)
            }

            session.active = true
            sessionViewModel.updateSession(session)

            val outAnimation = AlphaAnimation(1f, 0f)
            outAnimation.duration = 200
            layout_progress.animation = outAnimation
            layout_progress.visibility = View.GONE
        }
    }
}
