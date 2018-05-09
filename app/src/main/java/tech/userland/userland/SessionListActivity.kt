package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_session_list.*
import tech.userland.userland.database.models.Session
import tech.userland.userland.database.repositories.SessionRepository

class SessionListActivity : AppCompatActivity() {

    lateinit var sessionList: ArrayList<Session>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_list)
        setSupportActionBar(toolbar)

        sessionList = ArrayList(SessionRepository(this).getAllSessions())
        Toast.makeText(this, "Session list created with " + sessionList.toString(), Toast.LENGTH_LONG).show()

        list_sessions.emptyView = findViewById(R.id.empty)
        list_sessions.adapter = ArrayAdapter(this, R.layout.list_item, sessionList)
        registerForContextMenu(list_sessions)

        fab.setOnClickListener { navigateToSessionEdit() }
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
        return when(item.itemId) {
            R.id.menu_item_session_disconnect -> disconnectSession(item)
            R.id.menu_item_session_edit -> navigateToSessionEdit()
            R.id.menu_item_session_delete -> deleteSession(item)
            else -> super.onContextItemSelected(item)
        }
    }

    fun disconnectSession(item: MenuItem): Boolean {
        // TODO
        return true
    }

    fun deleteSession(item: MenuItem): Boolean {
        // TODO
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

    private fun navigateToSessionEdit(): Boolean {
        val intent = Intent(this, SessionEditActivity::class.java)
        startActivity(intent)
        return true
    }
}
