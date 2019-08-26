package tech.ula.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.room.Delete
import kotlinx.android.synthetic.main.frag_cloud_demo.*
import kotlinx.android.synthetic.main.frag_cloud_demo.view.*
import tech.ula.MainActivity
import tech.ula.R
import tech.ula.viewmodel.*

class CloudDemoFragment : Fragment() {

    interface CloudProgress {
        fun updateCloudProgress(details: String)
        fun killCloudProgress()
    }

    private val viewModel by lazy {
        ViewModelProviders.of(this, CloudDemoViewModelFactory()).get(CloudDemoViewModel::class.java)
    }

    private val cloudStateObserver = Observer<CloudState> { it?.let { state ->

        when (state) {
            is LoginResult -> handleLoginResult(state)
            is ConnectResult -> handleConnectResult(state)
            is DeleteResult -> handleDeleteResult(state)
        }
    } }

    private val activityContext by lazy {
        activity!! as MainActivity
    }

    private var email = ""
    private var password = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_cloud_demo, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.getCloudState().observe(this, cloudStateObserver)

        cloud_demo_email_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable) {
                email = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        cloud_demo_password_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable) {
                password = p0.toString()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        cloud_demo_login_button.setOnClickListener { viewModel.handleLoginClick(email, password) }
        cloud_demo_connect_button.setOnClickListener { viewModel.handleConnectClick(activityContext.filesDir) }
        cloud_demo_delete_button.setOnClickListener { viewModel.handleDeleteClick() }
    }

    private fun handleLoginResult(state: LoginResult) {
        when (state) {
            is LoginResult.InProgress -> activityContext.updateCloudProgress("Logging in")
            is LoginResult.Success -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Login succeeded", Toast.LENGTH_LONG).show()
            }
            is LoginResult.Failure -> {
                Toast.makeText(activityContext, "Login failed", Toast.LENGTH_LONG).show()
                activityContext.killCloudProgress()
            }
        }
    }

    private fun handleConnectResult(state: ConnectResult) {
        val a = when (state) {
            is ConnectResult.InProgress -> activityContext.updateCloudProgress("Creating box")
            is ConnectResult.Success -> {
                activityContext.killCloudProgress()
                startTermux(state.ipAddress, state.sshPort)
            }
            is ConnectResult.PublicKeyNotFound -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Public SSH key not found", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.RequestFailed -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "API request failed ${state.message}", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.BoxCreateFailure -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Failed to create box", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.NullResponseFromCreate -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Null response from list", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.ConnectFailure -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Failed to connect to box", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.BusyboxMissing -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Busybox is not downloaded", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.LinkFailed -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Could not link busybox to sh", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleDeleteResult(state: DeleteResult) {
        val a = when (state) {
            is DeleteResult.InProgress -> {
                activityContext.updateCloudProgress("Finding active box")
            }
            is DeleteResult.IdRetrieved -> {
                activityContext.updateCloudProgress("Deleting ${state.id}")
            }
            is DeleteResult.Success -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Successfully deleted ${state.id}", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.ListRequestFailure -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Fetching boxes failed", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.ListResponseFailure -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Fetching boxes response err: ${state.message}", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.NullResponseFromList -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Null response from list", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.DeleteRequestFailure -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Delete request failed", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.DeleteResponseFailure -> {
                activityContext.killCloudProgress()
                Toast.makeText(activityContext, "Delete response err: ${state.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startTermux(ipAddress: String, sshPort: Int) {
        val intent = Intent()
        intent.action = "android.intent.action.VIEW"
        intent.data = Uri.parse("ssh://ULACLOUDDEMO@$ipAddress:$sshPort/#userland")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        startActivity(intent)
    }
}