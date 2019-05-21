package tech.ula.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.frag_cloud_demo.*
import kotlinx.android.synthetic.main.frag_cloud_demo.view.*
import tech.ula.R

class CloudDemoFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_cloud_demo, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        cloud_demo_login_button.setOnClickListener { handleLoginClick() }
        cloud_demo_connect_button.setOnClickListener { handleConnectClick() }
    }

    private fun handleLoginClick() {
        Toast.makeText(activity, "login", Toast.LENGTH_LONG).show()
    }

    private fun handleConnectClick() {
        Toast.makeText(activity, "connect", Toast.LENGTH_LONG).show()
    }
}