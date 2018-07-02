package tech.ula.ui

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_session_edit.*
import org.jetbrains.anko.bundleOf
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.launchAsync
import tech.ula.viewmodel.SessionEditViewModel

class SessionEditFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val session: Session by lazy {
        arguments?.getParcelable("session") as Session
    }

    private val editExisting: Boolean by lazy {
        arguments?.getBoolean("editExisting") ?: false
    }

    private var sessionClientTypeList = ArrayList<String>()

    private lateinit var filesystemList: List<Filesystem>

    private val sessionEditViewModel: SessionEditViewModel by lazy {
        ViewModelProviders.of(this).get(SessionEditViewModel::class.java)
    }

    private val filesystemChangeObserver = Observer<List<Filesystem>> {
        it?.let {
            filesystemList = it
            val filesystemNameList = ArrayList(filesystemList.map { filesystem -> filesystem.name })
            filesystemNameList.add("Create new")
            if(it.isEmpty()) {
                filesystemNameList.add("")
            }
            val filesystemAdapter = ArrayAdapter(activityContext, android.R.layout.simple_spinner_item, filesystemNameList)
            filesystemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            val filesystemNamePosition = filesystemAdapter.getPosition(session.filesystemName)
            spinner_filesystem_list.adapter = filesystemAdapter
            spinner_filesystem_list.setSelection(filesystemNamePosition)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        sessionEditViewModel.getAllFilesystems().observe(viewLifecycleOwner, filesystemChangeObserver)
        return inflater.inflate(R.layout.frag_session_edit, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if(item.itemId == R.id.menu_item_add) insertSession()
        else super.onOptionsItemSelected(item)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        text_input_session_name.setText(session.name)
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
                val filesystemName = parent?.getItemAtPosition(position).toString()
                when (filesystemName) {
                    "Create new" -> {
                        val bundle = bundleOf("filesystem" to Filesystem(0), "editExisting" to false)
                        NavHostFragment.findNavController(this@SessionEditFragment).navigate(R.id.filesystem_edit_fragment, bundle)
                    }
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

        spinner_session_service_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedServiceType = parent?.getItemAtPosition(position).toString()
                session.serviceType = selectedServiceType
                session.port = getDefaultServicePort(selectedServiceType)

                sessionClientTypeList = getSupportedClientTypes(selectedServiceType)
                spinner_session_client_type.adapter = ArrayAdapter(activityContext, android.R.layout.simple_spinner_dropdown_item, sessionClientTypeList)
            }
        }

        spinner_session_client_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedClientType = parent?.getItemAtPosition(position).toString()
                session.clientType = selectedClientType
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

    private fun insertSession(): Boolean {
        val navController = NavHostFragment.findNavController(this)

        if (session.name == "") text_input_session_name.error = getString(R.string.error_session_name)
        //TODO: Uncomment when we support unique usernames
        // /if (session.username == "") text_input_username.error = getString(R.string.error_username)
        if (session.filesystemName == "") {
            val errorText = spinner_filesystem_list.selectedView as TextView
            errorText.error = ""
            errorText.setTextColor(Color.RED)
            errorText.text = getString(R.string.error_filesystem_name)
        }

        if(session.name == "" || session.username == "" || session.filesystemName == "") {
            Toast.makeText(activityContext, R.string.error_empty_field, Toast.LENGTH_LONG).show()
        }
        else {
            if(editExisting) {
                sessionEditViewModel.updateSession(session)
                navController.popBackStack()
            }
            else {
                launchAsync {
                    when (sessionEditViewModel.insertSession(session)) {
                        true -> navController.popBackStack()
                        false -> Toast.makeText(activityContext, R.string.session_unique_name_required, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        return true
    }

    private fun getSupportedClientTypes(selectedServiceType: String): ArrayList<String> {
        return when(selectedServiceType) {
            "ssh" -> arrayListOf(*activityContext.resources.getStringArray(R.array.supported_ssh_clients))
            "vnc" -> arrayListOf(*activityContext.resources.getStringArray(R.array.supported_vnc_clients))
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