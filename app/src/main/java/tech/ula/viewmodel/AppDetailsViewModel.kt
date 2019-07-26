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

sealed class AppDetailsEvent {
    data class SubmitApp(val app: App) : AppDetailsEvent()
    data class ServiceTypeChanged(@IdRes val selectedButton: Int, val app: App) : AppDetailsEvent()
}

class AppDetailsViewModel(context: Context, private val buildVersion: Int) : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val sessionDao = UlaDatabase.getInstance(context).sessionDao()

    private val appDetails = AppDetails(context.filesDir.path, context.resources)
    val viewState = MutableLiveData<AppDetailsViewState>()

    fun submitEvent(event: AppDetailsEvent) {
        return when (event) {
            is AppDetailsEvent.SubmitApp -> constructView(event.app)
            is AppDetailsEvent.ServiceTypeChanged -> handleServiceTypeChanged(event)
        }
    }

    private fun constructView(app: App) {
        this.launch {
            val appSession = getAppSession(app)
            viewState.postValue(buildViewState(app, appSession))
        }
    }

    private suspend fun getAppSession(app: App): Session? = withContext(Dispatchers.IO) {
        val listOfPotentialSessions = sessionDao.findAppsSession(app.name)
        return@withContext if (listOfPotentialSessions.isEmpty()) null
        else listOfPotentialSessions.first()
    }

    private fun buildViewState(app: App, appSession: Session?): AppDetailsViewState {
        val enableRadioButtons = radioButtonsShouldBeEnabled(appSession)

        val appIconUri = appDetails.findIconUri(app.name)
        val appTitle = app.name
        val appDescription = appDetails.findAppDescription(app.name)
        val sshEnabled = app.supportsCli && enableRadioButtons
        val vncEnabled = app.supportsGui && enableRadioButtons
        val xsdlEnabled = app.supportsGui && buildVersion <= Build.VERSION_CODES.O_MR1 && enableRadioButtons
        val describeStateHintEnabled = getStateHintEnabled(appSession)
        val describeStateText = getStateDescription(appSession)
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

    private fun handleServiceTypeChanged(event: AppDetailsEvent.ServiceTypeChanged) {
        this.launch {
            val appSession = getAppSession(event.app)
            val selectedServiceType = when (event.selectedButton) {
                R.id.apps_ssh_preference -> ServiceType.Ssh
                R.id.apps_vnc_preference -> ServiceType.Vnc
                R.id.apps_xsdl_preference -> ServiceType.Xsdl
                else -> ServiceType.Unselected
            }

            if (appSession == null) return@launch

            appSession.serviceType = selectedServiceType
            appSession.port = if (selectedServiceType == ServiceType.Vnc) 51 else 2022
            this.launch {
                withContext(Dispatchers.IO) {
                    sessionDao.updateSession(appSession)
                }
            }
        }
    }

    private fun getStateHintEnabled(appSession: Session?): Boolean {
        return !radioButtonsShouldBeEnabled(appSession)
    }

    @StringRes
    private fun getStateDescription(appSession: Session?): Int? {
        return when {
            appSession?.serviceType == ServiceType.Unselected || appSession == null -> {
                R.string.info_finish_app_setup
            }
            appSession.active  -> {
                R.string.info_stop_app
            }
            else -> {
                null
            }
        }
    }

    private fun radioButtonsShouldBeEnabled(appSession: Session?): Boolean {
        val serviceType = appSession?.serviceType ?: ServiceType.Unselected
        val isNotActive = appSession?.active == false
        return appSession != null &&
                isNotActive &&
                serviceType != ServiceType.Unselected
    }
}

class AppDetailsViewmodelFactory(private val context: Context, private val buildVersion: Int) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppDetailsViewModel(context, buildVersion) as T
    }
}