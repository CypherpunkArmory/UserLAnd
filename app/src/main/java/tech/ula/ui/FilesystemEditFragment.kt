package tech.ula.ui

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_filesystem_edit.*
import tech.ula.MainActivity
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.AppsPreferences
import tech.ula.utils.BuildWrapper
import tech.ula.utils.ValidationUtility
import tech.ula.utils.arePermissionsGranted
import tech.ula.viewmodel.FilesystemImportStatus
import tech.ula.viewmodel.ImportSuccess
import tech.ula.viewmodel.ImportFailure
import tech.ula.viewmodel.FilesystemEditViewModel
import tech.ula.viewmodel.FilesystemEditViewmodelFactory

class FilesystemEditFragment : Fragment() {

    private lateinit var activityContext: MainActivity

    private val filesystem: Filesystem by lazy {
        arguments?.getParcelable("filesystem") as Filesystem
    }

    private val editExisting: Boolean by lazy {
        arguments?.getBoolean("editExisting") ?: false
    }

    private val permissionRequestCode: Int by lazy {
        getString(R.string.permission_request_code).toInt()
    }

    private val filesystemImportStatusObserver = Observer<FilesystemImportStatus> {
        it?.let { importStatus ->
            when (importStatus) {
                is ImportSuccess -> Toast.makeText(activityContext, "Successfully imported backup", Toast.LENGTH_LONG).show()
                is ImportFailure -> Toast.makeText(activityContext, "Unable to import backup", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val filesystemEditViewModel: FilesystemEditViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(activityContext)
        ViewModelProviders.of(this, FilesystemEditViewmodelFactory(ulaDatabase)).get(FilesystemEditViewModel::class.java)
    }

    private val distributionList by lazy {
        AppsPreferences(activityContext.getSharedPreferences("apps", Context.MODE_PRIVATE))
                .getDistributionsList()
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
        activityContext = activity!! as MainActivity
        filesystemEditViewModel.getImportStatusLiveData().observe(viewLifecycleOwner, filesystemImportStatusObserver)

        if (distributionList.isNotEmpty()) {
            spinner_filesystem_type.adapter = ArrayAdapter(activityContext,
                    android.R.layout.simple_spinner_dropdown_item,
                    distributionList.map { it.capitalize() })
        }
        if (editExisting) {
            for (i in 0 until spinner_filesystem_type.adapter.count) {
                val item = spinner_filesystem_type.adapter.getItem(i).toString().toLowerCase()
                if (item == filesystem.distributionType) spinner_filesystem_type.setSelection(i)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTextInputs()
        setupImportButton()
        setupAdvancedOptionButton()

        if (editExisting) {
            spinner_filesystem_type.isEnabled = false
        }
        spinner_filesystem_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filesystem.distributionType = parent?.getItemAtPosition(position).toString().toLowerCase()
            }
        }
    }

    private fun setupTextInputs() {
        input_filesystem_name.setText(filesystem.name)
        input_filesystem_username.setText(filesystem.defaultUsername)
        input_filesystem_password.setText(filesystem.defaultPassword)
        input_filesystem_vncpassword.setText(filesystem.defaultVncPassword)

        if (editExisting) {
            input_filesystem_username.isEnabled = false
            input_filesystem_password.isEnabled = false
            input_filesystem_vncpassword.isEnabled = false
        }

        if (filesystem.isAppsFilesystem) {
            input_filesystem_name.isEnabled = false
        }

        input_filesystem_name.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.name = p0.toString()
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        input_filesystem_username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

                filesystem.defaultUsername = p0.toString()
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        input_filesystem_password.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.defaultPassword = p0.toString()
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        input_filesystem_vncpassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                filesystem.defaultVncPassword = p0.toString()
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
    }

    private fun setupImportButton() {
        import_button.setOnClickListener {
            val filePickerIntent = Intent(Intent.ACTION_GET_CONTENT)
            filePickerIntent.addCategory(Intent.CATEGORY_OPENABLE)
            filePickerIntent.type = "application/*"
            val fileChooser = Intent.createChooser(filePickerIntent, "Select Filesystem Backup file")
            if (!arePermissionsGranted(activityContext)) {
                showPermissionsNecessaryDialog()
                return@setOnClickListener
            }

            try {
                filesystem.isCreatedFromBackup = true
                startActivityForResult(fileChooser, IMPORT_FILESYSTEM_REQUEST_CODE)
            } catch (activityNotFoundErr: ActivityNotFoundException) {
                Toast.makeText(activityContext, "Please install a File Manager.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAdvancedOptionButton() {
        val btn = btn_show_advanced_options

        btn.setOnClickListener {
            when (btn.isChecked) {
                true -> {
                    btn.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_down_white_24dp, 0)
                    advanced_options.visibility = View.VISIBLE
                }
                false -> {
                    btn.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_keyboard_arrow_right_white_24dp, 0)
                    advanced_options.visibility = View.GONE
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun showPermissionsNecessaryDialog() {
        val builder = AlertDialog.Builder(activityContext)
        builder.setMessage(R.string.alert_permissions_necessary_message)
                .setTitle(R.string.alert_permissions_necessary_title)
                .setPositiveButton(R.string.button_ok) {
                    dialog, _ ->
                    requestPermissions(arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            permissionRequestCode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.alert_permissions_necessary_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
        builder.create().show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, returnIntent: Intent?) {
        super.onActivityResult(requestCode, resultCode, returnIntent)
        if (requestCode == IMPORT_FILESYSTEM_REQUEST_CODE) {
            returnIntent?.data?.let { uri ->
                filesystemEditViewModel.setBackupUri(uri)
                text_backup_filename.text = uri.lastPathSegment
            }
        }
    }

    private fun insertFilesystem(): Boolean {
        val navController = NavHostFragment.findNavController(this)
        if (!filesystemParametersAreCorrect()) {
            return false
        }

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
            if (filesystem.isCreatedFromBackup) {
                val backupUri = filesystemEditViewModel.getBackupUri()
                filesystemEditViewModel.insertFilesystemFromBackup(activityContext.contentResolver, filesystem, activityContext.filesDir, backupUri)
            } else {
                filesystemEditViewModel.insertFilesystem(filesystem)
            }
            navController.popBackStack()
        }

        return true
    }

    private fun filesystemParametersAreCorrect(): Boolean {
        val blacklistedUsernames = activityContext.resources.getStringArray(R.array.blacklisted_usernames)
        val validator = ValidationUtility()
        val username = filesystem.defaultUsername
        val password = filesystem.defaultPassword
        val vncPassword = filesystem.defaultVncPassword

        if (filesystem.name.isEmpty()) {
            input_filesystem_name.error = getString(R.string.error_filesystem_name)
            return false
        }

        val usernameCredentials = validator.validateUsername(username, blacklistedUsernames)
        val passwordCredentials = validator.validatePassword(password)
        val vncPasswordCredentials = validator.validateVncPassword(vncPassword)

        when {
            !usernameCredentials.credentialIsValid ->
                Toast.makeText(activityContext, usernameCredentials.errorMessageId, Toast.LENGTH_LONG).show()
            !passwordCredentials.credentialIsValid ->
                Toast.makeText(activityContext, passwordCredentials.errorMessageId, Toast.LENGTH_LONG).show()
            !vncPasswordCredentials.credentialIsValid ->
                Toast.makeText(activityContext, vncPasswordCredentials.errorMessageId, Toast.LENGTH_LONG).show()
            else ->
                return true
        }
        return false
    }

    companion object {
        const val IMPORT_FILESYSTEM_REQUEST_CODE = 5
    }
}