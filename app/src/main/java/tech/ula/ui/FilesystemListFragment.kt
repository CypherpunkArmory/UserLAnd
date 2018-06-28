package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import android.widget.AdapterView
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_filesystemlist.*
import org.jetbrains.anko.bundleOf
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.viewmodel.FilesystemListViewModel

class FilesystemListFragment : Fragment() {

    private lateinit var activityContext: Activity

    private lateinit var filesystemList: List<Filesystem>

    private val fileSystemListViewModel: FilesystemListViewModel by lazy {
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
        return if(item.itemId == R.id.menu_item_add) editFilesystem(Filesystem(0))
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fileSystemListViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)
        return inflater.inflate(R.layout.frag_filesystemlist, container, false)
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
        return when(item.itemId) {
            R.id.menu_item_filesystem_edit -> editFilesystem(filesystem)
            R.id.menu_item_filesystem_delete -> deleteFilesystem(filesystem)
            else -> super.onContextItemSelected(item)
        }
    }

    private fun editFilesystem(filesystem: Filesystem): Boolean {
        // TODO bundle
        val editExisting = filesystem.name != ""
        val bundle = bundleOf("filesystem" to filesystem, "editExisting" to editExisting)
        NavHostFragment.findNavController(this).navigate(R.id.filesystem_edit_fragment, bundle)
        return true
    }

    private fun deleteFilesystem(filesystem: Filesystem): Boolean {
        return true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}