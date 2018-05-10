package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.TextInputEditText
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_session_edit.*
import tech.userland.userland.database.repositories.*
import tech.userland.userland.database.models.*
import java.util.*

class SessionEditActivity: AppCompatActivity() {

    var sessionName: String = ""
    var filesystemName: String = ""
    var sessionType: String = ""
    var username: String = ""
    var password: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_edit)
        setSupportActionBar(toolbar)

        // Session name input
        val sessionNameInput: TextInputEditText = findViewById(R.id.text_input_session_name)
        sessionNameInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                sessionName = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

        // Filesystem name dropdown
        val filesystemList = FilesystemRepository(this).getAllFilesystems()
        val filesystemNameList: ArrayList<String> = ArrayList(filesystemList.map { filesystem -> filesystem.name })
        //filesystemNameList.add("New")

        val filesystemNameDropdown: Spinner = findViewById(R.id.spinner_filesystem_list)
        val filesystemNameAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filesystemNameList)
        filesystemNameAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filesystemNameDropdown.adapter = filesystemNameAdapter
        filesystemNameDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // TODO how should this be handled?
            }

            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val data = parent.getItemAtPosition(position)
                if(data == "New") {
                    //navigateToFilesystemEdit()
                }
                else {
                    //Toast.makeText(this@SessionEditActivity, "Adding " + data.toString(), Toast.LENGTH_LONG).show()
                    filesystemName = data.toString()
                }
            }
        }

        // Session type dropdown
        val sessionTypeList = ArrayList<String>()
        sessionTypeList.add("ssh")

        val sessionTypeDropdown: Spinner = findViewById(R.id.spinner_session_type)
        val sessionTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sessionTypeList)
        sessionTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sessionTypeDropdown.adapter = sessionTypeAdapter
        sessionTypeDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val data = parent.getItemAtPosition(position)
                sessionType = data.toString()
            }
        }

        // Username input
        val usernameInput: TextInputEditText = findViewById(R.id.text_input_username)
        usernameInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                username = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

        // Password input
        val passwordInput: TextInputEditText = findViewById(R.id.text_input_password)
        passwordInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                password = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

        val submitButton: Button = findViewById(R.id.button_submit_new_session)
        submitButton.setOnClickListener {
            insertSession()
        }
    }

    fun navigateToFilesystemEdit() {
        val intent = Intent(this, FilesystemEditActivity::class.java)
        startActivity(intent)
    }

    fun insertSession() {
        val newSession = Session(sessionName, 0, username, password, "/", "/", "/", 0, false, sessionType)
        SessionRepository(this).insertSession(newSession)
    }
}