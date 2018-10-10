package tech.ula.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ContextMenu
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.view.MenuInflater
import android.view.animation.AnimationUtils
import android.widget.Toast
import android.widget.EditText
import android.widget.RadioButton
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_app_list.* // ktlint-disable no-wildcard-imports
import org.jetbrains.anko.bundleOf
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.find
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.remote.GithubAppsFetcher
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.AppsPreferences
import tech.ula.utils.DefaultPreferences
import tech.ula.utils.ExecUtility
import tech.ula.utils.FilesystemUtility
import tech.ula.utils.PlayServiceManager
import tech.ula.utils.ValidationUtility
import tech.ula.utils.arePermissionsGranted
import tech.ula.utils.displayGenericErrorDialog
import tech.ula.viewmodel.AppListViewModel
import tech.ula.viewmodel.AppListViewModelFactory

class AppListFragment : Fragment(), PlayServiceManager.PlayServicesUpdateListener, AppListAdapter.OnAppsItemClicked {

    private lateinit var activityContext: Activity
    private val permissionRequestCode: Int by lazy {
        activityContext.resources.getString(R.string.permission_request_code).toInt()
    }

    private lateinit var appList: List<App>
    private lateinit var appAdapter: AppListAdapter

    private lateinit var activeSessions: List<Session>

    private val unselectedApp = App(name = "unselected")
    private var lastSelectedApp = unselectedApp

    private var billingClientIsConnected = false
    private val playServiceManager by lazy {
        PlayServiceManager(this)
    }

    private lateinit var filesystemList: List<Filesystem>

    private val execUtility by lazy {
        val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        ExecUtility(activityContext.filesDir.path, externalStoragePath, DefaultPreferences(activityContext.defaultSharedPreferences))
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(activityContext.filesDir.path, execUtility)
    }

    private var refreshStatus = RefreshStatus.INACTIVE

    private val appListPreferences by lazy {
        AppsPreferences(activityContext.getSharedPreferences("apps", Context.MODE_PRIVATE))
    }

    private val appListViewModel: AppListViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(activityContext)
        val sessionDao = ulaDatabase.sessionDao()
        val appsDao = ulaDatabase.appsDao()
        val filesystemDao = ulaDatabase.filesystemDao()
        val githubFetcher = GithubAppsFetcher("${activityContext.filesDir}")

