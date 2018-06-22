package tech.ula.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.frag_session_list.*
import tech.ula.R
import tech.ula.model.entities.Session
import tech.ula.utils.ServerUtility
import tech.ula.viewmodel.SessionListViewModel

class SessionListFragment : Fragment() {

    private lateinit var sessionList: List<Session>
    private lateinit var sessionAdapter: SessionListAdapter

    private var activeSessions = false

    private val sessionListViewModel: SessionListViewModel by lazy {
        ViewModelProviders.of(this).get(SessionListViewModel::class.java)
    }

    private val sessionChangeObserver = Observer<List<Session>> {
        it?.let {
            sessionList = it

            for(session in sessionList) {
                if(session.active) session.active = serverUtility.isServerRunning(session)
            }

            activeSessions = sessionList.any { it.active }

            sessionAdapter = SessionListAdapter(activity!!, sessionList)
            list_sessions.adapter = sessionAdapter
        }
    }

    private val serverUtility by lazy {
        ServerUtility(activity!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_session_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        sessionListViewModel.getAllSessions().observe(this, sessionChangeObserver)
    }
}