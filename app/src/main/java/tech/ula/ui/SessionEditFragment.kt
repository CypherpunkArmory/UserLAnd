package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.* // ktlint-disable no-wildcard-imports
import android.widget.* // ktlint-disable no-wildcard-imports
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_session_edit.* // ktlint-disable no-wildcard-imports
import org.jetbrains.anko.bundleOf
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.viewmodel.SessionEditViewModel
import tech.ula.viewmodel.SessionEditViewmodelFactory

class SessionEditFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val session: Session by lazy {
        arguments?.getParcelable("session") as Session
    }

    private val editExisting: Boolean by lazy {
        arguments?.getBoolean("editExisting") ?: false
    }

    private var filesystemList: List<Filesystem> = emptyList()

    private val sessionEditViewModel: SessionEditViewModel by lazy {
        val ulaDatabase = UlaDatabase.getInstance(activityContext)
        ViewModelProviders.of(this, SessionEditViewmodelFactory(ulaDatabase)).get(SessionEditViewModel::class.java)
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let { newList ->
            val spinnerList = augmentFilesystemList(newList)

            getListDifferenceAndSetNewFilesystem(filesystemList, it)

            val filesystemAdapter = ArrayAdapter(activityContext, android.R.layout.simple_spinner_dropdown_item, spinnerList)
            val filesystemNamePosition = it.indexOfFirst {
                filesystem -> filesystem.id == session.filesystemId
            }

            val usedPosition = if (filesystemNamePosition < 0) 0 else filesystemNamePosition

            spinner_filesystem_list.adapter = filesystemAdapter
            spinner_filesystem_list.setSelection(usedPosition)

            filesystemList = it
        }
    }

    internal sealed class FilesystemDropdownItem {
        data class NonFilesystemItem(val text: String) : FilesystemDropdownItem() {
            override fun toString(): String {
                return text
            }
        }
        data class FilesystemItem(val filesystem: Filesystem) : FilesystemDropdownItem() {
            override fun toString(): String {
                return "${filesystem.name}: ${filesystem.distributionType.capitalize()}"
            }
        }
    }

    private fun augmentFilesystemList(filesystems: List<Filesystem>): List<FilesystemDropdownItem> {
        val listBuilder = mutableListOf<FilesystemDropdownItem>()
        if (filesystems.isEmpty()) listBuilder.add(FilesystemDropdownItem.NonFilesystemItem(""))
        listBuilder.addAll(filesystems.map { FilesystemDropdownItem.FilesystemItem(it) })
        listBuilder.add(FilesystemDropdownItem.NonFilesystemItem("Create new"))
        return listBuilder.toList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_session_edit, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_item_add) {
            insertSession()
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
        sessionEditViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkbox_start_boot.isChecked = session.startOnBoot
        checkbox_start_boot.setOnCheckedChangeListener { buttonView, isChecked -> session.startOnBoot = isChecked }

        text_input_session_name.setText(session.name)
        if (session.isAppsSession) {
            text_input_session_name.isEnabled = false
        }
        text_input_session_name.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                session.name = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        spinner_filesystem_list.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                parent?.let {
                    val item = parent.getItemAtPosition(position) as FilesystemDropdownItem
                    when (item) {
                        is FilesystemDropdownItem.NonFilesystemItem -> {
                            if (item.text == "Create new") {
                                val bundle = bundleOf("filesystem" to Filesystem(0), "editExisting" to false)
                                NavHostFragment.findNavController(this@SessionEditFragment).navigate(R.id.filesystem_edit_fragment, bundle)
                            } else { }
                        }
                        is FilesystemDropdownItem.FilesystemItem -> {
                            val filesystem = item.filesystem
                            updateFilesystemDetailsForSession(filesystem)
                            text_input_username.setText(filesystem.defaultUsername)
                        }
                    }
                }
            }
        }

        spinner_session_service_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedServiceType = parent?.getItemAtPosition(position).toString()
                session.serviceType = selectedServiceType
                session.port = getDefaultServicePort(selectedServiceType)
            }
        }

        text_input_username.isEnabled = false
        text_input_username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                session.username = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
    }

    private fun insertSession() {
        val navController = NavHostFragment.findNavController(this)

        if (session.name == "") {
            text_input_session_name.error = getString(R.string.error_session_name)
        }
        if (session.filesystemName == "") {
            val errorText = spinner_filesystem_list.selectedView as TextView
            errorText.error = ""
            errorText.setTextColor(Color.RED)
            errorText.text = getString(R.string.error_filesystem_name)
        }

        if (session.name == "" || session.username == "" || session.filesystemName == "") {
            Toast.makeText(activityContext, R.string.error_empty_field, Toast.LENGTH_LONG).show()
            return
        }

        if (editExisting) sessionEditViewModel.updateSession(session)
        else sessionEditViewModel.insertSession(session)
        navController.popBackStack()
    }

    private fun getDefaultServicePort(selectedServiceType: String): Long {
        return when (selectedServiceType) {
            "vnc" -> 51
            else -> 2022
        }
    }

    private fun getListDifferenceAndSetNewFilesystem(prevFilesystems: List<Filesystem>, currentFilesystems: List<Filesystem>) {
        val uniqueFilesystems = currentFilesystems.subtract(prevFilesystems)
        if (prevFilesystems.isNotEmpty() && uniqueFilesystems.isNotEmpty()) {
            updateFilesystemDetailsForSession(uniqueFilesystems.first())
        }
    }

    private fun updateFilesystemDetailsForSession(filesystem: Filesystem) {
        session.filesystemName = filesystem.name
        session.username = filesystem.defaultUsername
        session.password = filesystem.defaultPassword
        session.vncPassword = filesystem.defaultVncPassword
        session.filesystemId = filesystem.id
    }
}