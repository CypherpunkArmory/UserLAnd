package tech.ula.ui

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.AdapterView
import kotlinx.android.synthetic.main.frag_session_list.*
import kotlinx.android.synthetic.main.list_item_session.view.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.anko.longToast
import tech.ula.BuildConfig
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.viewmodel.SessionListViewModel
import tech.ula.utils.*

class SessionListActivity : AppCompatActivity() {

    private lateinit var sessionList: List<Session>
    private lateinit var sessionAdapter: SessionListAdapter

    private var activeSessions = false
    private lateinit var filesystemList: List<Filesystem>

    private val sessionListViewModel: SessionListViewModel by lazy {
        ViewModelProviders.of(this).get(SessionListViewModel::class.java)
    }

    private val sessionChangeObserver = Observer<List<Session>> {
        it?.let {
            sessionList = it

            for(session in sessionList) {
                if(session.active) session.active = serverUtility.isServerRunning(session)
            }
            activeSessions = sessionList.any { it.active }

            sessionAdapter = SessionListAdapter(this, sessionList)
            list_sessions.adapter = sessionAdapter
        }
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let {
            filesystemList = it
        }
    }

    private val downloadList = ArrayList<Long>()

    private val downloadedList = ArrayList<Long>()

    private val downloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadedId != null)
                downloadedList.add(downloadedId)
        }
    }

    private val FILESYSTEM_EXTRACT_LOGGER = { line: String -> Int
        this.runOnUiThread({text_session_list_progress_details.text = getString(R.string.progress_setting_up_extract_text,line)})
        0
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

    private val permissionRequestCode = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.frag_session_list)
//        setSupportActionBar(toolbar)
        supportActionBar?.setTitle(R.string.sessions)
        notificationManager.createServiceNotificationChannel() // Android O requirement

        if(!arePermissionsGranted()) {
            ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    permissionRequestCode)
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val defaultPasswordMessageEnabled = preferences.getBoolean("pref_default_password_message_enabled", true)
        if(!defaultPasswordMessageEnabled) {
            text_session_list_default_password_message.visibility = View.GONE
        }

        sessionListViewModel.getAllSessions().observe(this, sessionChangeObserver)
        sessionListViewModel.getAllFilesystems().observe(this, filesystemChangeObserver)

        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            _, view, position, _ ->
            if(!arePermissionsGranted()) {
                showPermissionsNecessaryDialog()
                return@OnItemClickListener
            }

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

//        fab.setOnClickListener { navigateToSessionEdit(Session(0, filesystemId = 0)) }

//        val bundle = Bundle()
//        bundle.putParcelable("session", Session(0, filesystemId = 0))
//        fab.setOnClickListener(Navigation.createNavigateOnClickListener(R.id.sessionEditActivity))

