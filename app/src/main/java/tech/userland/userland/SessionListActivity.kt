package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_session_list.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import org.jetbrains.anko.toast
import tech.userland.userland.database.models.Session
import tech.userland.userland.database.repositories.SessionRepository
import tech.userland.userland.ui.SessionListAdapter
import java.io.File

class SessionListActivity : AppCompatActivity() {

    lateinit var sessionList: ArrayList<Session>
    lateinit var sessionNameList: ArrayList<String>
    lateinit var sessionAdapter: SessionListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_list)
        setSupportActionBar(toolbar)

        sessionList = SessionRepository(this).getAllSessions()
        sessionNameList = ArrayList(sessionList.map { session -> session.name })
        sessionAdapter = SessionListAdapter(this, sessionList)

        list_sessions.adapter = sessionAdapter
        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            parent, view, position, id ->
            val session = sessionList[position]
            if(!session.active == true) {
                session.active = true
                SessionRepository(this).updateSessionActive(session)
                sessionInstallAndStartStub()
            }
        }

        fab.setOnClickListener { navigateToSessionEdit(null) }

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
        session.active = false
        SessionRepository(this).updateSessionActive(session)
        return true
    }

    fun deleteSession(session: Session): Boolean {
        SessionRepository(this).deleteSessionByName(session.name)
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
        intent.putExtra("sessionName", session?.name ?: "")
        intent.putExtra("filesystemName", session?.filesystemName ?: "")
        intent.putExtra("username", session?.username ?: "")
        intent.putExtra("password", session?.password ?: "")
        startActivity(intent)
        return true
    }

    private fun sessionInstallAndStartStub() {
        // https://medium.com/@andrea.bresolin/playing-with-kotlin-in-android-coroutines-and-how-to-get-rid-of-the-callback-hell-a96e817c108b
        launch(UI) {
            text_session_list_progress_update.text = "Downloading required assets..."
            async(CommonPool) {
                delay(2000)
            }.await()
            progress_bar_session_list.progress = 25

            text_session_list_progress_update.text = "Setting up file system..."
            async(CommonPool) {
                delay(2000)
            }.await()
            progress_bar_session_list.progress = 50

            text_session_list_progress_update.text = "Starting service..."
            async(CommonPool) {
                delay(2000)
            }.await()
            progress_bar_session_list.progress = 75

            text_session_list_progress_update.text = "Connecting to service..."
            async(CommonPool) {
                delay(2000)
            }.await()
            progress_bar_session_list.progress = 100

            text_session_list_progress_update.text = "Session active!"
        }
    }

    private fun fileCreationStub() {
        val installDir = File(packageManager.getApplicationInfo("tech.userland.userland", 0).dataDir)
        val testFile = File(installDir.absolutePath + "/coroutine")
        launch(UI) {
            launch {
                delay(10000)
                Runtime.getRuntime().exec(arrayOf("touch", "coroutine"), arrayOf<String>(), installDir)
            }
            while(!testFile.exists()) {
                async(CommonPool) {
                    delay(100)
                }.await()
            }
            toast("File created")
        }
    }
}
