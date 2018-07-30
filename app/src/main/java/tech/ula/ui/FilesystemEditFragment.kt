package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.AdapterView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_filesystem_edit.* // ktlint-disable no-wildcard-imports
import org.jetbrains.anko.defaultSharedPreferences
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.utils.*
import tech.ula.viewmodel.FilesystemEditViewModel

class FilesystemEditFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val filesystem: Filesystem by lazy {
        arguments?.getParcelable("filesystem") as Filesystem
    }

    private val editExisting: Boolean by lazy {
        arguments?.getBoolean("editExisting") ?: false
    }

    private val filesystemEditViewModel: FilesystemEditViewModel by lazy {
        ViewModelProviders.of(this).get(FilesystemEditViewModel::class.java)
    }

    private val fileUtility: FileUtility by lazy {
        FileUtility(activityContext.filesDir.path)
    }

    private val execUtility: ExecUtility by lazy {
        ExecUtility(fileUtility, PreferenceUtility(activityContext.defaultSharedPreferences))
    }

    private val filesystemUtility: FilesystemUtility by lazy {
        FilesystemUtility(execUtility, fileUtility)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_item_add) insertFilesystem()
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_filesystem_edit, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input_filesystem_name.setText(filesystem.name)
        input_filesystem_name.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.name = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        if (editExisting) {
            spinner_filesystem_type.isEnabled = false
        }
        spinner_filesystem_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filesystem.distributionType = parent?.getItemAtPosition(position).toString()
            }
        }
    }

    private fun insertFilesystem(): Boolean {
        val navController = NavHostFragment.findNavController(this)
        if (filesystem.name == "") input_filesystem_name.error = getString(R.string.error_filesystem_name)
        if (filesystem.name == "") {
            Toast.makeText(activityContext, R.string.error_empty_field, Toast.LENGTH_LONG).show()
        } else {
            if (editExisting) {
                filesystemEditViewModel.updateFilesystem(filesystem)
                navController.popBackStack()
            } else {
                try {
                    filesystem.archType = filesystemUtility.getArchType()
                } catch (err: Exception) {
                    Toast.makeText(activityContext, R.string.no_supported_architecture, Toast.LENGTH_LONG).show()
                    return true
                }
                launchAsync {
                    when (filesystemEditViewModel.insertFilesystem(filesystem)) {
                        true -> navController.popBackStack()
                        false -> Toast.makeText(activityContext, R.string.filesystem_unique_name_required, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        return true
    }
}