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
        fun updateProgress(details: String)
        fun killProgress()
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
            is LoginResult.InProgress -> activityContext.updateProgress("Logging in")
            is LoginResult.Success -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Login succeeded", Toast.LENGTH_LONG).show()
            }
            is LoginResult.Failure -> {
                Toast.makeText(activityContext, "Login failed", Toast.LENGTH_LONG).show()
                activityContext.killProgress()
            }
        }
    }

    private fun handleConnectResult(state: ConnectResult) {
        val a = when (state) {
            is ConnectResult.InProgress -> activityContext.updateProgress("Creating box")
            is ConnectResult.Success -> {
                activityContext.killProgress()
                startTermux(state.ipAddress, state.sshPort)
            }
            is ConnectResult.PublicKeyNotFound -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Public SSH key not found", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.RequestFailed -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "API request failed ${state.message}", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.BoxCreateFailure -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Failed to create box", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.ConnectFailure -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Failed to connect to box", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.BusyboxMissing -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Busybox is not downloaded", Toast.LENGTH_LONG).show()
            }
            is ConnectResult.LinkFailed -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Could not link busybox to sh", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleDeleteResult(state: DeleteResult) {
        val a = when (state) {
            is DeleteResult.InProgress -> {
                activityContext.updateProgress("Finding active box")
            }
            is DeleteResult.IdRetrieved -> {
                activityContext.updateProgress("Deleting ${state.id}")
            }
            is DeleteResult.Success -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Successfully deleted ${state.id}", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.ListRequestFailure -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Fetching boxes failed", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.ListResponseFailure -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Fetching boxes response err: ${state.message}", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.DeleteRequestFailure -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Delete request failed", Toast.LENGTH_LONG).show()
            }
            is DeleteResult.DeleteResponseFailure -> {
                activityContext.killProgress()
                Toast.makeText(activityContext, "Delete response err: ${state.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startTermux(ipAddress: String, sshPort: Int) {
        val intent = Intent()
        intent.action = "android.intent.action.VIEW"
        intent.data = Uri.parse("ssh://blah@$ipAddress:$sshPort/#userland")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        startActivity(intent)
    }
}