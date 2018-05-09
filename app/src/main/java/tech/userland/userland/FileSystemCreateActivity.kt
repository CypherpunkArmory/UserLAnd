package tech.userland.userland

import android.os.Bundle
import android.support.design.widget.TextInputEditText
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_file_system_create.*
import tech.userland.userland.database.models.Filesystem
import tech.userland.userland.database.repositories.FilesystemRepository

class FileSystemCreateActivity: AppCompatActivity() {
    var newFilesystemName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_system_create)
        setSupportActionBar(toolbar)

        val nameInput: TextInputEditText = findViewById(R.id.input_file_system_name)
        nameInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                newFilesystemName = p0.toString()
                Toast.makeText(this@FileSystemCreateActivity, newFilesystemName, Toast.LENGTH_LONG).show()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })

        val submitButton: Button = findViewById(R.id.button_submit_new_file_system)
        submitButton.setOnClickListener {
            insertFilesystem()
        }
    }

    fun insertFilesystem() {
        FilesystemRepository(this).insertFilesystem(genStubFilesystem(newFilesystemName))
    }

    fun genStubFilesystem(filesystemName: String): Filesystem {
        return Filesystem(filesystemName, false, "/", "debian", "now")
    }
}