package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import kotlinx.android.synthetic.main.frag_filesystem_list.*
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

            list_file_system_management.adapter = FilesystemListAdapter(activityContext, filesystemList)
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fileSystemListViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)
        return inflater.inflate(R.layout.frag_filesystem_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!
    }
}