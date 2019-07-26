package tech.ula.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.frag_app_details.view.*
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.AppDetails
import kotlin.coroutines.CoroutineContext

data class AppDetailsViewState(
    val appIconUri: Uri,
    val appTitle: String,
    val appDescription: String,
    val sshEnabled: Boolean,
    val vncEnabled: Boolean,
    val xsdlEnabled: Boolean,
    val describeStateHintEnabled: Boolean,
    @StringRes val describeStateText: Int?,
    @IdRes val selectedServiceTypeButton: Int?
)

sealed class AppDetailsUserEvent {
    data class ServiceTypeChanged(@IdRes val selectedButton: Int) : AppDetailsUserEvent()
}

class AppDetailsViewModel(context: Context, private val buildVersion: Int, private val app: App) : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val sessionDao = UlaDatabase.getInstance(context).sessionDao()
    private var appSession: Session? = null

    init {
        this.launch { withContext(Dispatchers.IO) {
            val listOfPotentialSessions = sessionDao.findAppsSession(app.name)
            appSession = if (listOfPotentialSessions.isEmpty()) null
            else listOfPotentialSessions.first()
            viewState.postValue(buildViewState())
        } }
    }

    private val appDetails = AppDetails(context.filesDir.path, context.resources)
    val viewState = MutableLiveData<AppDetailsViewState>().apply {
        postValue(buildViewState())
    }

    private fun buildViewState(): AppDetailsViewState {
        val enableRadioButtons = radioButtonsShouldBeEnabled()

        val appIconUri = appDetails.findIconUri(app.name)
        val appTitle = app.name
        val appDescription = appDetails.findAppDescription(app.name)
        val sshEnabled = app.supportsCli && enableRadioButtons
        val vncEnabled = app.supportsGui && enableRadioButtons
        val xsdlEnabled = app.supportsGui && buildVersion <= Build.VERSION_CODES.O_MR1 && enableRadioButtons
        val describeStateHintEnabled = appSession == null || appSession?.active == true
        val describeStateText = getStateDescription()
        val selectedServiceTypeButton = when (appSession?.serviceType) {
            ServiceType.Ssh -> R.id.apps_ssh_preference
            ServiceType.Vnc -> R.id.apps_vnc_preference
            ServiceType.Xsdl -> R.id.apps_xsdl_preference
            else -> null
        }
        return AppDetailsViewState(
                appIconUri,
                appTitle,
                appDescription,
                sshEnabled,
                vncEnabled,
                xsdlEnabled,
                describeStateHintEnabled,
                describeStateText,
                selectedServiceTypeButton
        )
    }

    fun submitEvent(event: AppDetailsUserEvent) {
        return when (event) {
            is AppDetailsUserEvent.ServiceTypeChanged -> handleServiceTypeChanged(event)
        }
    }

    private fun handleServiceTypeChanged(event: AppDetailsUserEvent.ServiceTypeChanged) {
        val selectedServiceType = when (event.selectedButton) {
            R.id.apps_ssh_preference -> ServiceType.Ssh
            R.id.apps_vnc_preference -> ServiceType.Vnc
            R.id.apps_xsdl_preference -> ServiceType.Xsdl
            else -> ServiceType.Unselected
        }

        if (appSession == null) return

        appSession!!.serviceType = selectedServiceType
        this.launch { withContext(Dispatchers.IO) {
                sessionDao.updateSession(appSession!!)
            }
        }
    }

    @StringRes
    private fun getStateDescription(): Int? {
        return when {
            appSession == null -> R.string.info_finish_app_setup
            appSession?.active == true -> R.string.info_stop_app
            else -> null
        }
    }

    private fun radioButtonsShouldBeEnabled(): Boolean {
        val serviceType = appSession?.serviceType ?: ServiceType.Unselected
        return appSession != null &&
                appSession?.active == false &&
                serviceType != ServiceType.Unselected
    }
}

class AppDetailsViewmodelFactory(private val context: Context, private val buildVersion: Int, private val app: App) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppDetailsViewModel(context, buildVersion, app) as T
    }
}