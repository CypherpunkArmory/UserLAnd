package tech.ula

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.DownloadManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import com.google.android.material.textfield.TextInputEditText
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.appcompat.app.AppCompatActivity
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupWithNavController
import kotlinx.android.synthetic.main.activity_main.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.state.* // ktlint-disable no-wildcard-imports
import tech.ula.ui.AppListFragment
import tech.ula.ui.SessionListFragment
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import tech.ula.viewmodel.* // ktlint-disable no-wildcard-imports
import kotlinx.android.synthetic.main.dia_app_select_client.*
import tech.ula.ui.FilesystemListFragment
import tech.ula.model.repositories.DownloadMetadata

class MainActivity : AppCompatActivity(), SessionListFragment.SessionSelection, AppListFragment.AppSelection, FilesystemListFragment.ExportFilesystem {

    private val permissionRequestCode: Int by lazy {
        getString(R.string.permission_request_code).toInt()
    }

    private var progressBarIsVisible = false
    private var currentFragmentDisplaysProgressDialog = false

    private val logger = SentryLogger()
    private val busyboxExecutor by lazy {
        val prootDebugLogger = ProotDebugLogger(this.defaultSharedPreferences, this.storageRoot.path)
        BusyboxExecutor(filesDir, Environment.getExternalStorageDirectory(), prootDebugLogger)
    }

    private val navController: NavController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    private val notificationManager by lazy {
        NotificationUtility(this)
    }

    private val userFeedbackUtility by lazy {
        UserFeedbackUtility(this.getSharedPreferences("usage", Context.MODE_PRIVATE))
    }

