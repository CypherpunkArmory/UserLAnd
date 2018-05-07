package tech.userland.userland

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.TextInputEditText
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_file_system_create.*

class FileSystemCreateActivity: AppCompatActivity() {
    lateinit var sharedPreferences: SharedPreferences
    lateinit var fileSystemList: ArrayList<String>
    var newFileSystemName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_system_create)
        setSupportActionBar(toolbar)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        fileSystemList = ArrayList(sharedPreferences.getString("fileSystems", "").split(", "))
        val nameInput: TextInputEditText = findViewById(R.id.input_file_system_name)
        nameInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                newFileSystemName = p0.toString()
                Toast.makeText(this@FileSystemCreateActivity, newFileSystemName, Toast.LENGTH_LONG).show()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

        val submitButton: Button = findViewById(R.id.button_submit_new_file_system)
        submitButton.setOnClickListener {
            fileSystemList.add(newFileSystemName)
            Toast.makeText(this@FileSystemCreateActivity, "Added " + newFileSystemName, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        val fileSystemListString = fileSystemList.joinToString()
        with (sharedPreferences.edit()) {
            putString("fileSystems", fileSystemListString)
            commit()
        }
    }
}