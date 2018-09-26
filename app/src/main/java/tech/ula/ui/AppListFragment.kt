package tech.ula.ui

import android.app.Activity
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.AdapterView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_app_list.*
import tech.ula.OnFragmentDataPassed
import org.jetbrains.anko.bundleOf
import org.jetbrains.anko.find
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
import tech.ula.viewmodel.AppListViewModel
import tech.ula.viewmodel.AppListViewModelFactory

class AppListFragment : Fragment() {

    private lateinit var activityContext: Activity
    private lateinit var dataPasser: OnFragmentDataPassed

    private lateinit var appList: List<App>
    private lateinit var appAdapter: AppListAdapter

    private lateinit var activeSessions: List<Session>

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

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        dataPasser = context as OnFragmentDataPassed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_create, menu)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
        appListViewModel.getAppsAndActiveSessions().observe(viewLifecycleOwner, appsAndActiveSessionObserver)
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
              
                val preferredServiceType = appListViewModel.getAppServiceTypePreference(appList[position])
                if (preferredServiceType.isEmpty()) {
                    selectServiceTypePreference(selectedApp)
                } else {
                    val serviceIntent = Intent(activityContext, ServerService::class.java)
                            .putExtra("type", "startApp")
                            .putExtra("app", selectedApp)
                            .putExtra("serviceType", preferredServiceType.toLowerCase())

                    activityContext.startService(serviceIntent)
                }
            } else {
                passDataToActivity("permissionsRequired")
            }
        }

        val swipeLayout = activityContext.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeLayout.setOnRefreshListener {
                    appListViewModel.refreshAppsList()
                    while (appListViewModel.getRefreshStatus() == RefreshStatus.ACTIVE) {
                        Thread.sleep(500)
                    }

                    setPulldownPromptVisibilityForAppList()
                    swipeLayout.isRefreshing = false
                }
    }

    private fun doAppItemClicked(selectedApp: App) {
        if (arePermissionsGranted(activityContext)) {
            // TODO show description fragment if first time
            if (activeSessions.isNotEmpty()) {
                if (activeSessions.any { it.name == selectedApp.name }) {
                    val session = activeSessions.find { it.name == selectedApp.name }
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

            val serviceIntent = Intent(activityContext, ServerService::class.java)
                    .putExtra("type", "startApp")
                    .putExtra("app", selectedApp)
                    .putExtra("serviceType", "ssh") // TODO update this dynamically based on user preference
            activityContext.startService(serviceIntent)
        } else {
            passDataToActivity("permissionsRequired")
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

    private fun passDataToActivity(data: String) {
        dataPasser.onFragmentDataPassed(data)
    }

    private fun selectServiceTypePreference(selectedApp: App) {
        lateinit var dialog: AlertDialog

        val serviceTypes = arrayOf("SSH", "VNC")
        var preferredServiceType = "SSH"

        val builder = AlertDialog.Builder(activityContext)
                .setTitle("Always open with:")
                .setSingleChoiceItems(serviceTypes, 0) { _, selected ->
                    preferredServiceType = serviceTypes[selected]
                }

        builder.setPositiveButton("Continue") { _, _ ->
            appListViewModel.setAppServiceTypePreference(selectedApp, preferredServiceType)
            Toast.makeText(activityContext, "$preferredServiceType selected", Toast.LENGTH_SHORT).show()
        }

        dialog = builder.create()
        dialog.show()
    }
}