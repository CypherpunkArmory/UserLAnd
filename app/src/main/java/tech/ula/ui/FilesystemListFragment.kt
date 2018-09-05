package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.AdapterView
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_filesystem_list.* // ktlint-disable no-wildcard-imports
import org.jetbrains.anko.bundleOf
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.Filesystem
import tech.ula.viewmodel.FilesystemListViewModel
import android.net.Uri
import android.os.Environment
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import android.widget.Toast
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.defaultSharedPreferences
import tech.ula.utils.ExecUtility

class FilesystemListFragment : Fragment() {

    private lateinit var activityContext: Activity

    private lateinit var filesystemList: List<Filesystem>

    private val execUtility: ExecUtility by lazy {
        val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        ExecUtility(activityContext.filesDir.path, externalStoragePath, DefaultPreferences(activityContext.defaultSharedPreferences))
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(activityContext.filesDir.path, execUtility)
    }

    private val filesystemListViewModel: FilesystemListViewModel by lazy {
        ViewModelProviders.of(this).get(FilesystemListViewModel::class.java)
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let {
            filesystemList = it

            list_filesystems.adapter = FilesystemListAdapter(activityContext, filesystemList)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_create, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_item_add) editFilesystem(Filesystem(0))
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        filesystemListViewModel.getAllFilesystems().observe(this  ,filesystemChangeObserver)
        return inflater.inflate(R.layout.frag_filesystem_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!
        registerForContextMenu(list_filesystems)
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activityContext.menuInflater.inflate(R.menu.context_menu_filesystems, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = menuInfo.position
        val filesystem = filesystemList[position]
        return when (item.itemId) {
            R.id.menu_item_filesystem_edit -> editFilesystem(filesystem)
            R.id.menu_item_filesystem_backup -> backupFilesystem(filesystem)
            R.id.menu_item_filesystem_delete -> deleteFilesystem(filesystem)
            else -> super.onContextItemSelected(item)
        }
    }

    private fun editFilesystem(filesystem: Filesystem): Boolean {
        val editExisting = filesystem.name != ""
        val bundle = bundleOf("filesystem" to filesystem, "editExisting" to editExisting)
        NavHostFragment.findNavController(this).navigate(R.id.filesystem_edit_fragment, bundle)
        return true
    }

    private fun deleteFilesystem(filesystem: Filesystem): Boolean {
        filesystemListViewModel.deleteFilesystemById(filesystem.id)

        val serviceIntent = Intent(activityContext, ServerService::class.java)
        serviceIntent.putExtra("type", "filesystemIsBeingDeleted")
        serviceIntent.putExtra("filesystemId", filesystem.id)
        activityContext.startService(serviceIntent)

        return true
    }

    private fun backupFilesystem(filesystem: Filesystem): Boolean {
        launch { async {
            try {
                // TODO exec only if session is not running and block starting session if backup or restore is in progress
                // TODO progress notification
                val backupLocation = "${filesystem.id}.tar.gz"
                filesystemUtility.backupFilesystemByLocation("/support", "${filesystem.id}", "${backupLocation}", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            } catch (e: Exception) {
                Toast.makeText(activityContext, e.message, Toast.LENGTH_LONG).show()
            }
        }}
        return true
    }
}