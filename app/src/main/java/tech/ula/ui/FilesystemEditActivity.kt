package tech.ula.ui

import android.arch.lifecycle.ViewModelProviders
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_filesystem_edit.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.utils.FilesystemUtility
import tech.ula.utils.launchAsync
import tech.ula.viewmodel.FilesystemEditViewModel
import java.util.*

class FilesystemEditActivity: AppCompatActivity() {

    val filesystem: Filesystem by lazy {
        intent.getParcelableExtra("filesystem") as Filesystem
    }

    private val editExisting: Boolean by lazy {
        intent.getBooleanExtra("editExisting", false)
    }

    private val filesystemEditViewModel: FilesystemEditViewModel by lazy {
        ViewModelProviders.of(this).get(FilesystemEditViewModel::class.java)
    }

    private val filesystemUtility: FilesystemUtility by lazy {
        FilesystemUtility(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filesystem_edit)
        setSupportActionBar(toolbar)

        input_filesystem_name.setText(filesystem.name)
        input_filesystem_name.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.name = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

        // OS type dropdown
        //TODO: I think the list of distributions supported could just be a array resource
        val filesystemTypeList = ArrayList<String>()
        filesystemTypeList.add("debian")
        //TODO: uncomment next line after a little more cleanup
        //filesystemTypeList.add("ubuntu")

        val filesystemTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filesystemTypeList)
        filesystemTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinner_filesystem_type.adapter = filesystemTypeAdapter
        if(editExisting) {
            spinner_filesystem_type.isEnabled = false
        }
        spinner_filesystem_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filesystem.distributionType = parent?.getItemAtPosition(position).toString()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if(editExisting) {
            menuInflater.inflate(R.menu.menu_edit, menu)
        }
        else {
            menuInflater.inflate(R.menu.menu_create, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.menu_item_add -> {
                insertFilesystem()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun insertFilesystem() {
        // TODO cleaner logic
        if (filesystem.name == "") input_filesystem_name.error = getString(R.string.error_filesystem_name)
        if(filesystem.name == "") {
            toast(R.string.error_empty_field)
        }
        else {
            if(editExisting) {
                filesystemEditViewModel.updateFilesystem(filesystem)
                finish()
            }
            else {
                try {
                    filesystem.archType = filesystemUtility.getArchType()
                }
                catch(err: Exception) {
                    longToast(R.string.no_supported_architecture)
                    return
                }
                launchAsync {
                    when (filesystemEditViewModel.insertFilesystem(filesystem)) {
                        true -> finish()
                        false -> longToast(R.string.filesystem_unique_name_required)
                    }
                }
            }
        }
    }
}