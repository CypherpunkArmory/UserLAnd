package tech.ula

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.DownloadManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.annotation.IdRes
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupWithNavController
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.find
import org.jetbrains.anko.toast
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.state.*
import tech.ula.ui.AppListFragment
import tech.ula.ui.SessionListFragment
import tech.ula.utils.*
import tech.ula.viewmodel.*

class MainActivity : AppCompatActivity(), SessionListFragment.SessionSelection, AppListFragment.AppSelection {

    private val permissionRequestCode: Int by lazy {
        getString(R.string.permission_request_code).toInt()
    }

    private var progressBarIsVisible = false
    private var currentFragmentDisplaysProgressDialog = false

    private val navController: NavController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    private val notificationManager by lazy {
        NotificationUtility(this)
    }

    private val downloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == -1L) return
            // TODO what happens if intent received from nonula download
            // TODO submit event
            else viewModel.submitCompletedDownloadId(id)
        }
    }

    private val serverServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("type")?.let { type ->
                when (type) {
                    "sessionActivated" -> handleSessionHasBeenActivated()
                    "toast" -> {
                        val resId = intent.getIntExtra("id", -1)
                        if (resId == -1) return
                        showToast(resId)
                    }
                    "dialog" -> {
                        val type = intent.getStringExtra("dialogType") ?: ""
                        showDialog(type)
                    }
                }
            }
        }
    }

    private val stateObserver = Observer<State> {
        it?.let { state ->
            handleStateUpdate(state)
        }
    }

    private val viewModel: MainActivityViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(this)

        val timestampPreferences = TimestampPreferences(this.getSharedPreferences("file_timestamps", Context.MODE_PRIVATE))
        val assetPreferences = AssetPreferences(this.getSharedPreferences("assetLists", Context.MODE_PRIVATE))
        val assetRepository = AssetRepository(filesDir.path, timestampPreferences, assetPreferences)

        val execUtility = ExecUtility(filesDir.path, Environment.getExternalStorageDirectory().absolutePath, DefaultPreferences(defaultSharedPreferences))
        val filesystemUtility = FilesystemUtility(filesDir.path, execUtility)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadManagerWrapper = DownloadManagerWrapper()
        val downloadUtility = DownloadUtility(downloadManager, timestampPreferences, downloadManagerWrapper, filesDir)

        val appsPreferences = AppsPreferences(this.getSharedPreferences("apps", Context.MODE_PRIVATE))

        val appsStartupFsm = AppsStartupFsm(ulaDatabase, appsPreferences, filesystemUtility)
        val sessionStartupFsm = SessionStartupFsm(ulaDatabase, assetRepository, filesystemUtility, downloadUtility)
        ViewModelProviders.of(this, MainActivityViewModelFactory(appsStartupFsm, sessionStartupFsm))
                .get(MainActivityViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        notificationManager.createServiceNotificationChannel() // Android O requirement

        setNavStartDestination()

        navController.addOnNavigatedListener { _, destination ->
            currentFragmentDisplaysProgressDialog =
                    destination.label == getString(R.string.sessions) ||
                    destination.label == getString(R.string.apps) ||
                    destination.label == getString(R.string.filesystems)
            if (!currentFragmentDisplaysProgressDialog) killProgressBar()
        }

        setupWithNavController(bottom_nav_view, navController)

        viewModel.getState().observe(this, stateObserver)
    }

    private fun setNavStartDestination() {
        val navHostFragment = nav_host_fragment as NavHostFragment
        val inflater = navHostFragment.navController.navInflater
        val graph = inflater.inflate(R.navigation.nav_graph)

        val userPreference = defaultSharedPreferences.getString("pref_default_nav_location", "Apps")
        graph.startDestination = when (userPreference) {
            getString(R.string.sessions) -> R.id.session_list_fragment
            else -> R.id.app_list_fragment
        }
        navHostFragment.navController.graph = graph
    }

    override fun onSupportNavigateUp() = navController.navigateUp()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_options, menu)
        return true
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(serverServiceBroadcastReceiver, IntentFilter(ServerService.SERVER_SERVICE_RESULT))
        registerReceiver(downloadBroadcastReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onResume() {
        super.onResume()

        val intent = Intent(this, ServerService::class.java)
                .putExtra("type", "isProgressBarActive")
        this.startService(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.terms_and_conditions) {
            val intent = Intent("android.intent.action.VIEW", Uri.parse("https://userland.tech/eula"))
            startActivity(intent)
        }
        return NavigationUI.onNavDestinationSelected(item,
                Navigation.findNavController(this, R.id.nav_host_fragment)) ||
                super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(serverServiceBroadcastReceiver)
        unregisterReceiver(downloadBroadcastReceiver)
    }

    override fun appHasBeenSelected(app: App) {
        if (!arePermissionsGranted(this)) {
            showPermissionsNecessaryDialog()
            return
        }
        viewModel.submitAppSelection(app)
    }

    override fun sessionHasBeenSelected(session: Session) {
        if (!arePermissionsGranted(this)) {
            showPermissionsNecessaryDialog()
            return
        }
        viewModel.submitSessionSelection(session)
    }

    private fun handleStateUpdate(newState: State) {
        return when (newState) {
            is CanOnlyStartSingleSession -> { showToast(R.string.single_session_supported) }
            is SessionCanBeStarted -> { startSession(newState.session) }
            is SessionCanBeRestarted -> { restartRunningSession(newState.session) }
            is IllegalState -> {} // TODO
            is UserInputRequiredState -> { handleUserInputState(newState) }
            is ProgressBarUpdateState -> { handleProgressBarUpdateState(newState) }
        }
    }

    private fun startSession(session: Session) {
        val step = getString(R.string.progress_starting)
        val details = ""
        updateProgressBar(step, details)

        val serviceIntent = Intent(this, ServerService::class.java)
                .putExtra("type", "start")
                .putExtra("session", session)
        startService(serviceIntent)
    }

    private fun restartRunningSession(session: Session) {
        val serviceIntent = Intent(this, ServerService::class.java)
                .putExtra("type", "restartRunningSession")
                .putExtra("session", session)
        startService(serviceIntent)
    }

    private fun handleSessionHasBeenActivated() {
        killProgressBar()
    }
    
    private fun showToast(resId: Int) {
        val content = getString(resId)
        Toast.makeText(this, content, Toast.LENGTH_LONG).show()
    }
    
    private fun handleUserInputState(state: UserInputRequiredState) {
        return when (state) {
            is FilesystemCredentialsRequired -> {
                getCredentials()
            }
            is AppServiceTypePreferenceRequired -> {
                getServiceTypePreference()
            }
            is LargeDownloadRequired -> {
                if (wifiIsEnabled()) {
                    viewModel.startAssetDownloads(state.requiredDownloads)
                    return
                }
                displayNetworkChoicesDialog(state.requiredDownloads)
            }
        }
    }

    // TODO sealed classes?
    private fun showDialog(dialogType: String) {
        when (dialogType) {
            "errorFetchingAssetLists" ->
                displayGenericErrorDialog(this, R.string.alert_network_unavailable_title,
                        R.string.alert_network_unavailable_message)
            "extractionFailed" ->
                displayGenericErrorDialog(this, R.string.alert_extraction_failure_title,
                        R.string.alert_extraction_failure_message)
            "filesystemIsMissingRequiredAssets" ->
                displayGenericErrorDialog(this, R.string.alert_filesystem_missing_requirements_title,
                    R.string.alert_filesystem_missing_requirements_message)
            "playStoreMissingForClient" ->
                displayGenericErrorDialog(this, R.string.alert_need_client_app_title,
                    R.string.alert_need_client_app_message)
            "networkTooWeakForDownloads" ->
                displayGenericErrorDialog(this, R.string.general_error_title,
                        R.string.alert_network_strength_too_low_for_downloads)
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun showPermissionsNecessaryDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_permissions_necessary_message)
                .setTitle(R.string.alert_permissions_necessary_title)
                .setPositiveButton(R.string.button_ok) {
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
    
    private fun handleProgressBarUpdateState(state: ProgressBarUpdateState) {
        // TODO
        return when (state) {
            is StartingSetup -> {
                val step = getString(R.string.progress_start_step)
                updateProgressBar(step, "")
            }
            is FetchingAssetLists -> {
                val step = getString(R.string.progress_fetching_asset_lists)
                updateProgressBar(step, "")
            }
            is CheckingForAssetsUpdates -> {
                val step = getString(R.string.progress_checking_for_required_updates)
                updateProgressBar(step, "")
            }
            is DownloadProgress -> {
                val step = getString(R.string.progress_downloading)
                val details = getString(R.string.progress_downloading_out_of, state.numComplete, state.numTotal)
                updateProgressBar(step, details)
            }
            is CopyingDownloads -> {
                val step = getString(R.string.progress_copying_downloads)
                updateProgressBar(step, "")
            }
            is FilesystemExtraction -> {
                val step = getString(R.string.progress_setting_up_filesystem)
                val details = getString(R.string.progress_extraction_details, state.extractionTarget)
                updateProgressBar(step, details)
            }
            is VerifyingFilesystem -> {
                val step = getString(R.string.progress_verifying_assets)
                updateProgressBar(step, "")
            }
        }
    }

    private fun updateProgressBar(step: String, details: String) {
        if (!currentFragmentDisplaysProgressDialog) return

        if (!progressBarIsVisible) {
            val inAnimation = AlphaAnimation(0f, 1f)
            inAnimation.duration = 200
            layout_progress.animation = inAnimation

            layout_progress.visibility = View.VISIBLE
            layout_progress.isFocusable = true
            layout_progress.isClickable = true
            progressBarIsVisible = true
        }

        text_session_list_progress_step.text = step
        text_session_list_progress_details.text = details
    }

    private fun killProgressBar() {
        val outAnimation = AlphaAnimation(1f, 0f)
        outAnimation.duration = 200
        layout_progress.animation = outAnimation
        layout_progress.visibility = View.GONE
        layout_progress.isFocusable = false
        layout_progress.isClickable = false
        progressBarIsVisible = false
    }

    private fun wifiIsEnabled(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in connectivityManager.allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
        }
        return false
    }

    private fun displayNetworkChoicesDialog(downloadsToContinue: List<Asset>) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_wifi_disabled_message)
                .setTitle(R.string.alert_wifi_disabled_title)
                .setPositiveButton(R.string.alert_wifi_disabled_continue_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    viewModel.startAssetDownloads(downloadsToContinue)
                }
                .setNegativeButton(R.string.alert_wifi_disabled_turn_on_wifi_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                    viewModel.handleUserInputCancelled()
                    killProgressBar()
                }
                .setNeutralButton(R.string.alert_wifi_disabled_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    viewModel.handleUserInputCancelled()
                    killProgressBar()
                }
                .setOnCancelListener {
                    viewModel.handleUserInputCancelled()
                    killProgressBar()
                }
                .create()
                .show()
    }

    private fun getCredentials() {
        val dialog = AlertDialog.Builder(this)
        val dialogView = this.layoutInflater.inflate(R.layout.dia_app_credentials, null)
        dialog.setView(dialogView)
        dialog.setCancelable(true)
        dialog.setPositiveButton(R.string.button_continue, null)
        val customDialog = dialog.create()

        customDialog.setOnShowListener { _ ->
            customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { _ ->
                val username = customDialog.find<EditText>(R.id.text_input_username).text.toString()
                val password = customDialog.find<EditText>(R.id.text_input_password).text.toString()
                val vncPassword = customDialog.find<EditText>(R.id.text_input_vnc_password).text.toString()

                if (validateCredentials(username, password, vncPassword)) {
                    customDialog.dismiss()
                    viewModel.submitFilesystemCredentials(username, password, vncPassword)
                }
            }
        }
        customDialog.setOnCancelListener {
            viewModel.handleUserInputCancelled()
        }
        customDialog.show()
    }

    private fun getServiceTypePreference() {
        val dialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dia_app_select_client, null)
        dialog.setView(dialogView)
        dialog.setCancelable(true)
        dialog.setPositiveButton(R.string.button_continue, null)
        val customDialog = dialog.create()

        customDialog.setOnShowListener {
            customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                customDialog.dismiss()
                val sshTypePreference = customDialog.find<RadioButton>(R.id.ssh_radio_button)
                val selectedPreference =
                        if (sshTypePreference.isChecked) SshTypePreference else VncTypePreference
                viewModel.submitAppServicePreference(selectedPreference)
            }
        }
        customDialog.setOnCancelListener {
            viewModel.handleUserInputCancelled()
        }

        customDialog.show()
    }

    // TODO the view shouldn't be responsible for validation
    private fun validateCredentials(username: String, password: String, vncPassword: String): Boolean {
        val validator = ValidationUtility()
        var allCredentialsAreValid = false

        when {
            username.isEmpty() || password.isEmpty() || vncPassword.isEmpty() -> {
                Toast.makeText(this, R.string.error_empty_field, Toast.LENGTH_LONG).show()
            }
            vncPassword.length > 8 || vncPassword.length < 6 -> {
                Toast.makeText(this, R.string.error_vnc_password_length_incorrect, Toast.LENGTH_LONG).show()
            }
            !validator.isUsernameValid(username) -> {
                Toast.makeText(this, R.string.error_username_invalid, Toast.LENGTH_LONG).show()
            }
            !validator.isPasswordValid(password) -> {
                Toast.makeText(this, R.string.error_password_invalid, Toast.LENGTH_LONG).show()
            }
            !validator.isPasswordValid(vncPassword) -> {
                Toast.makeText(this, R.string.error_vnc_password_invalid, Toast.LENGTH_LONG).show()
            }
            else -> {
                allCredentialsAreValid = true
                return allCredentialsAreValid
            }
        }
        return allCredentialsAreValid
    }
}