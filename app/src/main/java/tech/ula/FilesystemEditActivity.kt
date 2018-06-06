package tech.ula

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.design.widget.TextInputEditText
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
import tech.ula.database.models.Filesystem
import tech.ula.ui.FilesystemViewModel
import tech.ula.utils.launchAsync
import java.util.*

class FilesystemEditActivity: AppCompatActivity() {

    val filesystem: Filesystem by lazy {
        intent.getParcelableExtra("filesystem") as Filesystem
    }

    var editExisting = false

    private val filesystemViewModel: FilesystemViewModel by lazy {
        ViewModelProviders.of(this).get(FilesystemViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filesystem_edit)
        setSupportActionBar(toolbar)

        if(filesystem.name != "") {
            editExisting = true
        }

        val nameInput: TextInputEditText = findViewById(R.id.input_filesystem_name)
        nameInput.setText(filesystem.name)
        nameInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.name = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

        // OS type dropdown
        val filesystemTypeList = ArrayList<String>()
        filesystemTypeList.add("debian")

        val filesystemTypeDropdown: Spinner = findViewById(R.id.spinner_filesystem_type)
        val filesystemTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filesystemTypeList)
        filesystemTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filesystemTypeDropdown.adapter = filesystemTypeAdapter
        if(editExisting) {
            filesystemTypeDropdown.isEnabled = false
        }
        filesystemTypeDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filesystem.distributionType = parent?.getItemAtPosition(position).toString()
            }
        }

        if(editExisting) filesystemTypeDropdown.isEnabled = false

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
        if(filesystem.name == "") {
            toast("Filesystem name cannot be blank.")
        }
        else {
            if(editExisting) {
                filesystemViewModel.updateFilesystem(filesystem)
                finish()
            }
            else {
                launchAsync {
                    when (filesystemViewModel.insertFilesystem(filesystem)) {
                        true -> finish()
                        false -> longToast(R.string.filesystem_unique_name_required)
                    }
                }
            }
        }
    }
}