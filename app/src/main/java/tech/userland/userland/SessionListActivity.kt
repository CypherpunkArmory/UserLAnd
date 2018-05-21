package tech.userland.userland

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_session_list.*
import kotlinx.android.synthetic.main.list_item_session.view.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.anko.toast
import tech.userland.userland.database.models.Session
import tech.userland.userland.ui.SessionListAdapter
import tech.userland.userland.ui.SessionViewModel
import tech.userland.userland.utils.*
import java.io.File

class SessionListActivity : AppCompatActivity() {

    lateinit var sessionList: List<Session>
    lateinit var sessionAdapter: SessionListAdapter

    private val sessionViewModel: SessionViewModel by lazy {
        ViewModelProviders.of(this).get(SessionViewModel::class.java)
    }

    private val sessionChangeObserver = Observer<List<Session>> {
        it?.let {
            sessionList = it
            sessionAdapter = SessionListAdapter(this, ArrayList(sessionList))
            list_sessions.adapter = sessionAdapter
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_list)
        setSupportActionBar(toolbar)

        sessionViewModel.getAllSessions().observe(this, sessionChangeObserver)

        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            parent, view, position, id ->
            val session = sessionList[position]
            if(!session.active == true) {
                session.active = true
                sessionViewModel.updateSession(session)
                sessionInstallAndStartStub()
            }
        }

        fab.setOnClickListener { navigateToSessionEdit(Session(0, filesystemId = 0)) }

        progress_bar_session_list.visibility = View.VISIBLE
        fileCreationStub()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_file_system_management -> navigateToFilesystemManagement()
            R.id.menu_item_settings -> navigateToSettings()
            R.id.menu_item_help -> navigateToHelp()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.context_menu_sessions, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val session = sessionList[position]
        return when(item.itemId) {
            R.id.menu_item_session_disconnect -> disconnectSession(session)
            R.id.menu_item_session_edit -> navigateToSessionEdit(session)
            R.id.menu_item_session_delete -> deleteSession(session)
            else -> super.onContextItemSelected(item)
        }
    }

    fun disconnectSession(session: Session): Boolean {
        if(session.active) {
            session.active = false
            sessionViewModel.updateSession(session)
            val view = list_sessions.getChildAt(sessionList.indexOf(session))
            view.image_list_item_active.setImageResource(R.drawable.ic_block_white_24dp)
        }
        return true
    }

    fun deleteSession(session: Session): Boolean {
        sessionViewModel.deleteSessionById(session.id)
        return true
    }

    private fun navigateToFilesystemManagement(): Boolean {
        val intent = Intent(this, FilesystemListActivity::class.java)
        startActivity(intent)
        return true
    }

    private fun navigateToSettings(): Boolean {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        return true
    }

    private fun navigateToHelp(): Boolean {
        val intent = Intent(this, HelpActivity::class.java)
        startActivity(intent)
        return true
    }

    private fun navigateToSessionEdit(session: Session?): Boolean {
        val intent = Intent(this, SessionEditActivity::class.java)
        session?.let { intent.putExtra("session", session) }
        startActivity(intent)
        return true
    }

    private fun sessionInstallAndStartStub() {
        launchAsync {
            progress_bar_session_list.progress = 0
            text_session_list_progress_update.text = "Downloading required assets..."
            asyncAwait { delay(2000) }
            progress_bar_session_list.progress = 25

            text_session_list_progress_update.text = "Setting up file system..."
            asyncAwait { delay(2000) }
            progress_bar_session_list.progress = 50

            text_session_list_progress_update.text = "Starting service..."
            asyncAwait { delay(2000) }
            progress_bar_session_list.progress = 75

            text_session_list_progress_update.text = "Connecting to service..."
            asyncAwait { delay(2000) }
            progress_bar_session_list.progress = 100

            text_session_list_progress_update.text = "Session active!"
        }
    }

    private fun fileCreationStub() {
        val installDir = File(packageManager.getApplicationInfo("tech.userland.userland", 0).dataDir)
        val testFile = File(installDir.absolutePath + "/coroutine")
        launchAsync {
            launch {
                delay(10000)
                Runtime.getRuntime().exec(arrayOf("touch", "coroutine"), arrayOf<String>(), installDir)
            }
            while(!testFile.exists()) {
                asyncAwait { delay(100) }
            }
            toast("File created")
        }
    }
}
