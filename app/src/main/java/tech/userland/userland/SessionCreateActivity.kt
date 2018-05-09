package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_session_create.*
import tech.userland.userland.database.repositories.*
import tech.userland.userland.database.models.*

class SessionCreateActivity: AppCompatActivity() {
    lateinit var filesystemList: ArrayList<Filesystem>

    private var hasSpinnerBeenCalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_create)
        setSupportActionBar(toolbar)

        filesystemList = FilesystemRepository(this).getAllFilesystems()
        val arrayAdapterList: ArrayList<String> = ArrayList(filesystemList.map { filesystem -> filesystem.name })
        arrayAdapterList.add("New")

        val dropdown: Spinner = findViewById(R.id.spinner_file_system_list)
        dropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayAdapterList)
        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // TODO how should this be handled?
            }

            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {

                // onItemSelected is called during initialization. This is to avoid adding empty
                // values.
                if(!hasSpinnerBeenCalled) {
                    hasSpinnerBeenCalled = true
                    return
                }
                val data = parent.getItemAtPosition(position)
                if(data == "New") {
                    navigateToCreateFileSystem()
                }
                else {
                    Toast.makeText(this@SessionCreateActivity, "Adding " + data.toString(), Toast.LENGTH_LONG).show()
                    SessionRepository(this@SessionCreateActivity).insertSession(genStubSession(data.toString()))
                }
            }
        }
    }

    fun navigateToCreateFileSystem() {
        val intent = Intent(this, FileSystemCreateActivity::class.java)
        startActivity(intent)
    }

    fun genStubSession(name: String): Session {
        return Session(name, 0, "/", "/", "/", 0, true, "ssh")
    }
}