package tech.userland.userland

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_session_create.*

class SessionCreateActivity: AppCompatActivity() {
    lateinit var sharedPreferences: SharedPreferences
    lateinit var fileSystemList: ArrayList<String>
    lateinit var sessionList: ArrayList<String>

    private var hasSpinnerBeenCalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_create)
        setSupportActionBar(toolbar)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        fileSystemList = ArrayList(sharedPreferences.getString("fileSystems", "").split(", "))
        sessionList = ArrayList(sharedPreferences.getString("sessions", "").split(", "))
        val arrayAdapterList: ArrayList<String> = fileSystemList
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
                    sessionList.add(data.toString())
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val sessionListString = sessionList.joinToString()
        with(sharedPreferences.edit()) {
            putString("sessions", sessionListString)
            commit()
        }
    }

    fun navigateToCreateFileSystem() {
        val intent = Intent(this, FileSystemCreateActivity::class.java)
        startActivity(intent)
    }
}