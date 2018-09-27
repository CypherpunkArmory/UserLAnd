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
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.AdapterView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_app_list.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.bundleOf
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.model.remote.GithubAppsFetcher
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.AppsPreferences
import tech.ula.utils.arePermissionsGranted
import tech.ula.utils.asyncAwait
import tech.ula.viewmodel.AppListViewModel
import tech.ula.viewmodel.AppListViewModelFactory

class AppListFragment : Fragment() {

    private lateinit var activityContext: Activity
    private val permissionRequestCode: Int by lazy {
        activityContext.resources.getString(R.string.permission_request_code).toInt()
    }

    private lateinit var appList: List<App>
    private lateinit var appAdapter: AppListAdapter

    private lateinit var activeSessions: List<Session>

    private lateinit var lastSelectedApp: App

    private var refreshStatus = RefreshStatus.INACTIVE

    private val appListPreferences by lazy {
        AppsPreferences(activityContext.getSharedPreferences("appLists", Context.MODE_PRIVATE))
    }

    private val appListViewModel: AppListViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(activityContext)
        val sessionDao = ulaDatabase.sessionDao()
        val appsDao = ulaDatabase.appsDao()
        val githubFetcher = GithubAppsFetcher("${activityContext.filesDir}")

        val appsRepository = AppsRepository(appsDao, githubFetcher, appListPreferences)
        ViewModelProviders.of(this, AppListViewModelFactory(appsRepository, sessionDao)).get(AppListViewModel::class.java)
    }

    private val appsAndActiveSessionObserver = Observer<Pair<List<App>, List<Session>>> {
        it?.let {
            appList = it.first
            activeSessions = it.second
            appAdapter = AppListAdapter(activityContext, appList, activeSessions)
            list_apps.adapter = appAdapter
            setPulldownPromptVisibilityForAppList()
        }
    }

    private val refreshStatusObserver = Observer<RefreshStatus> {
        it?.let {
            refreshStatus = it
            swipe_refresh.isRefreshing = refreshStatus == RefreshStatus.ACTIVE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
        appListViewModel.getAppsAndActiveSessions().observe(viewLifecycleOwner, appsAndActiveSessionObserver)
        appListViewModel.getRefreshStatus().observe(viewLifecycleOwner, refreshStatusObserver)

        registerForContextMenu(list_apps)
        list_apps.onItemClickListener = AdapterView.OnItemClickListener {
            parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position) as AppsListItem
            when (selectedItem) {
                is AppSeparatorItem -> return@OnItemClickListener
                is AppItem -> {
                    val selectedApp = selectedItem.app
                    doAppItemClicked(selectedApp)
                }
            }
        }

        swipe_refresh.setOnRefreshListener {
                    doRefresh()
                }
    }

    private fun doRefresh() {
        appListViewModel.refreshAppsList()
        setPulldownPromptVisibilityForAppList()
    }

    private fun doAppItemClicked(selectedApp: App) {
        lastSelectedApp = selectedApp
        if (arePermissionsGranted(activityContext)) {
            handleAppSelection(lastSelectedApp)
        } else {
            showPermissionsNecessaryDialog()
        }
    }

    private fun handleAppSelection(app: App) {
        if (activeSessions.isNotEmpty()) {
            if (activeSessions.any { it.name == app.name }) {
                val session = activeSessions.find { it.name == app.name }
                val serviceIntent = Intent(activityContext, ServerService::class.java)
                        .putExtra("type", "restartRunningSession")
                        .putExtra("session", session)
                activityContext.startService(serviceIntent)
            } else {
                Toast.makeText(activityContext, R.string.single_session_supported, Toast.LENGTH_LONG)
                        .show()
            }
            return
        }

        val preferredServiceType = appListViewModel.getAppServiceTypePreference(app)
        if (preferredServiceType.isEmpty()) {
            selectServiceTypePreference(app)
        } else {
            val serviceIntent = Intent(activityContext, ServerService::class.java)
                    .putExtra("type", "startApp")
                    .putExtra("app", app)
                    .putExtra("serviceType", preferredServiceType.toLowerCase())

            activityContext.startService(serviceIntent)
        }
    }

    private fun setPulldownPromptVisibilityForAppList() {
        empty_apps_list.visibility = when (appList.isEmpty()) {
            true -> View.VISIBLE
            false -> View.INVISIBLE
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activityContext.menuInflater.inflate(R.menu.context_menu_apps, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val selectedItem = list_apps.adapter.getItem(position) as AppsListItem
        return when (selectedItem) {
            is AppSeparatorItem -> true
            is AppItem -> {
                val app = selectedItem.app
                doContextItemSelected(app, item)
            }
        }
    }

    private fun doContextItemSelected(app: App, item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_app_details -> showAppDetails(app)
            R.id.menu_item_stop_app -> stopApp(app)
            else -> super.onContextItemSelected(item)
        }
    }

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

    private fun selectServiceTypePreference(selectedApp: App) {
        val serviceTypes = arrayOf(AppsPreferences.SSH, AppsPreferences.VNC)
        var preferredServiceType = AppsPreferences.SSH

        val builder = AlertDialog.Builder(activityContext)
                .setTitle("Always open with:")
                .setSingleChoiceItems(serviceTypes, 0) { _, selected ->
                    preferredServiceType = serviceTypes[selected]
                }
                .setPositiveButton(R.string.button_continue) { _, _ ->
                    appListViewModel.setAppServiceTypePreference(selectedApp, preferredServiceType)
                    handleAppSelection(selectedApp)
                }

        builder.create().show()
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
}