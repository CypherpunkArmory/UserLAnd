package tech.ula.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.navigation.fragment.navArgs
import kotlinx.android.synthetic.main.frag_app_details.* // ktlint-disable no-wildcard-imports
import tech.ula.R
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import tech.ula.utils.preferences.AppsPreferences
import tech.ula.utils.preferences.SshTypePreference
import tech.ula.utils.preferences.VncTypePreference
import tech.ula.utils.preferences.XsdlTypePreference

class AppDetailsFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val args: AppDetailsFragmentArgs by navArgs()
    private val app by lazy { args.app!! }

    private val appsPreferences by lazy {
        AppsPreferences(activityContext)
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
        if (!app.supportsGui) {
            apps_vnc_preference.isEnabled = false
            apps_xsdl_preference.isEnabled = false
        } else if (!app.supportsCli && app.supportsGui)
            apps_ssh_preference.isEnabled = false

        val xsdlPreferenceButton = view.find<RadioButton>(R.id.apps_xsdl_preference)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
            xsdlPreferenceButton.isEnabled = false
            xsdlPreferenceButton.alpha = 0.5f

            val xsdlSupportedText = view.find<TextView>(R.id.text_xsdl_version_supported_description)
            xsdlSupportedText.visibility = View.VISIBLE
        }
    }

    private fun setupPreferredServiceTypeRadioGroup() {
        when (appsPreferences.getAppServiceTypePreference(app)) {
            is SshTypePreference -> apps_service_type_preferences.check(R.id.apps_ssh_preference)
            is VncTypePreference -> apps_service_type_preferences.check(R.id.apps_vnc_preference)
            is XsdlTypePreference -> apps_service_type_preferences.check(R.id.apps_xsdl_preference)
        }

        apps_service_type_preferences.setOnCheckedChangeListener { _, checkedId ->
            val selectedServiceType = when (checkedId) {
                R.id.apps_ssh_preference -> SshTypePreference
                R.id.apps_vnc_preference -> VncTypePreference
                R.id.apps_xsdl_preference -> XsdlTypePreference
                else -> SshTypePreference
            }
            appsPreferences.setAppServiceTypePreference(app.name, selectedServiceType)
        }
    }
}