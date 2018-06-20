package tech.ula.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
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
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.viewmodel.FilesystemViewModel
import tech.ula.viewmodel.SessionViewModel
import tech.ula.utils.launchAsync

class SessionEditActivity: AppCompatActivity() {

    val session: Session by lazy {
        intent.getParcelableExtra("session") as Session
    }

    private val editExisting: Boolean by lazy {
        intent.getBooleanExtra("editExisting", false)
    }

    private var sessionServiceTypeList = ArrayList<String>()
    private var sessionClientTypeList = ArrayList<String>()

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
        text_input_session_name.setText(session.name)
        text_input_session_name.addTextChangedListener(object : TextWatcher {
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
                    "Create new" -> navigateToFilesystemEdit()
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

        sessionServiceTypeList = getSupportedServiceTypes()

        spinner_session_service_type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sessionServiceTypeList)
        spinner_session_service_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedServiceType = parent?.getItemAtPosition(position).toString()
                session.serviceType = selectedServiceType
                session.port = getDefaultServicePort(selectedServiceType)

                sessionClientTypeList = getSupportedClientTypes(selectedServiceType)
                spinner_session_client_type.adapter = ArrayAdapter(this@SessionEditActivity, android.R.layout.simple_spinner_dropdown_item, sessionClientTypeList)
            }
        }

        // Session client type dropdown
        spinner_session_client_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedClientType = parent?.getItemAtPosition(position).toString()
                session.clientType = selectedClientType
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


    fun navigateToFilesystemEdit(): Boolean {
        val intent = Intent(this, FilesystemEditActivity::class.java)
        intent.putExtra("filesystem", Filesystem(0))
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

    private fun getSupportedServiceTypes(): ArrayList<String> {
        return arrayListOf("ssh", "vnc")
    }

    private fun getSupportedClientTypes(selectedServiceType: String): ArrayList<String> {
        return when(selectedServiceType) {
            "ssh" -> arrayListOf("ConnectBot")
            "vnc" -> arrayListOf("bVNC")
            else -> arrayListOf()
        }
    }

    private fun getDefaultServicePort(selectedServiceType: String): Long {
        return when(selectedServiceType) {
            "vnc" -> 51
            else -> 2022
        }
    }
}