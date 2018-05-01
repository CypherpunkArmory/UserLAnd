package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_root_fs_list.*

class RootFsListActivity : AppCompatActivity() {

    val rootFsList: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_root_fs_list)
        setSupportActionBar(toolbar)

        genStubRootFsList()

        root_fs_list.layoutManager = LinearLayoutManager(this)
        root_fs_list.adapter = ListAdapter(rootFsList, this)


        /*
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        */
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> navigateToSettings()
            R.id.action_help -> navigateToHelp()
            else -> super.onOptionsItemSelected(item)
        }
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

    fun genStubRootFsList() {
        rootFsList.add("Debian")
        rootFsList.add("Ubuntu")
        rootFsList.add("Debian with vnc server")
    }
}