        val appsRepository = AppsRepository(appsDao, githubFetcher, appListPreferences)
        ViewModelProviders.of(this, AppListViewModelFactory(appsRepository, sessionDao, filesystemDao)).get(AppListViewModel::class.java)
    }

    private val appsAndActiveSessionObserver = Observer<Pair<List<App>, List<Session>>> {
        it?.let {
            appList = it.first
            activeSessions = it.second
            appAdapter = AppListAdapter(activityContext, this, appList, activeSessions)
            list_apps.adapter = appAdapter
            if (appList.isEmpty()) {
                doRefresh()
            }
        }
    }

    private val refreshStatusObserver = Observer<RefreshStatus> {
        it?.let {
            refreshStatus = it
            swipe_refresh.isRefreshing = refreshStatus == RefreshStatus.ACTIVE
        }
    }

    private val filesystemObserver = Observer<List<Filesystem>> {
        it?.let {
            filesystemList = it
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    private fun runLayoutAnimation(recyclerView: RecyclerView, resourceId: Int) {
        val context = recyclerView.context
        val controller = AnimationUtils.loadLayoutAnimation(context, resourceId)

        list_apps.layoutAnimation = controller
        list_apps.adapter?.notifyDataSetChanged()
        list_apps.scheduleLayoutAnimation()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.menu_refresh, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when {
            item?.itemId == R.id.menu_item_refresh -> {
                swipe_refresh.isRefreshing = true
                doRefresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onAppsItemClicked(appsItemClicked: AppsListItem) {
        when (appsItemClicked) {
            is AppSeparatorItem -> {}
            is AppItem -> { doAppItemClicked(appsItemClicked.app) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
        appListViewModel.getAppsAndActiveSessions().observe(viewLifecycleOwner, appsAndActiveSessionObserver)
        appListViewModel.getRefreshStatus().observe(viewLifecycleOwner, refreshStatusObserver)
        appListViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemObserver)

        if (playServiceManager.playServicesAreAvailable(activityContext)) {
            playServiceManager.startBillingClient(activityContext)
        }
        list_apps.layoutManager = LinearLayoutManager(list_apps.context)
        list_apps.adapter = AppListAdapter(activityContext, this)

        registerForContextMenu(list_apps)

        swipe_refresh.setOnRefreshListener { doRefresh() }
        runLayoutAnimation(list_apps, R.anim.layout_animation_from_right)
    }

    private fun doRefresh() {
        appListViewModel.refreshAppsList()
        runLayoutAnimation(list_apps, R.anim.layout_animation_from_right)
    }

    private fun doAppItemClicked(selectedApp: App) {
        lastSelectedApp = selectedApp
        if (arePermissionsGranted(activityContext)) {
            handleAppSelection(lastSelectedApp)
        } else {
            showPermissionsNecessaryDialog()
        }
    }

    private fun handleAppSelection(selectedApp: App) {
        if (selectedApp.isPaidApp) {
            when {
                !playServiceManager.playStoreIsAvailable(activityContext.packageManager) -> {
                    displayGenericErrorDialog(activityContext, R.string.general_error_title,
                            R.string.alert_play_store_required)
                    return
                }
                !playServiceManager.playServicesAreAvailable(activityContext) -> {
                    displayGenericErrorDialog(activityContext, R.string.general_error_title,
                            R.string.alert_play_service_error_message)
                    return
                }
                !playServiceManager.userHasYearlyAppsSubscription() -> {
                    playServiceManager.startBillingFlow(activityContext)
                    return
                }
            }
        }

        val preferredServiceType = appListViewModel.getAppServiceTypePreference(selectedApp).toLowerCase()

        if (activeSessions.isNotEmpty()) {
            if (activeSessions.any { it.name == selectedApp.name && it.serviceType == preferredServiceType }) {
                val session = activeSessions.find { it.name == selectedApp.name && it.serviceType == preferredServiceType }
                val serviceIntent = Intent(activityContext, ServerService::class.java)
                        .putExtra("type", "restartRunningSession")
                        .putExtra("session", session)
                activityContext.startService(serviceIntent)
                return
            } else {
                Toast.makeText(activityContext, R.string.single_session_supported, Toast.LENGTH_LONG)
                        .show()
                return
            }
        }

        var filesystemExtracted = false
        val possibleAppFilesystem = arrayListOf<Filesystem>()

        for (filesystem in filesystemList) {
            if (filesystem.distributionType == selectedApp.filesystemRequired) {
                if (filesystem.isAppsFilesystem) {
                    possibleAppFilesystem.add(filesystem)
                    filesystemExtracted = filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
                }
            }
        }

        if (!filesystemExtracted) {
            getCredentials(selectedApp = selectedApp)
            return
        }

        if (possibleAppFilesystem.isEmpty()) {
            // TODO some error notification
            return
        }
        val appFilesystem = possibleAppFilesystem.first()
        if (appListViewModel.getAppServiceTypePreference(selectedApp).isEmpty()) {
            getClientPreferenceAndStart(selectedApp, appFilesystem.defaultUsername, appFilesystem.defaultPassword, appFilesystem.defaultVncPassword)
            return
        }

        val startAppIntent = Intent(activityContext, ServerService::class.java)
                .putExtra("type", "startApp")
                .putExtra("app", selectedApp)
                .putExtra("serviceType", preferredServiceType)
        activityContext.startService(startAppIntent)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activityContext.menuInflater.inflate(R.menu.context_menu_apps, menu)
    }

//    override fun onContextItemSelected(item: MenuItem): Boolean {
//        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
//        val position = menuInfo.position
//        val selectedItem = list_apps.adapter.getItem(position) as AppsListItem
//        return when (selectedItem) {
//            is AppSeparatorItem -> true
//            is AppItem -> {
//                val app = selectedItem.app
//                doContextItemSelected(app, item)
//            }
//        }
//    }

//    private fun doContextItemSelected(app: App, item: MenuItem): Boolean {
//        return when (item.itemId) {
//            R.id.menu_item_app_details -> showAppDetails(app)
//            R.id.menu_item_stop_app -> stopApp(app)
//            else -> super.onContextItemSelected(item)
//        }
//    }

    private fun showAppDetails(app: App): Boolean {
        val bundle = bundleOf("app" to app)
        NavHostFragment.findNavController(this).navigate(R.id.menu_item_app_details, bundle)
        return true
    }

    private fun stopApp(app: App): Boolean {
        val serviceIntent = Intent(activityContext, ServerService::class.java)
                .putExtra("type", "stopApp")
                .putExtra("app", app)
        activityContext.startService(serviceIntent)
        return true
    }

    private fun getCredentials(selectedApp: App) {
        val dialog = AlertDialog.Builder(activityContext)
        val dialogView = activityContext.layoutInflater.inflate(R.layout.dia_app_credentials, null)
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

                    if (appSupportsOneServiceTypeAndSetPref(selectedApp)) {
                        val serviceTypePreference = appListViewModel.getAppServiceTypePreference(selectedApp)
                        val serviceIntent = Intent(activityContext, ServerService::class.java)
                                .putExtra("type", "startApp")
                                .putExtra("username", username)
                                .putExtra("password", password)
                                .putExtra("vncPassword", vncPassword)
                                .putExtra("app", selectedApp)
                                .putExtra("serviceType", serviceTypePreference.toLowerCase())

                        activityContext.startService(serviceIntent)
                        return@setOnClickListener
                    }

                    if (appListViewModel.getAppServiceTypePreference(selectedApp).isEmpty()) {
                        getClientPreferenceAndStart(selectedApp, username, password, vncPassword)
                    }

                    val serviceTypePreference = appListViewModel.getAppServiceTypePreference(selectedApp)
                    val serviceIntent = Intent(activityContext, ServerService::class.java)
                            .putExtra("type", "startApp")
                            .putExtra("username", username)
                            .putExtra("password", password)
                            .putExtra("vncPassword", vncPassword)
                            .putExtra("app", selectedApp)
                            .putExtra("serviceType", serviceTypePreference.toLowerCase())

                    activityContext.startService(serviceIntent)
                }
            }
        }
        customDialog.show()
    }

    private fun appSupportsOneServiceTypeAndSetPref(app: App): Boolean {
        when {
            app.supportsGui && app.supportsCli -> return false
            app.supportsCli -> appListViewModel.setAppServiceTypePreference(app, AppsPreferences.SSH)
            app.supportsGui -> appListViewModel.setAppServiceTypePreference(app, AppsPreferences.VNC)
        }
        return true
    }

    private fun getClientPreferenceAndStart(app: App, username: String, password: String, vncPassword: String) {
        val dialog = AlertDialog.Builder(activityContext)
        val dialogView = layoutInflater.inflate(R.layout.dia_app_select_client, null)
        dialog.setView(dialogView)
        dialog.setCancelable(true)
        dialog.setPositiveButton(R.string.button_continue, null)
        val customDialog = dialog.create()

        customDialog.setOnShowListener { _ ->
            customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { _ ->
                customDialog.dismiss()
                val sshTypePreference = customDialog.find<RadioButton>(R.id.ssh_radio_button)
                if (sshTypePreference.isChecked) {
                    appListViewModel.setAppServiceTypePreference(app, AppsPreferences.SSH)
                } else {
                    appListViewModel.setAppServiceTypePreference(app, AppsPreferences.VNC)
                }

                val serviceTypePreference = appListViewModel.getAppServiceTypePreference(app)
                val serviceIntent = Intent(activityContext, ServerService::class.java)
                        .putExtra("type", "startApp")
                        .putExtra("username", username)
                        .putExtra("password", password)
                        .putExtra("vncPassword", vncPassword)
                        .putExtra("app", app)
                        .putExtra("serviceType", serviceTypePreference.toLowerCase())

                activityContext.startService(serviceIntent)
            }
        }
        customDialog.show()
    }

    private fun validateCredentials(username: String, password: String, vncPassword: String): Boolean {
        val validator = ValidationUtility()
        var allCredentialsAreValid = false

        when {
            username.isEmpty() || password.isEmpty() || vncPassword.isEmpty() -> {
                Toast.makeText(activityContext, R.string.error_empty_field, Toast.LENGTH_LONG).show()
            }
            vncPassword.length > 8 || vncPassword.length < 6 -> {
                Toast.makeText(activityContext, R.string.error_vnc_password_length_incorrect, Toast.LENGTH_LONG).show()
            }
            !validator.isUsernameValid(username) -> {
                Toast.makeText(activityContext, R.string.error_username_invalid, Toast.LENGTH_LONG).show()
            }
            !validator.isPasswordValid(password) -> {
                Toast.makeText(activityContext, R.string.error_password_invalid, Toast.LENGTH_LONG).show()
            }
            !validator.isPasswordValid(vncPassword) -> {
                Toast.makeText(activityContext, R.string.error_vnc_password_invalid, Toast.LENGTH_LONG).show()
            }
            else -> {
                allCredentialsAreValid = true
                return allCredentialsAreValid
            }
        }
        return allCredentialsAreValid
    }

    private fun showPermissionsNecessaryDialog() {
        val builder = AlertDialog.Builder(activityContext)
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
                    handleAppSelection(lastSelectedApp)
                } else {
                    showPermissionsNecessaryDialog()
                }
            }
        }
    }

    override fun onSubscriptionsAreNotSupported() {
        AlertDialog.Builder(activityContext)
                .setMessage(R.string.alert_subscriptions_unsupported_message)
                .setTitle(R.string.general_error_title)
                .setPositiveButton(R.string.button_ok) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create().show()
    }

    override fun onBillingClientConnectionChange(isConnected: Boolean) {
        billingClientIsConnected = isConnected
        if (!isConnected) playServiceManager.startBillingClient(activityContext)
    }

    override fun onPlayServiceError() {
        AlertDialog.Builder(activityContext)
                .setMessage(R.string.alert_play_service_error_message)
                .setTitle(R.string.general_error_title)
                .setPositiveButton(R.string.button_ok) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create().show()
    }

    override fun onSubscriptionPurchased() {
        if (lastSelectedApp != unselectedApp) handleAppSelection(lastSelectedApp)
    }
}