//        fab.setOnClickListener { view ->
//            val bundle = Bundle()
//            bundle.putParcelable("session", Session(0, filesystemId = 0))
//            view.findNavController().navigate(R.id.sessionEditActivity, bundle)
//        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //TODO handle cases appropriately
        when(requestCode) {
            permissionRequestCode -> {
                if(!(grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    showPermissionsNecessaryDialog()
                }
                return
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
//        return when (item.itemId) {
//            R.id.menu_item_settings -> navigateToSettings()
//            R.id.menu_item_help -> navigateToHelp()
//            else -> super.onOptionsItemSelected(item)
//        }
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

    private fun arePermissionsGranted(): Boolean {
        return (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

                ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    private fun showPermissionsNecessaryDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_permissions_necessary_message)
                .setTitle(R.string.alert_permissions_necessary_title)
                .setPositiveButton(R.string.alert_permissions_necessary_request_button) {
                    dialog, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            permissionRequestCode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.alert_permissions_necessary_settings_button, {
                    dialog, _ ->
                    val settingsIntent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${BuildConfig.APPLICATION_ID}"))
                    startActivity(settingsIntent)
                    dialog.dismiss()
                })
                .setNeutralButton(R.string.alert_permissions_necessary_cancel_button, {
                    dialog, _ ->
                    dialog.dismiss()
                })
        builder.create().show()
    }

    fun stopService(session: Session): Boolean {
        // TODO update all sessions relying on service
        // TODO more granular service killing
        if(session.active) {
            session.active = false
            sessionListViewModel.updateSession(session)
            val view = list_sessions.getChildAt(sessionList.indexOf(session))
            view.image_list_item_active.setImageResource(R.drawable.ic_block_red_24dp)

            serverUtility.stopService(session)

            val serviceIntent = Intent(this, ServerService::class.java)
            serviceIntent.putExtra("type", "kill")
            serviceIntent.putExtra("pid", session.pid)

            startService(serviceIntent)
        }
        return true
    }

    fun deleteSession(session: Session): Boolean {
        stopService(session)
        sessionListViewModel.deleteSessionById(session.id)
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
//        val intent = Intent(this, HelpActivity::class.java)
//        startActivity(intent)
        return true
    }

    private fun navigateToSessionEdit(session: Session): Boolean {
        val intent = Intent(this, SessionEditActivity::class.java)
        intent.putExtra("session", session)
        intent.putExtra("editExisting", session.name != "")
        startActivity(intent)
        return true
    }

    private fun startSession(session: Session, view: View) {
        val filesystemDirectoryName = session.filesystemId.toString()
        var assetsWereDownloaded = false
        launchAsync {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

            val inAnimation = AlphaAnimation(0f, 1f)
            inAnimation.duration = 200
            layout_progress.animation = inAnimation
            layout_progress.visibility = View.VISIBLE

            val archType = filesystemUtility.getArchType()
            val filesystem = filesystemList.find { it.name == session.filesystemName }
            val distType = filesystem!!.distributionType
            val downloadManager = DownloadUtility(this@SessionListActivity, archType, distType)
            if (downloadManager.checkIfLargeRequirement()) {
                val result = downloadManager.displayWifiChoices()
                when (result) {
                    DownloadUtility.TURN_ON_WIFI -> {
                        startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                        endProgressBar()
                        return@launchAsync
                    }
                    DownloadUtility.CANCEL -> {
                        endProgressBar()
                        return@launchAsync
                    }

                }
            }

            text_session_list_progress_step.setText(R.string.progress_downloading)
            text_session_list_progress_details.text = ""
            asyncAwait {
                downloadList.clear()
                downloadedList.clear()
                downloadList.addAll(downloadManager.downloadRequirements())

                if (downloadList.isNotEmpty())
                    assetsWereDownloaded = true

                while (downloadList.size != downloadedList.size) {
                    this@SessionListActivity.runOnUiThread({text_session_list_progress_details.text = getString(R.string.progress_downloading_out_of,downloadedList.size,downloadList.size)})
                    delay(500)
                }
                if (assetsWereDownloaded) {
                    fileManager.moveDownloadedAssetsToSharedSupportDirectory()
                    fileManager.correctFilePermissions()
                }
            }
            text_session_list_progress_details.text = ""

            text_session_list_progress_step.setText(R.string.progress_setting_up)
            asyncAwait {
                // TODO only copy when newer versions have been downloaded (and skip rootfs)
                fileManager.copyDistributionAssetsToFilesystem(filesystemDirectoryName, distType)
                if (!fileManager.statusFileExists(filesystemDirectoryName, ".success_filesystem_extraction")) {
                    filesystemUtility.extractFilesystem(filesystemDirectoryName,FILESYSTEM_EXTRACT_LOGGER)
                }
            }

            text_session_list_progress_step.setText(R.string.progress_starting)
            text_session_list_progress_details.text = ""
            asyncAwait {

                session.pid = serverUtility.startServer(session)

                val serviceIntent = Intent(this@SessionListActivity, ServerService::class.java)
                serviceIntent.putExtra("type", "start")
                serviceIntent.putExtra("pid", session.pid)
                startService(serviceIntent)

                while (!serverUtility.isServerRunning(session)) {
                    delay(500)
                }
            }

            text_session_list_progress_step.setText(R.string.progress_connecting)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            asyncAwait {
                clientUtility.startClient(session)
            }

            session.active = true
            sessionListViewModel.updateSession(session)

            endProgressBar()
        }
    }

    private fun endProgressBar() {
        val outAnimation = AlphaAnimation(1f, 0f)
        outAnimation.duration = 200
        layout_progress.animation = outAnimation
        layout_progress.visibility = View.GONE
    }
}