    private val downloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == -1L) return
            else viewModel.submitCompletedDownloadId(id)
        }
    }

    private val serverServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("type")?.let { intentType ->
                logger.addBreadcrumb("Last service broadcast received", intentType)
                when (intentType) {
                    "sessionActivated" -> handleSessionHasBeenActivated()
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

        val assetPreferences = AssetPreferences(this.getSharedPreferences("assetLists", Context.MODE_PRIVATE))
        val assetRepository = AssetRepository(filesDir.path, assetPreferences)

        val filesystemUtility = FilesystemUtility(filesDir.path, busyboxExecutor)
        val storageUtility = StorageUtility(StatFs(Environment.getExternalStorageDirectory().path))

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadManagerWrapper = DownloadManagerWrapper(downloadManager)
        val downloadUtility = DownloadUtility(assetPreferences, downloadManagerWrapper, filesDir)

        val appsPreferences = AppsPreferences(this.getSharedPreferences("apps", Context.MODE_PRIVATE))

        val appsStartupFsm = AppsStartupFsm(ulaDatabase, appsPreferences, filesystemUtility)
        val sessionStartupFsm = SessionStartupFsm(ulaDatabase, assetRepository, filesystemUtility, downloadUtility, storageUtility)
        ViewModelProviders.of(this, MainActivityViewModelFactory(appsStartupFsm, sessionStartupFsm))
                .get(MainActivityViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.initialize(applicationContext)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        notificationManager.createServiceNotificationChannel() // Android O requirement

        setNavStartDestination()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentFragmentDisplaysProgressDialog =
                    destination.label == getString(R.string.sessions) ||
                    destination.label == getString(R.string.apps) ||
                    destination.label == getString(R.string.filesystems)
            if (!currentFragmentDisplaysProgressDialog) killProgressBar()
            else if (progressBarIsVisible) displayProgressBar()
        }

        setupWithNavController(bottom_nav_view, navController)

        userFeedbackUtility.incrementNumberOfTimesOpened()
        if (userFeedbackUtility.askingForFeedbackIsAppropriate())
            setupReviewRequestUI()

        viewModel.getState().observe(this, stateObserver)
    }

    private fun setupReviewRequestUI() {
        val viewHolder = findViewById<ViewGroup>(R.id.request_review_insert_point)
        layoutInflater.inflate(R.layout.list_item_review_request, viewHolder)

        val requestQuestion = viewHolder.findViewById<TextView>(R.id.prompt_review_question)
        val negativeBtn = viewHolder.findViewById<Button>(R.id.btn_negative_response)
        val positiveBtn = viewHolder.findViewById<Button>(R.id.btn_positive_response)

        positiveBtn.setOnClickListener {
            requestQuestion.text = getString(R.string.review_ask_for_rating)
            positiveBtn.text = getString(R.string.button_positive)
            negativeBtn.text = getString(R.string.button_refuse)

            positiveBtn.setOnClickListener {
                handleUserFeedback(viewHolder)
                val userlandPlayStoreURI = "https://play.google.com/store/apps/details?id=tech.ula"
                val intent = Intent("android.intent.action.VIEW", Uri.parse(userlandPlayStoreURI))
                startActivity(intent)
            }

            negativeBtn.setOnClickListener {
                handleUserFeedback(viewHolder)
            }
        }

        negativeBtn.setOnClickListener {
            requestQuestion.text = getString(R.string.review_ask_for_feedback)
            positiveBtn.text = getString(R.string.button_positive)
            negativeBtn.text = getString(R.string.button_negative)

            positiveBtn.setOnClickListener {
                handleUserFeedback(viewHolder)
                val githubURI = "https://github.com/CypherpunkArmory/UserLAnd"
                val intent = Intent("android.intent.action.VIEW", Uri.parse(githubURI))
                startActivity(intent)
            }

            negativeBtn.setOnClickListener {
                handleUserFeedback(viewHolder)
            }
        }
    }

    private fun handleUserFeedback(viewHolder: ViewGroup) {
        userFeedbackUtility.userHasGivenFeedback()
        viewHolder.removeAllViews()
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

        logger.addBreadcrumb("Last call to onResume", "${System.currentTimeMillis()}")
        viewModel.handleOnResume()
        val intent = Intent(this, ServerService::class.java)
                .putExtra("type", "isProgressBarActive")
        this.startService(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.terms_and_conditions) {
            val intent = Intent("android.intent.action.VIEW", Uri.parse("https://userland.tech/eula"))
            startActivity(intent)
        }
        if (item.itemId == R.id.clear_support_files) {
            displayClearSupportFilesDialog()
        }
        return NavigationUI.onNavDestinationSelected(item,
                Navigation.findNavController(this, R.id.nav_host_fragment)) ||
                super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()

        logger.addBreadcrumb("Last call to onStop", "${System.currentTimeMillis()}")
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(serverServiceBroadcastReceiver)
        unregisterReceiver(downloadBroadcastReceiver)
    }

    override fun appHasBeenSelected(app: App) {
        if (!arePermissionsGranted(this)) {
            showPermissionsNecessaryDialog()
            viewModel.waitForPermissions(appToContinue = app)
            return
        }
        viewModel.submitAppSelection(app)
    }

    override fun sessionHasBeenSelected(session: Session) {
        if (!arePermissionsGranted(this)) {
            showPermissionsNecessaryDialog()
            viewModel.waitForPermissions(sessionToContinue = session)
            return
        }
        viewModel.submitSessionSelection(session)
    }

    private fun handleStateUpdate(newState: State) {
        logger.addBreadcrumb("Last observed state from viewmodel", "$newState")
        return when (newState) {
            is CanOnlyStartSingleSession -> { showToast(R.string.single_session_supported) }
            is SessionCanBeStarted -> { prepareSessionForStart(newState.session) }
            is SessionCanBeRestarted -> { restartRunningSession(newState.session) }
            is IllegalState -> { handleIllegalState(newState) }
            is UserInputRequiredState -> { handleUserInputState(newState) }
            is ProgressBarUpdateState -> { handleProgressBarUpdateState(newState) }
        }
    }

    private fun prepareSessionForStart(session: Session) {
        val step = getString(R.string.progress_starting)
        val details = ""
        updateProgressBar(step, details)

        // TODO: Alert user when defaulting to VNC
        if (session.serviceType == "xsdl" && Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
            session.serviceType = "vnc"
        }

        when (session.serviceType) {
            "xsdl" -> {
                viewModel.lastSelectedSession = session
                sendXsdlIntentToSetDisplayNumberAndExpectResult()
            }
            "vnc" -> {
                getDeviceDimensions(session)
                startSession(session)
            }
            else -> startSession(session)
        }
    }

    private fun getDeviceDimensions(session: Session) {
        val deviceDimensions = DeviceDimensions()
        val windowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        deviceDimensions.getDeviceDimensions(windowManager, DisplayMetrics())
        session.geometry = deviceDimensions.getGeometry()
    }

    private fun startSession(session: Session) {
        val serviceIntent = Intent(this, ServerService::class.java)
                .putExtra("type", "start")
                .putExtra("session", session)
        startService(serviceIntent)
    }

    /*
    XSDL has a different flow than starting SSH/VNC session.  It sends an intent to XSDL with
        with a display value.  Then XSDL sends an intent to open UserLAnd signalling
        that it has an xserver listening.  We set the initial display number as an environment variable
        then start a twm process to connect to XSDL's xserver.
    */
    private fun sendXsdlIntentToSetDisplayNumberAndExpectResult() {
        try {
            val xsdlIntent = Intent(Intent.ACTION_MAIN, Uri.parse("x11://give.me.display:4721"))
            val setDisplayRequestCode = 1
            startActivityForResult(xsdlIntent, setDisplayRequestCode)
        } catch (e: Exception) {
            val appPackageName = "x.org.server"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
            } catch (error: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.let {
            val session = viewModel.lastSelectedSession
            val result = data.getStringExtra("run")
            if (session.serviceType == "xsdl" && result.isNotEmpty()) {
                startSession(session)
            }
        }
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
        logger.addBreadcrumb("Last handled user input state", "$state")
        return when (state) {
            is LowStorageAcknowledgementRequired -> {
                displayLowStorageDialog()
            }
            is FilesystemCredentialsRequired -> {
                getCredentials()
            }
            is AppServiceTypePreferenceRequired -> {
                getServiceTypePreference()
            }
            is LargeDownloadRequired -> {
                if (wifiIsEnabled()) {
                    viewModel.startAssetDownloads(state.downloadRequirements)
                    return
                }
                displayNetworkChoicesDialog(state.downloadRequirements)
            }
            is ActiveSessionsMustBeDeactivated -> {
                displayGenericErrorDialog(this, R.string.general_error_title, R.string.deactivate_sessions)
            }
        }
    }

    private fun handleIllegalState(state: IllegalState) {
        val stateDescription = IllegalStateHandler().getLocalizationData(state).getString(this)
        val displayMessage = getString(R.string.illegal_state_github_message, stateDescription)

        AlertDialog.Builder(this)
                .setMessage(displayMessage)
                .setTitle(R.string.illegal_state_title)
                .setPositiveButton(R.string.button_ok) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create().show()
    }

    // TODO sealed classes?
    private fun showDialog(dialogType: String) {
        when (dialogType) {
            "unhandledSessionServiceType" -> {
                displayGenericErrorDialog(this, R.string.general_error_title,
                        R.string.illegal_state_unhandled_session_service_type)
            }
            "playStoreMissingForClient" ->
                displayGenericErrorDialog(this, R.string.alert_need_client_app_title,
                    R.string.alert_need_client_app_message)
        }
    }

    private fun displayClearSupportFilesDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.alert_clear_support_files_message)
                .setTitle(R.string.alert_clear_support_files_title)
                .setPositiveButton(R.string.alert_clear_support_files_clear_button) { dialog, _ ->
                    handleClearSupportFiles()
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.button_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .create().show()
    }

    private fun handleClearSupportFiles() {
        val appsPreferences = AppsPreferences(this.getSharedPreferences("apps", Context.MODE_PRIVATE))
        val assetDirectoryNames = appsPreferences.getDistributionsList().plus("support")
        val assetFileClearer = AssetFileClearer(this.filesDir, assetDirectoryNames, busyboxExecutor)
        CoroutineScope(Dispatchers.Main).launch { viewModel.handleClearSupportFiles(assetFileClearer) }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permissionRequestCode -> {

                val grantedPermissions = (grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED)

                if (grantedPermissions) {
                    viewModel.permissionsHaveBeenGranted()
                } else {
                    showPermissionsNecessaryDialog()
                }
            }
        }
    }

    private fun handleProgressBarUpdateState(state: ProgressBarUpdateState) {
        logger.addBreadcrumb("Last handled progress bar update state", "$state")
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
            is VerifyingFilesystem -> {
                val step = getString(R.string.progress_verifying_assets)
                updateProgressBar(step, "")
            }
            is VerifyingAvailableStorage -> {
                val step = getString(R.string.progress_verifying_sufficient_storage)
                updateProgressBar(step, "")
            }
            is FilesystemExtractionStep -> {
                val step = getString(R.string.progress_setting_up_filesystem)
                val details = getString(R.string.progress_extraction_details, state.extractionTarget)
                updateProgressBar(step, details)
            }
            is ClearingSupportFiles -> {
                val step = getString(R.string.progress_clearing_support_files)
                updateProgressBar(step, "")
            }
            is ProgressBarOperationComplete -> {
                killProgressBar()
            }
        }
    }

    override fun updateExportProgress(details: String) {
        val step = getString(R.string.progress_exporting_filesystem)
        updateProgressBar(step, details)
    }

    override fun stopExportProgress() {
        killProgressBar()
    }

    private fun displayProgressBar() {
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
    }

    private fun updateProgressBar(step: String, details: String) {
        displayProgressBar()

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

    private fun displayNetworkChoicesDialog(downloadsToContinue: List<DownloadMetadata>) {
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
                val username = customDialog.find<TextInputEditText>(R.id.text_input_username).text.toString()
                val password = customDialog.find<TextInputEditText>(R.id.text_input_password).text.toString()
                val vncPassword = customDialog.find<TextInputEditText>(R.id.text_input_vnc_password).text.toString()

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

    private fun displayLowStorageDialog() {
        displayGenericErrorDialog(this, R.string.alert_storage_low_title, R.string.alert_storage_low_message) {
            viewModel.lowAvailableStorageAcknowledged()
        }
    }

    private fun getServiceTypePreference() {
        val dialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dia_app_select_client, null)
        dialog.setView(dialogView)
        dialog.setCancelable(true)
        dialog.setPositiveButton(R.string.button_continue, null)
        val customDialog = dialog.create()

        customDialog.setOnShowListener {
            val sshTypePreference = customDialog.find<RadioButton>(R.id.ssh_radio_button)
            val vncTypePreference = customDialog.find<RadioButton>(R.id.vnc_radio_button)
            val xsdlTypePreference = customDialog.find<RadioButton>(R.id.xsdl_radio_button)

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                xsdlTypePreference.isEnabled = false
                xsdlTypePreference.alpha = 0.5f

                val xsdlSupportedText = customDialog.findViewById<TextView>(R.id.text_xsdl_version_supported_description)
                xsdlSupportedText.visibility = View.VISIBLE
            }

            if (!viewModel.lastSelectedApp.supportsCli) {
                sshTypePreference.isEnabled = false
                sshTypePreference.alpha = 0.5f
            }

            customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                customDialog.dismiss()
                val selectedPreference = when {
                    sshTypePreference.isChecked -> SshTypePreference
                    vncTypePreference.isChecked -> VncTypePreference
                    xsdlTypePreference.isChecked -> XsdlTypePreference
                    else -> PreferenceHasNotBeenSelected
                }
                viewModel.submitAppServicePreference(selectedPreference)
            }
        }
        customDialog.setOnCancelListener {
            viewModel.handleUserInputCancelled()
        }

        customDialog.show()
    }

    private fun validateCredentials(username: String, password: String, vncPassword: String): Boolean {
        val blacklistedUsernames = this.resources.getStringArray(R.array.blacklisted_usernames)
        val validator = ValidationUtility()

        val usernameCredentials = validator.validateUsername(username, blacklistedUsernames)
        val passwordCredentials = validator.validatePassword(password)
        val vncPasswordCredentials = validator.validateVncPassword(vncPassword)

        return when {
            !usernameCredentials.credentialIsValid -> {
                Toast.makeText(this, usernameCredentials.errorMessageId, Toast.LENGTH_LONG).show()
                false
            }
            !passwordCredentials.credentialIsValid -> {
                Toast.makeText(this, passwordCredentials.errorMessageId, Toast.LENGTH_LONG).show()
                false
            }
            !vncPasswordCredentials.credentialIsValid -> {
                Toast.makeText(this, vncPasswordCredentials.errorMessageId, Toast.LENGTH_LONG).show()
                false
            }
            else -> true
        }
    }
}