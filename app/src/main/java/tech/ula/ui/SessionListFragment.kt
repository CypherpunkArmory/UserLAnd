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
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_session_list.* // ktlint-disable no-wildcard-imports
import kotlinx.android.synthetic.main.list_item_session.view.* // ktlint-disable no-wildcard-imports
import org.jetbrains.anko.bundleOf
import tech.ula.OnFragmentDataPassed
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.arePermissionsGranted
import tech.ula.viewmodel.SessionListViewModel

class SessionListFragment : Fragment() {

    private lateinit var activityContext: Activity
    private lateinit var dataPasser: OnFragmentDataPassed

    private lateinit var sessionList: List<Session>
    private lateinit var sessionAdapter: SessionListAdapter
    private lateinit var filesystemList: List<Filesystem>

    private lateinit var lastSelectedSession: Session

    private val sessionListViewModel: SessionListViewModel by lazy {
        ViewModelProviders.of(this).get(SessionListViewModel::class.java)
    }

    private val sessionsAndFilesystemsChangeObserver = Observer<Pair<List<Session>, List<Filesystem>>> {
        it?.let {
            sessionList = it.first
            filesystemList = it.second

            sessionAdapter = SessionListAdapter(activityContext, sessionList, filesystemList)
            list_sessions.adapter = sessionAdapter
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
        return if (item.itemId == R.id.menu_item_add) editSession(Session(0, filesystemId = 0))
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_session_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        sessionListViewModel.getSessionsAndFilesystems().observe(viewLifecycleOwner, sessionsAndFilesystemsChangeObserver)

        activityContext = activity!!

        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position) as SessionListItem
            when (selectedItem) {
                is SessionSeparatorItem -> return@OnItemClickListener
                is SessionItem -> {
                    val session = selectedItem.session
                    doSessionItemClicked(session)
                }
            }
        }
    }

    private fun doSessionItemClicked(session: Session) {
        lastSelectedSession = session

        if (arePermissionsGranted(activityContext)) {
            handleSessionSelection(lastSelectedSession)
        } else {
            passDataToActivity("permissionsRequired")
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

    private fun passDataToActivity(data: String) {
        dataPasser.onFragmentDataPassed(data)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val info = menuInfo as AdapterView.AdapterContextMenuInfo
        val position = info.position
        val selectedItem = list_sessions.adapter.getItem(position) as SessionListItem
        when (selectedItem) {
            is SessionSeparatorItem -> return
            is SessionItem -> {
                val session = selectedItem.session
                doCreateSessionContextMenu(session, menu)
            }
        }
    }

    private fun doCreateSessionContextMenu(session: Session, menu: ContextMenu) {
        when {
            session.active ->
                activityContext.menuInflater.inflate(R.menu.context_menu_active_sessions, menu)
            else ->
                activityContext.menuInflater.inflate(R.menu.context_menu_inactive_sessions, menu)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val selectedItem = list_sessions.adapter.getItem(position) as SessionListItem
        return when (selectedItem) {
            is SessionSeparatorItem -> true
            is SessionItem -> {
                val session = selectedItem.session
                doSessionContextItemSelected(session, item)
            }
        }
    }

    private fun doSessionContextItemSelected(session: Session, item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_session_kill_service -> stopService(session)
            R.id.menu_item_session_edit -> editSession(session)
            R.id.menu_item_session_delete -> deleteSession(session)
            else -> super.onContextItemSelected(item)
        }
    }

    private fun stopService(session: Session): Boolean {
        if (session.active) {
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
}