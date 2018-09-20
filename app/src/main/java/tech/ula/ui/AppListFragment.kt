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
import tech.ula.utils.AppsListPreferences
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
        AppsListPreferences(activityContext.getSharedPreferences("appLists", Context.MODE_PRIVATE))
    }

    private val appListViewModel: AppListViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(activityContext)
        val sessionDao = ulaDatabase.sessionDao()
        val appsDao = ulaDatabase.appsDao()
        val githubFetcher = GithubAppsFetcher("${activityContext.filesDir}")

        val appsRepository = AppsRepository(appsDao, githubFetcher, appListPreferences)
        ViewModelProviders.of(this, AppListViewModelFactory(appsRepository, sessionDao)).get(AppListViewModel::class.java)
    }

    private val appChangeObserver = Observer<List<App>> {
        it?.let {
            appList = it
            appAdapter = AppListAdapter(activityContext, appList)
            list_apps.adapter = appAdapter
            setPulldownPromptVisibilityForAppList()
        }
    }

    private val activeSessionsChangeObserver = Observer<List<Session>> {
        it?.let {
            activeSessions = it
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
        appListViewModel.getAllApps().observe(viewLifecycleOwner, appChangeObserver)
        appListViewModel.getAllActiveSessions().observe(viewLifecycleOwner, activeSessionsChangeObserver)
        registerForContextMenu(list_apps)
        list_apps.onItemClickListener = AdapterView.OnItemClickListener {
            _, _, position, _ ->
            if (arePermissionsGranted(activityContext)) {
                // TODO show description fragment if first time
                val selectedApp = appList[position]

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
                    return@OnItemClickListener
                }

                val preferredClient = appListViewModel.getAppClientPreference(appList[position])
                if (preferredClient.isEmpty()) {
                    select_client_preference(selectedApp)
                } else {
                    val serviceIntent = Intent(activityContext, ServerService::class.java)
                            .putExtra("type", "startApp")
                            .putExtra("app", selectedApp)
                            .putExtra("serviceType", preferredClient.toLowerCase())

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

    private fun setPulldownPromptVisibilityForAppList() {
        empty_apps_list.visibility = when (appList.isEmpty()) {
            true -> View.VISIBLE
            false -> View.INVISIBLE
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activityContext.menuInflater.inflate(R.menu.context_menu_app_description, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val app = appList[position]

        return when (item.itemId) {
            R.id.menu_item_app_details -> showAppDetails(app)
            else -> super.onContextItemSelected(item)
        }
    }

    private fun showAppDetails(app: App): Boolean {
        val bundle = bundleOf("app" to app)
        NavHostFragment.findNavController(this).navigate(R.id.menu_item_app_details, bundle)
        return true
    }

    private fun passDataToActivity(data: String) {
        dataPasser.onFragmentDataPassed(data)
    }

    private fun select_client_preference(selectedApp: App) {
        lateinit var dialog: AlertDialog

        val clients = arrayOf("SSH", "VNC")
        var preferredClient = "SSH"

        val builder = AlertDialog.Builder(activityContext)
        builder.setTitle("Always open with:")
        builder.setSingleChoiceItems(clients, 0) { _, selected ->
            val choice = clients[selected]
            preferredClient = choice
        }

        builder.setPositiveButton("Continue") { _, _ ->
            appListViewModel.setAppClientPreference(selectedApp, preferredClient)
            Toast.makeText(activityContext, "$preferredClient selected", Toast.LENGTH_SHORT).show()
        }

        dialog = builder.create()
        dialog.show()
    }
}