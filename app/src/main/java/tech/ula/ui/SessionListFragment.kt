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
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.view.* // ktlint-disable no-wildcard-imports
import android.view.animation.AlphaAnimation
import android.widget.AdapterView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_session_list.* // ktlint-disable no-wildcard-imports
import kotlinx.android.synthetic.main.list_item_session.view.* // ktlint-disable no-wildcard-imports
import org.jetbrains.anko.bundleOf
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.viewmodel.SessionListViewModel

class SessionListFragment : Fragment() {

    private val permissionRequestCode = 1000
    private lateinit var activityContext: Activity

    private lateinit var sessionList: List<Session>
    private lateinit var sessionAdapter: SessionListAdapter
    private lateinit var filesystemList: List<Filesystem>

    private lateinit var lastSelectedSession: Session

    private val sessionListViewModel: SessionListViewModel by lazy {
        ViewModelProviders.of(this).get(SessionListViewModel::class.java)
    }

    private val sessionChangeObserver = Observer<List<Session>> {
        it?.let {
            sessionList = it

            sessionAdapter = SessionListAdapter(activityContext, sessionList)
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
            activityContext.runOnUiThread {
                intent?.let {
                    val type = it.getStringExtra("type")
                    when (type) {
                        "startProgressBar" -> startProgressBar()
                        "updateProgressBar" -> updateProgressBar(it)
                        "killProgressBar" -> killProgressBar()
                        "isProgressBarActive" -> syncProgressBarDisplayedWithService(it)
                        "networkUnavailable" -> displayNetworkUnavailableDialog()
                        "assetListFailure" -> displayAssetListFailureDialog()
                        "displayNetworkChoices" -> displayNetworkChoicesDialog()
                        "toast" -> showToast(it)
                        "dialog" -> showDialog(it)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permissionRequestCode -> {

                val grantedPermissions = (grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED)

                if (grantedPermissions) {
                    handleSessionSelection(lastSelectedSession)
                } else {
                    showPermissionsNecessaryDialog()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_create, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_item_add) editSession(Session(0, filesystemId = 0))
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_session_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        sessionListViewModel.getAllSessions().observe(viewLifecycleOwner, sessionChangeObserver)
        sessionListViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)

        activityContext = activity!!

        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            _, _, position, _ ->
            lastSelectedSession = sessionList[position]

            if (arePermissionsGranted()) {
                handleSessionSelection(lastSelectedSession)
            } else {
                showPermissionsNecessaryDialog()
                return@OnItemClickListener
            }
        }
    }

    private fun handleSessionSelection(session: Session) {
        if (session.active) {
            restartRunningSession(session)
        } else {
            if (sessionListViewModel.activeSessions) {
                Toast.makeText(activityContext, R.string.single_session_supported, Toast.LENGTH_LONG).show()
            } else {
                startSession(session)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(activityContext).registerReceiver(serverServiceBroadcastReceiver, IntentFilter(ServerService.SERVER_SERVICE_RESULT))

        val intent = Intent(activityContext, ServerService::class.java)
        intent.putExtra("type", "isProgressBarActive")
        activityContext.startService(intent)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(activityContext).unregisterReceiver(serverServiceBroadcastReceiver)
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
                .setPositiveButton(R.string.alert_permissions_necessary_ok_button) {
                    dialog, _ ->
                    requestPermissions(arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            permissionRequestCode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.alert_permissions_necessary_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
        builder.create().show()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val info = menuInfo as AdapterView.AdapterContextMenuInfo
        super.onCreateContextMenu(menu, v, menuInfo)
        val session = sessionList[info.position]
        when {
            session.isExtracted && !session.active ->
                    activityContext.menuInflater.inflate(R.menu.context_menu_sessions_updateable, menu)
            else ->
                activityContext.menuInflater.inflate(R.menu.context_menu_sessions, menu)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val session = sessionList[position]
        return when (item.itemId) {
            R.id.menu_item_session_kill_service -> stopService(session)
            R.id.menu_item_session_edit -> editSession(session)
            R.id.menu_item_session_delete -> deleteSession(session)
            R.id.menu_item_session_update_assets -> forceAssetUpdate(session)
            else -> super.onContextItemSelected(item)
        }
    }

    private fun stopService(session: Session): Boolean {
        if (session.active) {
            val view = list_sessions.getChildAt(sessionList.indexOf(session))
            view.image_list_item_active.setImageResource(R.drawable.ic_block_red_24dp)

            val serviceIntent = Intent(activityContext, ServerService::class.java)
            serviceIntent.putExtra("type", "kill")
            serviceIntent.putExtra("session", session)
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

    private fun forceAssetUpdate(session: Session): Boolean {
        val filesystem = filesystemList.find { it.name == session.filesystemName }
        val serviceIntent = Intent(activityContext, ServerService::class.java)
        serviceIntent.putExtra("type", "forceAssetUpdate")
        serviceIntent.putExtra("session", session)
        serviceIntent.putExtra("filesystem", filesystem)
        activityContext.startService(serviceIntent)
        return true
    }

    private fun startSession(session: Session) {
        val filesystem = filesystemList.find { it.name == session.filesystemName }
        val serviceIntent = Intent(activityContext, ServerService::class.java)
        serviceIntent.putExtra("type", "start")
        serviceIntent.putExtra("session", session)
        serviceIntent.putExtra("filesystem", filesystem)
        activityContext.startService(serviceIntent)
    }

    private fun restartRunningSession(session: Session) {
        val serviceIntent = Intent(activityContext, ServerService::class.java)
        serviceIntent.putExtra("type", "restartRunningSession")
        serviceIntent.putExtra("session", session)
        activityContext.startService(serviceIntent)
    }

    private fun startProgressBar() {
        val inAnimation = AlphaAnimation(0f, 1f)
        inAnimation.duration = 200
        layout_progress.animation = inAnimation
        layout_progress.visibility = View.VISIBLE
        layout_progress.isFocusable = true
        layout_progress.isClickable = true
    }

    private fun killProgressBar() {
        val outAnimation = AlphaAnimation(1f, 0f)
        outAnimation.duration = 200
        layout_progress.animation = outAnimation
        layout_progress.visibility = View.GONE
        layout_progress.isFocusable = false
        layout_progress.isClickable = false
    }

    private fun updateProgressBar(intent: Intent) {
        layout_progress.visibility = View.VISIBLE
        layout_progress.isFocusable = true
        layout_progress.isClickable = true

        val step = intent.getStringExtra("step")
        val details = intent.getStringExtra("details")
        text_session_list_progress_step.text = step
        text_session_list_progress_details.text = details
    }

    private fun syncProgressBarDisplayedWithService(intent: Intent) {
        val isActive = intent.getBooleanExtra("isProgressBarActive", false)
        if (isActive) startProgressBar()
        else killProgressBar()
    }

    private fun showToast(intent: Intent) {
    }

    private fun showDialog(intent: Intent) {
        when (intent.getStringExtra("dialogType")) {
            "errorFetchingAssetLists" -> displayAssetListFailureDialog()
            "wifiRequired" -> displayNetworkChoicesDialog()
            "extractionFailed" -> displayExtractionFailedDialog()
            "sessionIsMissingRequiredAssets" -> displayFilesystemMissingRequiredAssets()
        }
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

    private fun displayAssetListFailureDialog() {
        val builder = AlertDialog.Builder(activityContext)
        builder.setMessage(R.string.alert_asset_list_failure_message)
                .setTitle(R.string.alert_asset_list_failure_title)
                .setPositiveButton(R.string.alert_asset_list_failure_positive_button) {
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
                    serviceIntent.putExtra("type", "forceDownloads")
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

    private fun displayExtractionFailedDialog() {
        val builder = AlertDialog.Builder(activityContext)
        builder.setMessage(R.string.alert_extraction_failure_message)
                .setTitle(R.string.alert_extraction_failure_title)
                .setPositiveButton(R.string.alert_extraction_failure_positive_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }

    private fun displayFilesystemMissingRequiredAssets() {
        val builder = AlertDialog.Builder(activityContext)
        builder.setMessage(R.string.alert_filesystem_missing_requirements_message)
                .setTitle(R.string.alert_filesystem_missing_requirements_title)
                .setPositiveButton(R.string.alert_filesystem_missing_requirements_positive_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }
}