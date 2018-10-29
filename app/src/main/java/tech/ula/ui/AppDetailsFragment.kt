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
import tech.ula.utils.AppsPreferences
import tech.ula.utils.LocalFileLocator
import tech.ula.utils.SshTypePreference
import tech.ula.utils.VncTypePreference

class AppDetailsFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val app: App by lazy {
        arguments?.getParcelable("app") as App
    }

    private val appsPreferences by lazy {
        AppsPreferences(activityContext.getSharedPreferences("apps", Context.MODE_PRIVATE))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_details, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!
        val localFileLocator = LocalFileLocator(activityContext.filesDir.path, activityContext.resources)

        apps_icon.setImageURI(localFileLocator.findIconUri(app.name))
        apps_title.text = app.name
        apps_description.text = (localFileLocator.findAppDescription(app.name))

        setupPreferredServiceTypeRadioGroup()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!(app.supportsGui && app.supportsCli)) {
            apps_vnc_preference.isEnabled = false
            apps_ssh_preference.isEnabled = false
            return
        }
    }

    private fun setupPreferredServiceTypeRadioGroup() {
        val appServiceTypePreference = appsPreferences.getAppServiceTypePreference(app.name)
        if (appServiceTypePreference == SshTypePreference) {
            apps_service_type_preferences.check(R.id.apps_ssh_preference)
        } else {
            apps_service_type_preferences.check(R.id.apps_vnc_preference)
        }

        apps_service_type_preferences.setOnCheckedChangeListener { _, checkedId ->
            val selectedServiceType = when (R.id.apps_ssh_preference) {
                checkedId -> SshTypePreference
                else -> VncTypePreference
            }

            appsPreferences.setAppServiceTypePreference(app.name, selectedServiceType)
        }
    }
}