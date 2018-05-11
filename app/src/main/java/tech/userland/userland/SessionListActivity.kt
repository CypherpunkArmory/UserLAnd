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
import tech.userland.userland.database.models.Session
import tech.userland.userland.database.repositories.SessionRepository
import tech.userland.userland.ui.SessionListAdapter

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

        list_sessions.emptyView = findViewById(R.id.empty)
        list_sessions.adapter = sessionAdapter
        registerForContextMenu(list_sessions)
        list_sessions.onItemClickListener = AdapterView.OnItemClickListener {
            parent, view, position, id ->
            val session = sessionList[position]
            if(!session.active == true) {
                session.active = true
                SessionRepository(this).updateSessionActive(session)
            }
        }

        fab.setOnClickListener { navigateToSessionEdit(null) }
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
        intent.putExtra("sessionName", session?.name)
        intent.putExtra("username", session?.username)
        intent.putExtra("password", session?.password)
        startActivity(intent)
        return true
    }

    /*CC: Just putting this code here as an example of how to run the various client connection intents
    fun fireBvncIntent() {
        val bvncIntent = Intent()
        bvncIntent.action = "android.intent.action.VIEW"
        bvncIntent.type = "application/vnd.vnc"
        bvncIntent.data = Uri.parse("vnc://127.0.0.1:5951/?" + PARAM_VNC_PWD + "=" + "bogusVncPassword")
        startActivity(bvncIntent)
    }

    fun fireConnectBotIntent() {
        val connectBotIntent = Intent()
        connectBotIntent.action = "android.intent.action.VIEW"
        connectBotIntent.data = Uri.parse("ssh://user@host:port/#nickname")
        startActivity(connectBotIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            try {
                Log.i("XSDL-Launcher", "Got return result data: " + data.getStringExtra("run"))
            } catch (e: Exception) {
                Log.i("XSDL-Launcher", "Got exception: " + e.toString())
            }
        }
    }

    fun startXSDL() {
        Log.i("XSDL-Launcher", "Launching XSDL")
        try {
            val i = Intent(Intent.ACTION_MAIN, Uri.parse("x11://give.me.display:111"))
            startActivityForResult(i, 1)
        } catch (e: Exception) {
            Log.i("XSDL-Launcher","XSDL not installed!")
            val appPackageName = "x.org.server"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
            } catch (anfe: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }

        }

    }
    */
}
