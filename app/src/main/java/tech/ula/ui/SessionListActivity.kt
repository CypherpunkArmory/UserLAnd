package tech.ula.ui

import android.Manifest
import android.app.AlertDialog
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
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_session_list.*
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

    private val serverServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            this@SessionListActivity.runOnUiThread({
                if (intent != null) {
                    val type = intent.getStringExtra("type")
                    when (type) {
                        "startProgressBar" -> startProgressBar()
                        "updateProgressBar" -> updateProgressBar(intent)
                        "killProgressBar" -> killProgressBar()
                        "updateSession" -> updateSession(intent)
                        "displayWifiChoices" -> displayWifiChoices(intent)
                    }
                }
            })
        }
    }

    private val notificationManager by lazy {
        NotificationUtility(this)
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(this)
    }

    private val serverUtility by lazy {
        ServerUtility(this)
    }

    private val clientUtility by lazy {
        ClientUtility(this)
    }

    private val permissionRequestCode = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_list)
        setSupportActionBar(toolbar)
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
            _, _, position, _ ->
            if(!arePermissionsGranted()) {
                showPermissionsNecessaryDialog()
                return@OnItemClickListener
            }

            val session = sessionList[position]
            if(!session.active) {
                if (!activeSessions) {
                    startSession(session)
                } else {
                    longToast(R.string.single_session_supported)
                }
            }
            else {
                clientUtility.startClient(session)
            }

        }

        fab.setOnClickListener { navigateToSessionEdit(Session(0, filesystemId = 0)) }

        progress_bar_session_list.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(serverServiceBroadcastReceiver, IntentFilter(ServerService.SERVER_SERVICE_RESULT))
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serverServiceBroadcastReceiver)
        super.onStop()
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
            R.id.menu_item_session_kill_service -> killSession(session)
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
                .setPositiveButton(R.string.alert_permissions_necessary_request_button, {
                    dialog, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            permissionRequestCode)
                    dialog.dismiss()
                })
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


    private fun deleteSession(session: Session): Boolean {
        killSession(session)
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
        val intent = Intent(this, HelpActivity::class.java)
        startActivity(intent)
        return true
    }

    private fun navigateToSessionEdit(session: Session): Boolean {
        val intent = Intent(this, SessionEditActivity::class.java)
        intent.putExtra("session", session)
        intent.putExtra("editExisting", session.name != "")
        startActivity(intent)
        return true
    }

    private fun startSession(session: Session) {
        val filesystem = filesystemList.find { it.name == session.filesystemName }
        val serviceIntent = Intent(this@SessionListActivity, ServerService::class.java)
        serviceIntent.putExtra("session", session)
        serviceIntent.putExtra("filesystem", filesystem)
        serviceIntent.putExtra("type", "start")
        startService(serviceIntent)
    }

    private fun continueStartSession(session: Session) {
        val filesystem = filesystemList.find { it.name == session.filesystemName }
        val serviceIntent = Intent(this@SessionListActivity, ServerService::class.java)
        serviceIntent.putExtra("continue", session)
        serviceIntent.putExtra("filesystem", filesystem)
        serviceIntent.putExtra("type", "start")
        startService(serviceIntent)
    }

    private fun killSession(session: Session): Boolean {
        if(session.active) {
            val serviceIntent = Intent(this@SessionListActivity, ServerService::class.java)
            serviceIntent.putExtra("session", session)
            serviceIntent.putExtra("type", "kill")
            startService(serviceIntent)
        }
        return true
    }

    private fun startProgressBar() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
        val inAnimation = AlphaAnimation(0f, 1f)
        inAnimation.duration = 200
        layout_progress.animation = inAnimation
        layout_progress.visibility = View.VISIBLE
    }

    private fun killProgressBar() {
        val outAnimation = AlphaAnimation(1f, 0f)
        outAnimation.duration = 200
        layout_progress.animation = outAnimation
        layout_progress.visibility = View.GONE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    private fun updateProgressBar(intent: Intent) {
        val step = intent.getStringExtra("step")
        val details = intent.getStringExtra("details")
        text_session_list_progress_step.text = step
        text_session_list_progress_details.text = details
    }

    private fun updateSession(intent: Intent) {
        val session = intent.getParcelableExtra<Session>("session")
        sessionListViewModel.updateSession(session)
    }

    private fun displayWifiChoices(intent: Intent) {
        var session = intent.getParcelableExtra<Session>("session")
        val filesystem = filesystemList.find { it.name == session.filesystemName }
        val archType = filesystemUtility.getArchType()
        val distType = filesystem!!.distributionType
        val downloadManager = DownloadUtility(this@SessionListActivity, archType, distType)
        launchAsync {
            val result = downloadManager.displayWifiChoices()
            when (result) {
                DownloadUtility.TURN_ON_WIFI -> {
                    startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                    killProgressBar()
                }
                DownloadUtility.CANCEL -> {
                    killProgressBar()
                }
                else -> {
                    continueStartSession(session)
                }
            }
        }
    }

}
