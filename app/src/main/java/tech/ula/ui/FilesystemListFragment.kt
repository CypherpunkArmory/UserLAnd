package tech.ula.ui

import android.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.fragment.app.Fragment
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_filesystem_list.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import tech.ula.MainActivity
import tech.ula.R
import tech.ula.ServerService
import tech.ula.model.entities.Filesystem
import tech.ula.model.repositories.UlaDatabase
import tech.ula.viewmodel.* // ktlint-disable no-wildcard-imports
import tech.ula.model.entities.Session
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import tech.ula.viewmodel.FilesystemListViewModel
import kotlin.coroutines.CoroutineContext

private const val FILESYSTEM_EXPORT_REQUEST_CODE = 7

class FilesystemListFragment : Fragment(), CoroutineScope {

    interface ExportFilesystem {
        fun updateExportProgress(details: String)
        fun stopExportProgress()
    }

    private lateinit var activityContext: MainActivity

    private lateinit var filesystemList: List<Filesystem>

    private val externalStorageDir = Environment.getExternalStorageDirectory()

    private lateinit var activeSessions: List<Session>

    private val filesystemListViewModel: FilesystemListViewModel by lazy {
        val filesystemDao = UlaDatabase.getInstance(activityContext).filesystemDao()
        val sessionDao = UlaDatabase.getInstance(activityContext).sessionDao()
        val prootDebugLogger = ProotDebugLogger(activityContext.defaultSharedPreferences, activityContext.storageRoot.path)
        val busyboxExecutor = BusyboxExecutor(activityContext.filesDir, externalStorageDir, prootDebugLogger)
        val filesystemUtility = FilesystemUtility(activityContext.filesDir.absolutePath, busyboxExecutor)
        ViewModelProviders.of(this, FilesystemListViewmodelFactory(filesystemDao, sessionDao, filesystemUtility)).get(FilesystemListViewModel::class.java)
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
                is ExportUpdate -> {
                    activityContext.updateExportProgress(exportStatus.details)
                }
                is ExportSuccess -> {
                    activityContext.stopExportProgress()
                    Toast.makeText(activityContext, R.string.backup_export_success, Toast.LENGTH_LONG).show()
                }
                is ExportFailure -> {
                    val dialogBuilder = AlertDialog.Builder(activityContext)
                    val reason = if (exportStatus.reason == R.string.error_export_execution_failure) {
                        getString(exportStatus.reason, exportStatus.details)
                    } else {
                        getString(exportStatus.reason)
                    }
                    dialogBuilder.setMessage(getString(R.string.export_failure) + "\n" + reason).create().show()
                    activityContext.stopExportProgress()
                }
                is ExportStarted -> {
                    activityContext.updateExportProgress(getString(R.string.export_started))
                }
            }
        }
    }

    private val activeSessionObserver = Observer<List<Session>> {
        it?.let { sessions -> activeSessions = sessions }
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

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

        activityContext = activity!! as MainActivity
        filesystemListViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)
        filesystemListViewModel.getExportStatusLiveData().observe(viewLifecycleOwner, filesystemExportStatusObserver)
        filesystemListViewModel.getAllActiveSessions().observe(viewLifecycleOwner, activeSessionObserver)
        registerForContextMenu(list_filesystems)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILESYSTEM_EXPORT_REQUEST_CODE) {
            data?.data?.let { uri ->
                val filesDir = activityContext.filesDir
                filesystemListViewModel.startExport(filesDir, uri, activityContext.contentResolver)
            }
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
        val suggestedFilesystemBackupName = filesystemListViewModel.getFilesystemBackupName(filesystem)
        val intent = createExportExternalIntent(suggestedFilesystemBackupName)
        filesystemListViewModel.setFilesystemToBackup(filesystem)
        startActivityForResult(intent, FILESYSTEM_EXPORT_REQUEST_CODE)
        return true
    }

    private fun createExportExternalIntent(backupName: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/*"
            putExtra(Intent.EXTRA_TITLE, backupName)
        }
    }
}