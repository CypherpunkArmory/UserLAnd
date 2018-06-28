package tech.ula.ui

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.*
import android.widget.AdapterView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_session_list.*
import kotlinx.android.synthetic.main.list_item_session.view.*
import org.jetbrains.anko.bundleOf
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.Session
import tech.ula.utils.ServerUtility
import tech.ula.viewmodel.SessionListViewModel

class SessionListFragment : Fragment() {

    private val permissionRequestCode = 1000

    private lateinit var sessionList: List<Session>
    private lateinit var sessionAdapter: SessionListAdapter

    private lateinit var activityContext: Activity

    private var activeSessions = false

    private val sessionListViewModel: SessionListViewModel by lazy {
        ViewModelProviders.of(this).get(SessionListViewModel::class.java)
    }

    private val sessionChangeObserver = Observer<List<Session>> {
        it?.let {
            sessionList = it

            // TODO
//            for(session in sessionList) {
//                if(session.active) session.active = serverUtility.isServerRunning(session)
//            }

            activeSessions = sessionList.any { it.active }

            sessionAdapter = SessionListAdapter(activityContext, sessionList)
            list_sessions.adapter = sessionAdapter
        }
    }

    private val serverUtility by lazy {
        ServerUtility(activityContext)
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
        return if(item.itemId == R.id.menu_item_add) editSession(Session(0, filesystemId = 0))
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        sessionListViewModel.getAllSessions().observe(viewLifecycleOwner, sessionChangeObserver)
        return inflater.inflate(R.layout.frag_session_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!

        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            _, view, position, _ ->
            if(!arePermissionsGranted()) {
//                showPermissionsNecessaryDialog()
                Toast.makeText(activityContext, "no permissions", Toast.LENGTH_LONG).show()
                return@OnItemClickListener
            }

            val session = sessionList[position]
            if(!session.active) {
                if (!activeSessions) {
                    startSession(session, view)
                } else {
                    Toast.makeText(activityContext, R.string.single_session_supported, Toast.LENGTH_LONG).show()
                }
            }
            else {
                // TODO
                Toast.makeText(activityContext, "reconnect", Toast.LENGTH_LONG).show()
            }
        }

//        fab.setOnClickListener { view ->
//            val bundle = bundleOf("session" to Session(0, filesystemId = 0))
//            view.findNavController().navigate(R.id.session_edit_fragment, bundle)
//        }
    }

    private fun arePermissionsGranted(): Boolean {
        return (ContextCompat.checkSelfPermission(activityContext,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

                ContextCompat.checkSelfPermission(activityContext,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    private fun startSession(session: Session, view: View) {
        // TODO
        session.active = true
        sessionListViewModel.updateSession(session)
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activityContext.menuInflater.inflate(R.menu.context_menu_sessions, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val session = sessionList[position]
        return when(item.itemId) {
            R.id.menu_item_session_kill_service -> stopService(session)
            R.id.menu_item_session_edit -> editSession(session)
            R.id.menu_item_session_delete -> deleteSession(session)
            else -> super.onContextItemSelected(item)
        }
    }

    private fun stopService(session: Session): Boolean {
        if(session.active) {
            session.active = false
            sessionListViewModel.updateSession(session)
            val view = list_sessions.getChildAt(sessionList.indexOf(session))
            view.image_list_item_active.setImageResource(R.drawable.ic_block_red_24dp)

            serverUtility.stopService(session)

            val serviceIntent = Intent(activityContext, ServerService::class.java)
            serviceIntent.putExtra("type", "kill")
            serviceIntent.putExtra("pid", session.pid)

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
}