package tech.userland.userland

import android.Manifest
import android.app.DownloadManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_session_list.*
import kotlinx.android.synthetic.main.list_item_session.view.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.anko.toast
import tech.userland.userland.database.models.Session
import tech.userland.userland.ui.SessionListAdapter
import tech.userland.userland.ui.SessionViewModel
import tech.userland.userland.utils.*

class SessionListActivity : AppCompatActivity() {

    lateinit var sessionList: List<Session>
    lateinit var sessionAdapter: SessionListAdapter

    private val sessionViewModel: SessionViewModel by lazy {
        ViewModelProviders.of(this).get(SessionViewModel::class.java)
    }

    private val sessionChangeObserver = Observer<List<Session>> {
        it?.let {
            sessionList = it
            sessionAdapter = SessionListAdapter(this, ArrayList(sessionList))
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_list)
        setSupportActionBar(toolbar)

        sessionViewModel.getAllSessions().observe(this, sessionChangeObserver)

        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            parent, view, position, id ->
            val session = sessionList[position]
            if(!session.active == true) {
                startSession(session, view)
            }
            else {
                fireConnectBotIntent()
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadBroadcastReceiver)
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
            R.id.menu_item_session_kill_service -> killSessionService(session)
            R.id.menu_item_session_edit -> navigateToSessionEdit(session)
            R.id.menu_item_session_delete -> deleteSession(session)
            else -> super.onContextItemSelected(item)
        }
    }

    fun killSessionService(session: Session): Boolean {
        // TODO update all sessions relying on service
        // TODO more granular service killing
        if(session.active) {
            session.active = false
            sessionViewModel.updateSession(session)
            val view = list_sessions.getChildAt(sessionList.indexOf(session))
            view.image_list_item_active.setImageResource(R.drawable.ic_block_white_24dp)
            fileManager.killService(session.filesystemId.toString())
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
            progress_bar_session_list.progress = 0
            text_session_list_progress_update.text = "Downloading required assets..."
            val downloadManager = DownloadUtility(this@SessionListActivity)
            // TODO adjust requirements dynamically
            downloadManager.addRequirements("debian")
            if(downloadManager.checkIfLargeRequirement()) {
                val result = downloadManager.displayWifiChoices()
                when (result) {
                    DownloadUtility.TURN_ON_WIFI -> {
                        text_session_list_progress_update.text = ""
                        startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                        return@launchAsync
                    }
                    DownloadUtility.CANCEL -> {
                        text_session_list_progress_update.text = ""
                        return@launchAsync
                    }

                }
            }

            asyncAwait {
                downloadList.addAll(downloadManager.downloadRequirements())
                while(downloadList.isNotEmpty()) {
                    assetsWereDownloaded = true
                    delay(500)
                }
                if(assetsWereDownloaded) {
                    fileManager.moveDownloadedAssetsToSharedSupportDirectory()
                    fileManager.correctFilePermissions()
                }
            }
            progress_bar_session_list.progress = 25

            text_session_list_progress_update.text = "Setting up file system..."
            asyncAwait {
                // TODO support multiple distribution types
                if(assetsWereDownloaded) {
                    // TODO more granular replacement
                    fileManager.copyDistributionAssetsToFilesystem(filesystemDirectoryName, "debian")
                }
                if(!fileManager.statusFileExists(filesystemDirectoryName, ".success_filesystem_extraction")) {
                    fileManager.extractFilesystem(filesystemDirectoryName)
                }
            }
            progress_bar_session_list.progress = 50

            text_session_list_progress_update.text = "Starting service..."
            // TODO some check to determine if service is started
            asyncAwait {
                fileManager.startDropbearServer(filesystemDirectoryName)
                delay(500)
            }
            progress_bar_session_list.progress = 75

            text_session_list_progress_update.text = "Connecting to service..."
            asyncAwait {
                fireConnectBotIntent()
            }
            progress_bar_session_list.progress = 100

            text_session_list_progress_update.text = "Session active!"
            session.active = true
            sessionViewModel.updateSession(session)
        }
    }

    fun fireConnectBotIntent() {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        // TODO use db data here
        connectBotIntent.data = Uri.parse("ssh://non-root@localhost:2022/#userland")
        startActivity(connectBotIntent)
    }
}
