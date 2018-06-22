package tech.ula.ui

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.navigation.findNavController
import kotlinx.android.synthetic.main.frag_session_list.*
import tech.ula.R
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_session_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!

        sessionListViewModel.getAllSessions().observe(this, sessionChangeObserver)

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

        fab.setOnClickListener { view ->
            val bundle = Bundle()
            bundle.putParcelable("session", Session(0, filesystemId = 0))
            view.findNavController().navigate(R.id.sessionEditFragment, bundle)
        }
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
}