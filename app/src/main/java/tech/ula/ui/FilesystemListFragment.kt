package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.Fragment
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.AdapterView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_filesystem_list.* // ktlint-disable no-wildcard-imports
import org.jetbrains.anko.bundleOf
import org.jetbrains.anko.defaultSharedPreferences
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.Filesystem
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.BusyboxExecutor
import tech.ula.utils.DefaultPreferences
import tech.ula.utils.FilesystemUtility
import tech.ula.viewmodel.ExportSuccess
import tech.ula.viewmodel.ExportFailure
import tech.ula.viewmodel.FilesystemExportStatus
import tech.ula.viewmodel.FilesystemListViewModel
import tech.ula.viewmodel.FilesystemListViewmodelFactory
import java.io.File

class FilesystemListFragment : Fragment() {

    private lateinit var activityContext: Activity

    private lateinit var filesystemList: List<Filesystem>

    private val externalStorageDir = Environment.getExternalStorageDirectory()

    private val filesystemListViewModel: FilesystemListViewModel by lazy {
        val filesystemDao = UlaDatabase.getInstance(activityContext).filesystemDao()
        val busyboxExecutor = BusyboxExecutor(activityContext.filesDir, externalStorageDir, DefaultPreferences(activityContext.defaultSharedPreferences))
        val filesystemUtility = FilesystemUtility(activityContext.filesDir.absolutePath, busyboxExecutor)
        ViewModelProviders.of(this, FilesystemListViewmodelFactory(filesystemDao, filesystemUtility)).get(FilesystemListViewModel::class.java)
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let { list ->
            filesystemList = list

            list_filesystems.adapter = FilesystemListAdapter(activityContext, filesystemList)
        }
    }

    private val filesystemExportStatusObserver = Observer<FilesystemExportStatus> {
        it?.let { exportStatus ->
            when (exportStatus) {
                is ExportSuccess -> Toast.makeText(activityContext, "Successfully exported backup", Toast.LENGTH_LONG).show()
                is ExportFailure -> Toast.makeText(activityContext, "Unable to exported backup: ${exportStatus.reason}", Toast.LENGTH_LONG).show()
            }
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
        return inflater.inflate(R.layout.frag_filesystem_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!
        filesystemListViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)
        filesystemListViewModel.getExportStatusLiveData().observe(viewLifecycleOwner, filesystemExportStatusObserver)
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
            R.id.menu_item_filesystem_delete -> deleteFilesystem(filesystem)
            R.id.menu_item_filesystem_export -> exportFilesystem(filesystem)
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

    private fun exportFilesystem(filesystem: Filesystem): Boolean {
        // TODO: Add real listener
        val statelessListener: (line: String) -> Unit = { }

        val localDestination = File("${activityContext.filesDir.path}/backups")
        val externalDestination = File(Environment.getExternalStoragePublicDirectory("UserLAnd").toString())
        filesystemListViewModel.compressFilesystem(filesystem, localDestination, externalDestination, statelessListener)

        return true
    }
}