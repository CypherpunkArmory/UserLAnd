package tech.ula.ui

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.os.Environment
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
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
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

    private val execUtility: ExecUtility by lazy {
        val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        ExecUtility(activityContext.filesDir.path, externalStoragePath, DefaultPreferences(activityContext.defaultSharedPreferences))
    }

    private val filesystemUtility by lazy {
        FilesystemUtility(activityContext.filesDir.path, execUtility)
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

        restore_filesystem_button.setOnClickListener {

            val intent = Intent(Intent.ACTION_GET_CONTENT)
            //intent.type = "file/*"
            //intent.type = "application/gzip"
            intent.type = "*/*"
            startActivityForResult(intent,  0)
            startActivity(intent)

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            //Uri return from external activity
            val orgUri = data!!.data
            if (orgUri != null) {
                try {
                    filesystemUtility.restoreFilesystemByLocation("/support",activity = activityContext, backupUri = orgUri, restoreDirName = "${filesystem.id}")
                } catch (e: Exception) {
                    Toast.makeText(activityContext, e.message, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(activityContext, R.string.error_restore_no_filesystem, Toast.LENGTH_LONG).show()
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
                    filesystem.archType = BuildWrapper().getArchType()
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