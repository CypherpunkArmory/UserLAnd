package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.AdapterView
import kotlinx.android.synthetic.main.frag_app_list.*
import tech.ula.OnFragmentDataPassed
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.App
import tech.ula.model.remote.GithubAppsFetcher
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.arePermissionsGranted
import tech.ula.viewmodel.AppListViewModel
import tech.ula.viewmodel.AppListViewModelFactory

class AppListFragment : Fragment() {

    private lateinit var activityContext: Activity
    private lateinit var dataPasser: OnFragmentDataPassed

    private lateinit var appList: List<App>
    private lateinit var appAdapter: AppListAdapter

    private val appListViewModel: AppListViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(activityContext)
        val appsRepository = AppsRepository(ulaDatabase.appsDao(), GithubAppsFetcher("${activityContext.filesDir}"))
        ViewModelProviders.of(this, AppListViewModelFactory(appsRepository)).get(AppListViewModel::class.java)
    }

    private val appChangeObserver = Observer<List<App>> {
        it?.let {
            appList = it
            appAdapter = AppListAdapter(activityContext, appList)
            list_apps.adapter = appAdapter
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!
        appListViewModel.getAllApps().observe(viewLifecycleOwner, appChangeObserver)

        registerForContextMenu(list_apps)
        list_apps.onItemClickListener = AdapterView.OnItemClickListener {
            _, _, position, _ ->
            if (arePermissionsGranted(activityContext)) {
                val selectedApp = appList[position]
                println("Clicked on APP: ${selectedApp.name}")
                val serviceIntent = Intent(activityContext, ServerService::class.java)
                        .putExtra("type", "startApp")
                        .putExtra("app", selectedApp)
                        .putExtra("serviceType", "ssh")
                activityContext.startService(serviceIntent)
            }
            else {
                passDataToActivity("permissionsRequired")
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val info = menuInfo as AdapterView.AdapterContextMenuInfo
        super.onCreateContextMenu(menu, v, menuInfo)
        val app = appList[info.position]
        when {
            app.isPaidApp ->
                activityContext.menuInflater.inflate(R.menu.context_menu_active_sessions, menu)
            else ->
                activityContext.menuInflater.inflate(R.menu.context_menu_inactive_sessions, menu)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val app = appList[position]

        super.onContextItemSelected(item)
        return true
    }

    private fun passDataToActivity(data: String) {
        dataPasser.onFragmentDataPassed(data)
    }
}
