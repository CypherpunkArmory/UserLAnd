package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_file_system_management.*
import tech.userland.userland.database.models.Filesystem
import tech.userland.userland.database.repositories.FilesystemRepository

class FileSystemManagementActivity: AppCompatActivity() {

    lateinit var filesystemList: ArrayList<Filesystem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_system_management)
        setSupportActionBar(toolbar)

        filesystemList = FilesystemRepository(this).getAllFilesystems()
        val filesystemNames = filesystemList.map { filesystem -> filesystem.name }

        list_file_system_management.adapter = ArrayAdapter(this, R.layout.list_item, filesystemNames)
        registerForContextMenu(list_file_system_management)

        fab.setOnClickListener { navigateToFileSystemCreate() }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.context_menu_file_systems, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_file_system_edit -> navigateToFileSystemCreate()
            R.id.menu_item_file_system_delete -> deleteFileSystem(item)
            else -> super.onContextItemSelected(item)
        }
    }

    fun navigateToFileSystemCreate(): Boolean {
        val intent = Intent(this, FileSystemCreateActivity::class.java)
        startActivity(intent)
        return true
    }

    fun deleteFileSystem(item: MenuItem): Boolean {
        // TODO
        return true
    }
}