package tech.ula

import android.app.AlertDialog
import android.app.DownloadManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import android.os.* // ktlint-disable no-wildcard-imports
import com.google.android.material.textfield.TextInputEditText
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.animation.AlphaAnimation
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.ula.model.entities.App
import tech.ula.model.entities.AppType
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AppRepository
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.FilesystemRepository
import tech.ula.model.repositories.SessionRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.viewmodel.MainActivityViewModel
import tech.ula.viewmodel.MainActivityViewModelFactory
import tech.ula.viewmodel.State
import tech.ula.viewmodel.State.* // ktlint-disable no-wildcard-imports
import tech.ula.viewmodel.state.* // ktlint-disable no-wildcard-imports
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import android.util.DisplayMetrics
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : UlaBaseActivity(),
        AppsFragment.AppSelection,
        AppsFragment.SessionSelection,
        SessionsFragment.SessionSelection,
        FilesystemListFragment.FilesystemAction,
        FilesystemListFragment.FilesystemProgress,
        SessionListProgress {

    private val className = "MainActivity"

    private val logger = SentryLogger()
    private val ulaFiles by lazy { UlaFiles(this, this.applicationInfo.nativeLibraryDir) }
    private val busyboxExecutor by lazy {
        val prootDebugLogger = ProotDebugLogger(this.defaultSharedPreferences, ulaFiles)
        BusyboxExecutor(ulaFiles, prootDebugLogger)
    }

    private val devModeEnabled: Boolean
        get() = defaultSharedPreferences.getBoolean("pref_dev_mode_enabled", false)

    private val navController: NavController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    private val notificationManager by lazy {
        NotificationConstructor(this)
    }

    private val billingManager by lazy {
        val assetManager = this.assets
        val purchaseHandler = PurchaseHandler(
                this,
                assetManager,
                layout_user_prompt_insert,
                layout_user_prompt_subscribed,
                animationView
        )
        BillingManager(this, purchaseHandler)
    }

    private var autoStarted = false
    private var currentFragmentDisplaysProgressDialog = false
    private var progressBarIsVisible = false

    private val contributionPrompter by lazy {
        ContributionPrompter(this, findViewById(R.id.layout_user_prompt_insert))
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
                val breadcrumb = UlaBreadcrumb(className, BreadcrumbType.ReceivedIntent, intentType)
                logger.addBreadcrumb(breadcrumb)
                if (devModeEnabled) {
                    Log.d(className, "ServerService broadcast: type=$intentType extras=${intent.extras}")
                }
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
        val breadcrumb = UlaBreadcrumb(className, BreadcrumbType.ObservedState, "$it")
        logger.addBreadcrumb(breadcrumb)
        it?.let { state ->
            handleStateUpdate(state)
        }
    }

    private val viewModel: MainActivityViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(this)

        val assetPreferences = AssetPreferences(this)
        val githubApiClient = GithubApiClient(ulaFiles)
        val assetRepository = AssetRepository(filesDir.path, assetPreferences, githubApiClient)

        val filesystemManager = FilesystemManager(ulaFiles, busyboxExecutor)
        val storageCalculator = StorageCalculator(StatFs(filesDir.path))

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadManagerWrapper = DownloadManagerWrapper(downloadManager)
        val assetDownloader = AssetDownloader(assetPreferences, downloadManagerWrapper, ulaFiles)

        val appsStartupFsm = AppsStartupFsm(ulaDatabase, filesystemManager, ulaFiles)
        val sessionStartupFsm = SessionStartupFsm(ulaDatabase, assetRepository, filesystemManager, assetDownloader, storageCalculator)
        ViewModelProviders.of(this, MainActivityViewModelFactory(appsStartupFsm, sessionStartupFsm))
                .get(MainActivityViewModel::class.java)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.type.equals("settings"))
            navController.navigate(R.id.settings_fragment)
        else
            autoStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        notificationManager.createServiceNotificationChannel() // Android O requirement

        setNavStartDestination()
        setProgressDialogNavListeners()
        setStateObservers()

        contributionPrompter.handleContributionPrompt()

        handleQWarning()
        autoStart()
    }

    private fun setNavStartDestination() {
        val navView = nav_view
        val bottomNavigationView = bottom_navigation
        NavigationUI.setupWithNavController(navView, navController)
        setupWithNavController(bottomNavigationView, navController)

        val userPreference = defaultSharedPreferences.getString("pref_default_nav_location", "Apps")
        when (userPreference) {
            "Apps" -> {
                navController.navigate(R.id.appsFragment)
                navView.setCheckedItem(R.id.nav_apps)
            }
            "Sessions" -> {
                navController.navigate(R.id.sessionsFragment)
                navView.setCheckedItem(R.id.nav_sessions)
            }
        }

        val orientation = resources.configuration.orientation
        val deviceDimensions = DeviceDimensions
        deviceDimensions.saveDeviceDimensions(windowManager, DisplayMetrics(), orientation, defaultSharedPreferences)
    }

    private fun setProgressDialogNavListeners() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentFragmentDisplaysProgressDialog = when (destination.id) {
                R.id.appsFragment -> false
                R.id.sessionsFragment -> false
                R.id.filesystemsListFragment -> true
                R.id.filesystemDetailsFragment -> true
                else -> currentFragmentDisplaysProgressDialog
            }
            if (!currentFragmentDisplaysProgressDialog) killProgressBar()
            else if (progressBarIsVisible) displayProgressBar()
        }
    }

    private fun handleQWarning() {
        val handler = QWarningHandler(this.getSharedPreferences(QWarningHandler.prefsString, Context.MODE_PRIVATE), ulaFiles)
        if (handler.messageShouldBeDisplayed()) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.q_warning_title)
                    .setMessage(R.string.q_warning_message)
                    .setPositiveButton(R.string.button_ok) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNeutralButton(R.string.wiki) {
                        dialog, _ ->
                        dialog.dismiss()
                        sendWikiIntent()
                    }
                    .create().show()
            handler.messageHasBeenDisplayed()
        }
    }

    override fun onSupportNavigateUp() = navController.navigateUp()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_options, menu)
        return true
    }

    private fun autoStart() {
        val prefs = getSharedPreferences("apps", Context.MODE_PRIVATE)
        val json = prefs.getString("AutoApp", " ")
        if (json.isNullOrBlank() || json == " ") return

        try {
            val gson = Gson()
            val autoApp = gson.fromJson(json, App::class.java)
            if (autoApp != null) {
                autoStarted = true
                appHasBeenSelected(autoApp, true)
            } else {
                prefs.edit().remove("AutoApp").apply()
                if (devModeEnabled) {
                    Toast.makeText(this, "Auto-start app configuration is invalid, resetting.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            logger.addBreadcrumb(
                UlaBreadcrumb(
                    className,
                    BreadcrumbType.RuntimeError,
                    "autoStart deserialization error: ${e.message}"
                )
            )
            prefs.edit().remove("AutoApp").apply()
            if (devModeEnabled) {
                Toast.makeText(this, "Auto-start app configuration is invalid, resetting.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(serverServiceBroadcastReceiver, IntentFilter(ServerService.SERVER_SERVICE_RESULT))
        registerReceiver(downloadBroadcastReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onResume() {
        super.onResume()
        billingManager.querySubPurchases()
        billingManager.queryInAppPurchases()
        viewModel.handleOnResume()
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.terms_and_conditions) {
            val intent = Intent("android.intent.action.VIEW", Uri.parse("https://userland.tech/eula"))
            startActivity(intent)
            return true
        }
        return NavigationUI.onNavDestinationSelected(item, navController) || super.onOptionsItemSelected(item)
    }

    override fun updateState(state: State) {
        viewModel.updateState(state)
    }

    private fun setStateObservers() {
        viewModel.state.observe(this, stateObserver)
    }

    private fun handleStateUpdate(state: State) {
        val stateHandler = stateHandlerForState(state)

        when (stateHandler) {
            is AlertHandler -> displayAlertDialog(stateHandler.alertId)
            is DialogHandler -> showDialog(stateHandler.dialogTag)
            is NetworkDialogHandler -> displayNetworkChoicesDialog(stateHandler.downloadsToContinue)
            is ProgressBarHandler -> {
                val step = getString(stateHandler.stepId)
                updateProgressBar(step, "")
            }
            is ProgressBarWithDetailsHandler -> {
                val step = getString(stateHandler.stepId)
                val details = getString(stateHandler.detailsId)
                updateProgressBar(step, details)
            }
            is AppsFragmentDisplayHandler -> displayAppsList()
            is SessionsFragmentDisplayHandler -> displaySessionsList()
        }
    }

    private fun showDialog(type: String) {
        when (type) {
            "networkUnreachable" -> displayAlertDialog(R.string.error_network_unreachable_message)
            "filesystemExtractionFailed" -> displayAlertDialog(R.string.error_extraction_failed_message)
            "filesystemDeletionFailed" -> displayAlertDialog(R.string.error_filesystem_delete_message)
            "assetsCopyFailed" -> displayAlertDialog(R.string.error_copying_assets_to_filesystem_message)
            "playStoreMissingForClient" -> displayAlertDialog(R.string.play_store_missing_for_client)
            "notEnoughStorage" -> displayAlertDialog(R.string.error_not_enough_storage_message)
            "filesystemImportFailed" -> displayAlertDialog(R.string.error_importing_filesystem_message)
            "ownershipFailure" -> displayAlertDialog(R.string.error_ownership_failure_message)
            "noVncAuthentication" -> displayAlertDialog(R.string.error_vnc_authentication)
            "unsupportedSessionType" -> displayAlertDialog(R.string.error_unsupported_session_type)
            "storagePermissionDenied" -> displayAlertDialog(R.string.error_storage_permission_denied_message)
            "restartApp" -> displayAlertDialog(R.string.error_restart_app_message)
            "fetchDistributionImageFailed" -> displayAlertDialog(R.string.error_fetching_distribution_list)
            "cancelledDownloads" -> displayAlertDialog(R.string.error_downloading_assets_message)
            "permissionDenied" -> displayAlertDialog(R.string.error_permission_denied)
            "sessionTimeoutError" -> displayAlertDialog(R.string.error_session_timeout)
            "criticalServiceError" -> displayAlertDialog(R.string.error_critical_service)
        }
    }

    private fun displayAlertDialog(messageId: Int) {
        AlertDialog.Builder(this)
                .setTitle(R.string.generic_error_title)
                .setMessage(messageId)
                .setPositiveButton(R.string.button_ok) { dialog, _ ->
                    dialog.dismiss()
                    viewModel.handleUserInputCancelled()
                }
                .create().show()
    }

    private fun displayAppsList() {
        navController.navigate(R.id.appsFragment)
    }

    private fun displaySessionsList() {
        navController.navigate(R.id.sessionsFragment)
    }

    override fun appHasBeenSelected(app: App, autoStart: Boolean) {
        if (!app.isPaidApp) {
            viewModel.submitAppSelection(app, autoStart)
            return
        }

        if (billingManager.subscribedToSupport()) {
            viewModel.submitAppSelection(app, autoStart)
        } else {
            displayAlertDialog(R.string.alert_not_subscribed_message)
        }
    }

    override fun sessionHasBeenSelected(session: Session, autoRestart: Boolean) {
        val previousState = viewModel.state.value
        if (previousState is SessionCanBeStarted) {
            val state = SessionCanBeRestarted(session)
            updateState(state)
        } else {
            viewModel.submitSessionSelection(session, autoRestart)
        }
    }

    override fun sessionListSessionSelection(session: Session) {
        viewModel.submitSessionSelection(session, autoRestart = false)
    }

    override fun createNewFilesystem() {
        navController.navigate(R.id.filesystemDetailsFragment)
    }

    override fun filesystemHasBeenSelected(filesystem: Filesystem) {
        val action = FilesystemListFragmentDirections.actionFilesystemListToFilesystemDetails(filesystem)
        navController.navigate(action)
    }

    override fun filesystemCreateShortcut(filesystem: Filesystem) {
        viewModel.submitFilesystemSelection(filesystem)
    }

    override fun startProgressFromFilesystemList() {
        displayProgressBar()
    }

    override fun stopProgressFromFilesystemList() {
        killProgressBar()
    }

    // Cached animations to evitar realocação a cada update (pequeno ganho de GC e throughput UI)
    private val progressFadeIn by lazy {
        AlphaAnimation(0f, 1f).apply { duration = 200 }
    }

    private val progressFadeOut by lazy {
        AlphaAnimation(1f, 0f).apply { duration = 200 }
    }

    private fun displayProgressBar() {
        if (!currentFragmentDisplaysProgressDialog) return

        if (!progressBarIsVisible) {
            layout_progress.animation = progressFadeIn
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
        layout_progress.animation = progressFadeOut
        layout_progress.visibility = View.GONE
        layout_progress.isFocusable = false
        layout_progress.isClickable = false
        progressBarIsVisible = false
    }

    private fun wifiIsEnabled(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in connectivityManager.allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) return true
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
                .setNeutralButton(R.string.button_cancel) {
                    dialog, _ ->
                    dialog.dismiss()
                    viewModel.handleUserInputCancelled()
                    killProgressBar()
                }
        builder.create().show()
    }

    override fun updateFilesystemImportProgress(details: String) {
        val step = getString(R.string.progress_importing_filesystem)
        updateProgressBar(step, details)
    }

    override fun updateFilesystemExportProgress(details: String) {
        val step = getString(R.string.progress_exporting_filesystem)
        updateProgressBar(step, details)
    }

    override fun updateFilesystemDeleteProgress() {
        val step = getString(R.string.progress_deleting_filesystem)
        updateProgressBar(step, "")
    }

    override fun stopProgressFromFilesystemList() {
        killProgressBar()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1) return

        val session = viewModel.lastSelectedSession
        if (session == null) {
            if (devModeEnabled) {
                logger.addBreadcrumb(
                    UlaBreadcrumb(
                        className,
                        BreadcrumbType.RuntimeError,
                        "onActivityResult(XSDL): lastSelectedSession is null"
                    )
                )
            }
            return
        }

        val result = data?.getStringExtra("run") ?: ""
        if (session.serviceType == ServiceType.Xsdl && result.isNotEmpty()) {
            startSession(session)
        } else if (devModeEnabled) {
            logger.addBreadcrumb(
                UlaBreadcrumb(
                    className,
                    BreadcrumbType.RuntimeError,
                    "onActivityResult(XSDL): unexpected result='$result' for serviceType=${session.serviceType}"
                )
            )
        }
    }

    private fun restartRunningSession(session: Session) {
        val serviceIntent = Intent(this, ServerService::class.java)
                .putExtra("type", "restartRunningSession")
                .putExtra("session", session)
        startService(serviceIntent)
        if (autoStarted) {
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
        }
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

    private fun startSession(session: Session) {
        when (session.serviceType) {
            ServiceType.Ssh, ServiceType.Vnc -> startServer(session)
            ServiceType.Xsdl -> sendXsdlIntentToSetDisplayNumberAndExpectResult()
            else -> showDialog("unsupportedSessionType")
        }
    }

    private fun startServer(session: Session) {
        val serviceIntent = Intent(this, ServerService::class.java)
                .putExtra("type", "start")
                .putExtra("session", session)
        startService(serviceIntent)
        displayProgressBar()
    }

    private fun sendWikiIntent() {
        Intent(Intent.ACTION_VIEW).also {
            it.data = Uri.parse(getString(R.string.q_warning_link))
            if (it.resolveActivity(packageManager) != null) {
                startActivity(it)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(serverServiceBroadcastReceiver)
        unregisterReceiver(downloadBroadcastReceiver)
    }

    override fun updateProgressBarForSessionDownloadProgress(downloadProgress: DownloadProgress) {
        val details = getString(R.string.progress_downloading_out_of, downloadProgress.numComplete, downloadProgress.numTotal)
        val step = getString(R.string.progress_downloading)
        updateProgressBar(step, details)
    }

    override fun updateProgressBarForSessionProgress(state: State) {
        when (state) {
            is DownloadProgress -> {
                val details = getString(R.string.progress_downloading_out_of, state.numComplete, state.numTotal)
                val step = getString(R.string.progress_downloading)
                updateProgressBar(step, details)
            }
            is Initialization -> {
                val step = getString(R.string.progress_initialization)
                updateProgressBar(step, "")
            }
            is PermissionsCheckNeeded -> {
                val step = getString(R.string.progress_checking_permissions)
                updateProgressBar(step, "")
            }
            is PermissionsCheckCompletedSuccessfully -> {
                val step = getString(R.string.progress_permissions_check_complete)
                updateProgressBar(step, "")
            }
            is AssetsHaveBeenDownloadedSuccessfully -> {
                val step = getString(R.string.progress_assets_downloaded)
                updateProgressBar(step, "")
            }
            is AssetListsFetchProgress -> {
                val step = getString(R.string.progress_fetching_asset_lists)
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
            is AssetsCopierCompletedSuccessfully -> {
                val step = getString(R.string.progress_assets_copied)
                updateProgressBar(step, "")
            }
            is FilesystemSyncProgress -> {
                val step = getString(R.string.progress_checking_filesystem_image_state)
                updateProgressBar(step, "")
            }
            is FilesystemExtractionProgress -> {
                val step = getString(R.string.progress_filesystem_extraction)
                val details = getString(R.string.progress_filesystem_extraction_out_of, state.numComplete, state.numTotal)
                updateProgressBar(step, details)
            }
            is FilesystemExtractionCompletedSuccessfully -> {
                val step = getString(R.string.progress_filesystem_extraction_complete)
                updateProgressBar(step, "")
            }
            is SessionPreparationProgress -> {
                val step = getString(R.string.progress_preparing_session)
                val details = getString(R.string.progress_preparing_session_details, state.numComplete, state.numTotal)
                updateProgressBar(step, details)
            }
            is SessionCanBeStarted,
            is SessionCanBeRestarted,
            is SessionStartedSuccessfully -> {
                killProgressBar()
            }
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view?.let {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun updateCredentials(usernameCredentials: CredentialValidationResult, passwordCredentials: CredentialValidationResult, vncPasswordCredentials: CredentialValidationResult) {
        if (!usernameCredentials.isValid) {
            usernameCredentials.errorMessageId?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
        if (!passwordCredentials.isValid) {
            passwordCredentials.errorMessageId?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
        if (!vncPasswordCredentials.isValid) {
            vncPasswordCredentials.errorMessageId?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun copyTextToClipboard(text: String) {
        clipboardManager.primaryClip = ClipData.newPlainText("Copied Text", text)
        Toast.makeText(this, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun promptUserToPickAppName(filesystemName: String, appType: AppType): String {
        val builder = AlertDialog.Builder(this)

        val dialogLayout = layoutInflater.inflate(R.layout.dialog_with_text_input, null)
        builder.setView(dialogLayout)
        dialogLayout.findViewById<TextView>(R.id.dialog_title).setText(R.string.title_setup_an_app)

        val editText = dialogLayout.findViewById<TextInputEditText>(R.id.text_input_layout)

        editText.hint = getString(R.string.hint_default_app_name, filesystemName)

        val radioButton1 = dialogLayout.findViewById<RadioButton>(R.id.radio_button_choice_one)
        val radioButton2 = dialogLayout.findViewById<RadioButton>(R.id.radio_button_choice_two)
        val radioButton3 = dialogLayout.findViewById<RadioButton>(R.id.radio_button_choice_three)

        radioButton1.text = getString(R.string.layout_choice_default_app_name, filesystemName)
        when (appType) {
            AppType.Userland -> {
                radioButton2.text = getString(R.string.layout_choice_default_app_name_userland)
                radioButton3.text = getString(R.string.layout_choice_default_app_name_custom)
            }
            AppType.Ssh -> {
                radioButton2.text = getString(R.string.layout_choice_default_app_name_ssh)
                radioButton3.text = getString(R.string.layout_choice_default_app_name_custom)
            }
        }

        var appName = filesystemName

        radioButton1.setOnClickListener {
            editText.visibility = View.GONE
            appName = filesystemName
        }
        radioButton2.setOnClickListener {
            editText.visibility = View.GONE
            appName = radioButton2.text.toString()
        }
        radioButton3.setOnClickListener {
            editText.visibility = View.VISIBLE
            editText.requestFocus()
            appName = editText.text.toString()
        }

        builder.setPositiveButton(R.string.button_ok) { dialog, _ ->
            if (editText.visibility == View.VISIBLE) {
                appName = editText.text.toString()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.button_cancel) { dialog, _ ->
            dialog.cancel()
        }

        val alertDialog = builder.create()
        alertDialog.setOnShowListener {
            radioButton1.isChecked = true
        }
        alertDialog.show()

        return appName
    }
}
