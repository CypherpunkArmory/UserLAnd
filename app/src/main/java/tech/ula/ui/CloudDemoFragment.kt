package tech.ula.ui

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
            is LoginResult -> handleLoginResult()
            is ConnectResult -> handleConnectResult()
        }
    } }

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
        cloud_demo_connect_button.setOnClickListener { viewModel.handleConnectClick() }
    }

    private fun handleLoginResult(state: LoginResult) {
        val activityContext = activity!!
        activityContext as MainActivity
        when (state) {
            is LoginResult.InProgress -> activityContext.updateProgress("Logging in")
            is LoginResult.Success -> activityContext.killProgress()
            is LoginResult.Failure -> activityContext.killProgress()
        }
    }
}