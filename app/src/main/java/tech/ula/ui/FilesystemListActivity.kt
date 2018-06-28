package tech.ula.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import kotlinx.android.synthetic.main.frag_filesystem_list.*
import org.jetbrains.anko.toast
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.viewmodel.FilesystemListViewModel
import tech.ula.utils.*


class FilesystemListActivity: AppCompatActivity() {

    private lateinit var filesystemList: List<Filesystem>

    private val fileSystemListViewModel: FilesystemListViewModel by lazy {
        ViewModelProviders.of(this).get(FilesystemListViewModel::class.java)
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let {
            filesystemList = it

            list_file_system_management.adapter = FilesystemListAdapter(this, filesystemList)
        }
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.frag_filesystem_list)

        fileSystemListViewModel.getAllFilesystems().observe(this, filesystemChangeObserver)

        registerForContextMenu(list_file_system_management)

    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.context_menu_file_systems, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val filesystem = filesystemList[position]

        return when (item.itemId) {
            R.id.menu_item_file_system_edit -> navigateToFilesystemEdit(filesystem)
            R.id.menu_item_file_system_delete -> deleteFilesystem(filesystem)
            else -> super.onContextItemSelected(item)
        }
    }

    fun navigateToFilesystemEdit(filesystem: Filesystem): Boolean {
        val intent = Intent(this, FilesystemEditActivity::class.java)
        intent.putExtra("filesystem", filesystem)
        intent.putExtra("editExisting", filesystem.name != "")
        startActivity(intent)
        return true
    }

    fun deleteFilesystem(filesystem: Filesystem): Boolean {
        fileSystemListViewModel.deleteFilesystemById(filesystem.id)
        val success = filesystemUtility.deleteFilesystem(filesystem.id.toString())
        if(!success) {
            toast(R.string.filesystem_delete_failure)
        }
        return true
    }
}