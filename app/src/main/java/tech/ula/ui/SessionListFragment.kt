package tech.ula.ui

import android.Manifest
import android.app.Activity
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
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.frag_session_list.*
import kotlinx.android.synthetic.main.list_item_session.view.*
import org.jetbrains.anko.bundleOf
import tech.ula.BuildConfig
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.*
import tech.ula.viewmodel.SessionListViewModel

class SessionListFragment : Fragment() {

    private val permissionRequestCode = 1000
    private lateinit var activityContext: Activity

    private lateinit var sessionList: List<Session>
    private lateinit var sessionAdapter: SessionListAdapter
    private lateinit var filesystemList: List<Filesystem>
    private var activeSessions = false

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

            sessionAdapter = SessionListAdapter(activityContext, sessionList)
            list_sessions.adapter = sessionAdapter
        }
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let { filesystemList = it }
    }

    private val serverServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            activityContext.runOnUiThread {
                intent?.let {
                    val type = it.getStringExtra("type")
                    when (type) {
                        "startProgressBar" -> startProgressBar()
                        "updateProgressBar" -> updateProgressBar(it)
                        "killProgressBar" -> killProgressBar()
                        "updateSession" -> updateSession(it)
                        "networkUnavailable" -> displayNetworkUnavailableDialog()
                        "displayNetworkChoices" -> displayNetworkChoicesDialog()
                    }
                }
            }
        }
    }

    private val serverUtility by lazy {
        ServerUtility(activityContext)
    }

    private val clientUtility by lazy {
        ClientUtility(activityContext)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(activityContext).registerReceiver(serverServiceBroadcastReceiver, IntentFilter(ServerService.SERVER_SERVICE_RESULT))
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(activityContext).unregisterReceiver(serverServiceBroadcastReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_create, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if(item.itemId == R.id.menu_item_add) editSession(Session(0, filesystemId = 0))
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        sessionListViewModel.getAllSessions().observe(viewLifecycleOwner, sessionChangeObserver)
        sessionListViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)
        return inflater.inflate(R.layout.frag_session_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!

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
                    Toast.makeText(activityContext, R.string.single_session_supported, Toast.LENGTH_LONG).show()
                }
            }
            else {
                clientUtility.startClient(session)
            }
        }
    }

    private fun arePermissionsGranted(): Boolean {
        return (ContextCompat.checkSelfPermission(activityContext,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

                ContextCompat.checkSelfPermission(activityContext,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    private fun showPermissionsNecessaryDialog() {
        val builder = AlertDialog.Builder(activityContext)
        builder.setMessage(R.string.alert_permissions_necessary_message)
                .setTitle(R.string.alert_permissions_necessary_title)
                .setPositiveButton(R.string.alert_permissions_necessary_request_button) {
                    dialog, _ ->
                    ActivityCompat.requestPermissions(activityContext, arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            permissionRequestCode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.alert_permissions_necessary_settings_button) {
                    dialog, _ ->
                    val settingsIntent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${BuildConfig.APPLICATION_ID}"))
                    startActivity(settingsIntent)
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.alert_permissions_necessary_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
        builder.create().show()
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activityContext.menuInflater.inflate(R.menu.context_menu_sessions, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val session = sessionList[position]
        return when(item.itemId) {
            R.id.menu_item_session_kill_service -> stopService(session)
            R.id.menu_item_session_edit -> editSession(session)
            R.id.menu_item_session_delete -> deleteSession(session)
            else -> super.onContextItemSelected(item)
        }
    }

    private fun stopService(session: Session): Boolean {
        if(session.active) {
            val view = list_sessions.getChildAt(sessionList.indexOf(session))
            view.image_list_item_active.setImageResource(R.drawable.ic_block_red_24dp)

            val serviceIntent = Intent(activityContext, ServerService::class.java)
            serviceIntent.putExtra("session", session)
            serviceIntent.putExtra("type", "kill")
            activityContext.startService(serviceIntent)
        }
        return true
    }

    private fun editSession(session: Session): Boolean {
        val editExisting = session.name != ""
        val bundle = bundleOf("session" to session, "editExisting" to editExisting)
        NavHostFragment.findNavController(this).navigate(R.id.session_edit_fragment, bundle)
        return true
    }

    private fun deleteSession(session: Session): Boolean {
        stopService(session)
        sessionListViewModel.deleteSessionById(session.id)
        return true
    }

    private fun startSession(session: Session) {
        val filesystem = filesystemList.find { it.name == session.filesystemName }
        val serviceIntent = Intent(activityContext, ServerService::class.java)
        serviceIntent.putExtra("session", session)
        serviceIntent.putExtra("filesystem", filesystem)
        serviceIntent.putExtra("type", "start")
        activityContext.startService(serviceIntent)
    }

    private fun startProgressBar() {
        activityContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
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
        activityContext.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
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

    private fun displayNetworkUnavailableDialog() {
        val builder = AlertDialog.Builder(activityContext)
        builder.setMessage(R.string.alert_network_unavailable_message)
                .setTitle(R.string.alert_network_unavailable_title)
                .setPositiveButton(R.string.alert_network_unavailable_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }

    private fun displayNetworkChoicesDialog() {
        val builder = AlertDialog.Builder(activityContext)
        builder.setMessage(R.string.alert_wifi_disabled_message)
                .setTitle(R.string.alert_wifi_disabled_title)
                .setPositiveButton(R.string.alert_wifi_disabled_continue_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    val serviceIntent = Intent(activityContext, ServerService::class.java)
                    serviceIntent.putExtra("type", "continue")
                    activityContext.startService(serviceIntent)
                }
                .setNegativeButton(R.string.alert_wifi_disabled_turn_on_wifi_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                    killProgressBar()
                }
                .setNeutralButton(R.string.alert_wifi_disabled_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    killProgressBar()
                }
                .setOnCancelListener {
                    killProgressBar()
                }
                .create()
                .show()
    }
}