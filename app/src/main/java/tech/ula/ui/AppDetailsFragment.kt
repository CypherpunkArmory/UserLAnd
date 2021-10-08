package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.navArgs
import kotlinx.android.synthetic.main.frag_app_details.* // ktlint-disable no-wildcard-imports
import tech.ula.R
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import tech.ula.viewmodel.AppDetailsEvent
import tech.ula.viewmodel.AppDetailsViewModel
import tech.ula.viewmodel.AppDetailsViewState
import tech.ula.viewmodel.AppDetailsViewmodelFactory

class AppDetailsFragment : Fragment() {

    private lateinit var activityContext: Activity

    private val args: AppDetailsFragmentArgs by navArgs()
    private val app by lazy { args.app!! }

    private val viewModel by lazy {
        val sessionDao = UlaDatabase.getInstance(activityContext).sessionDao()
        val appDetails = AppDetails(activityContext.filesDir.path, activityContext.resources)
        val buildVersion = Build.VERSION.SDK_INT
        val factory = AppDetailsViewmodelFactory(sessionDao, appDetails, buildVersion, activityContext.getSharedPreferences("apps", Context.MODE_PRIVATE))
        ViewModelProviders.of(this, factory)
                .get(AppDetailsViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_details, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activityContext = activity!!
        viewModel.viewState.observe(this, Observer<AppDetailsViewState> { viewState ->
            viewState?.let {
                handleViewStateChange(viewState)
            }
        })
        viewModel.submitEvent(AppDetailsEvent.SubmitApp(app))
        setupPreferredServiceTypeRadioGroup()
        setupAutoStartCheckbox()
    }

    private fun handleViewStateChange(viewState: AppDetailsViewState) {
        apps_icon.setImageURI(viewState.appIconUri)
        apps_title.text = viewState.appTitle
        apps_description.text = viewState.appDescription
        handleEnableRadioButtons(viewState)
        handleShowStateHint(viewState)

        if (viewState.selectedServiceTypeButton != null) {
            apps_service_type_preferences.check(viewState.selectedServiceTypeButton)
        }

        checkbox_auto_start.setChecked(viewState.autoStartEnabled)
    }

    private fun handleEnableRadioButtons(viewState: AppDetailsViewState) {
        apps_ssh_preference.isEnabled = viewState.sshEnabled
        apps_vnc_preference.isEnabled = viewState.vncEnabled

        if (viewState.xsdlEnabled) {
            apps_xsdl_preference.isEnabled = true
        } else {
            // Xsdl is unavailable on Android 9 and greater
            apps_xsdl_preference.isEnabled = false
            apps_xsdl_preference.alpha = 0.5f

            val xsdlSupportedText = view?.find<TextView>(R.id.text_xsdl_version_supported_description)
            xsdlSupportedText?.visibility = View.VISIBLE
        }
    }

    private fun handleShowStateHint(viewState: AppDetailsViewState) {
        if (viewState.describeStateHintEnabled) {
            text_describe_state.visibility = View.VISIBLE
            text_describe_state.setText(viewState.describeStateText!!)
        } else {
            text_describe_state.visibility = View.GONE
        }
    }

    private fun setupPreferredServiceTypeRadioGroup() {
        apps_service_type_preferences.setOnCheckedChangeListener { _, checkedId ->
            viewModel.submitEvent(AppDetailsEvent.ServiceTypeChanged(checkedId, app))
        }
    }

    private fun setupAutoStartCheckbox() {
        checkbox_auto_start.setOnCheckedChangeListener { _, checked ->
            viewModel.submitEvent(AppDetailsEvent.AutoStartChanged(checked, app))
        }
    }
}