package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_root_fs_list.*

class RootFsListActivity : AppCompatActivity() {

    val rootFsList: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_root_fs_list)
        setSupportActionBar(toolbar)

        genStubRootFsList()

        /*
        root_fs_list.layoutManager = LinearLayoutManager(this)
        root_fs_list.adapter = ListAdapter(rootFsList,this)
        registerForContextMenu(root_fs_list)
        */
        list_sessions.adapter = ArrayAdapter(this, R.layout.list_item, rootFsList)
        registerForContextMenu(list_sessions)

        fab.setOnClickListener { navigateToAddRootFs() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_settings -> navigateToSettings()
            R.id.menu_item_help -> navigateToHelp()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.context_menu_sessions, menu)
    }

    /*
    val onLongClickCallback: () -> Unit = {
        Toast.makeText(this, "Hello from rootfs", Toast.LENGTH_LONG).show()
    }
    */

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

    private fun navigateToAddRootFs(): Boolean {
        val intent = Intent(this, RootFsAddActivity::class.java)
        startActivity(intent)
        return true
    }

    fun genStubRootFsList() {
        rootFsList.add("Debian")
        rootFsList.add("Ubuntu")
        rootFsList.add("Debian with vnc server")
    }
}
