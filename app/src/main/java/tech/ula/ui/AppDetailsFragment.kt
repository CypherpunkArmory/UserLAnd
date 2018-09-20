package tech.ula.ui

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import tech.ula.R
import tech.ula.model.entities.App

class AppDetailsFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val app: App by lazy {
        arguments?.getParcelable("app") as App
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_details, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appsTitle = view.findViewById<TextView>(R.id.apps_title)
        val appsIcon = view.findViewById<ImageView>(R.id.apps_icon)
        appsTitle.text = app.name
        appsIcon.setImageResource(R.drawable.octave)

        val clientPreferences = view.findViewById<RadioGroup>(R.id.apps_client_preference)
        clientPreferences.setOnCheckedChangeListener { _, checkedId ->
            var text = "You selected: "
            text += if (R.id.apps_ssh_preference == checkedId) "SSH" else "VNC"
            Toast.makeText(activityContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}