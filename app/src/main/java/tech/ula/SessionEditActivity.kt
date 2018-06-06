package tech.ula

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.TextInputEditText
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_session_edit.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import tech.ula.database.models.Filesystem
import tech.ula.database.models.Session
import tech.ula.ui.FilesystemViewModel
import tech.ula.ui.SessionViewModel
import tech.ula.utils.launchAsync

class SessionEditActivity: AppCompatActivity() {

    val session: Session by lazy {
        intent.getParcelableExtra("session") as Session
    }

    var editExisting = false

    lateinit var filesystemList: List<Filesystem>

    private val sessionViewModel: SessionViewModel by lazy {
        ViewModelProviders.of(this).get(SessionViewModel::class.java)
    }

    private val filesystemViewModel: FilesystemViewModel by lazy {
        ViewModelProviders.of(this).get(FilesystemViewModel::class.java)
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let {
            filesystemList = it
            val filesystemNameList = ArrayList(filesystemList.map { filesystem -> filesystem.name })
            filesystemNameList.add("Create new")
            if(it.isEmpty()) {
                filesystemNameList.add("")
            }
            val filesystemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filesystemNameList)
            filesystemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            val filesystemNamePosition = filesystemAdapter.getPosition(session.filesystemName)
            spinner_filesystem_list.adapter = filesystemAdapter
            spinner_filesystem_list.setSelection(filesystemNamePosition)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_edit)
        setSupportActionBar(toolbar)

        filesystemViewModel.getAllFilesystems().observe(this, filesystemChangeObserver)

        // Session name input
        if (session.name != "") {
            editExisting = true
        }
        val sessionNameInput: TextInputEditText = findViewById(R.id.text_input_session_name)
        sessionNameInput.setText(session.name)
        sessionNameInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                session.name = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        // Filesystem name dropdown
        spinner_filesystem_list.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val filesystemName = parent?.getItemAtPosition(position).toString()
                when (filesystemName) {
                    "Create new" -> navigateToFilesystemEdit("")
                    "" -> return
                    else -> {
                        // TODO adapter to associate filesystem structure with list items?
                        val filesystem = filesystemList.find { it.name == filesystemName }
                        session.filesystemName = filesystem!!.name
                        session.filesystemId = filesystem.id
                        text_input_username.setText(filesystem.defaultUsername)
                    }
                }
            }
        }

        // Session service type dropdown
        // TODO reenable this dropdown
        val sessionServiceTypeList = ArrayList<String>()
        sessionServiceTypeList.add("ssh")

        val sessionServiceTypeDropdown: Spinner = findViewById(R.id.spinner_session_service_type)
        val sessionServiceTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sessionServiceTypeList)
        sessionServiceTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sessionServiceTypeDropdown.adapter = sessionServiceTypeAdapter
        sessionServiceTypeDropdown.isEnabled = false
        sessionServiceTypeDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val data = parent?.getItemAtPosition(position) ?: ""
                session.serviceType = data.toString()
            }
        }

        // Session client type dropdown
        // TODO reenable this dropdown
        val sessionClientTypeList = ArrayList<String>()
        sessionClientTypeList.add("ConnectBot")

        val sessionClientTypeDropdown: Spinner = findViewById(R.id.spinner_session_client_type)
        val sessionClientTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sessionClientTypeList)
        sessionClientTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sessionClientTypeDropdown.adapter = sessionClientTypeAdapter
        sessionClientTypeDropdown.isEnabled = false
        sessionClientTypeDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val data = parent?.getItemAtPosition(position) ?: ""
                session.clientType = data.toString()
            }
        }

        // Username input
        text_input_username.isEnabled = false
        text_input_username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                session.username = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
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
                insertSession()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    fun navigateToFilesystemEdit(filesystemName: String): Boolean {
        val intent = Intent(this, FilesystemEditActivity::class.java)
        intent.putExtra("filesystemName", filesystemName)
        startActivity(intent)
        return true
    }

    private fun insertSession() {
        // TODO cleaner logic
        if(session.name == "" || session.username == "") {
            toast("Each field must be answered.")
        }
        else {
            if(editExisting) {
                sessionViewModel.updateSession(session)
                finish()
            }
            else {
                launchAsync {
                    when (sessionViewModel.insertSession(session)) {
                        true -> finish()
                        false -> longToast(R.string.session_unique_name_required)
                    }
                }
            }
        }
    }
}