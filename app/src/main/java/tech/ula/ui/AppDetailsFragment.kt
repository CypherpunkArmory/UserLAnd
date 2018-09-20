package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.frag_app_details.*
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.utils.AppsListPreferences

class AppDetailsFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val app: App by lazy {
        arguments?.getParcelable("app") as App
    }

    private val appListPreferences by lazy {
        AppsListPreferences(activityContext.getSharedPreferences("appLists", Context.MODE_PRIVATE))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_details, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!

        apps_title.text = app.name
        apps_icon.setImageResource(R.drawable.octave)

        setupPreferredClientRadioGroup()
    }

    fun setupPreferredClientRadioGroup() {
        val appClientPreference = appListPreferences.getAppClientPreference(app.name)
        if (appClientPreference == "SSH") {
            apps_client_preference.check(R.id.apps_ssh_preference)
        } else {
            apps_client_preference.check(R.id.apps_vnc_preference)
        }

        apps_client_preference.setOnCheckedChangeListener { _, checkedId ->
            val selectedClient = if (R.id.apps_ssh_preference == checkedId) "SSH" else "VNC"
            appListPreferences.setAppClientPreference(app.name, selectedClient)
        }
    }
}