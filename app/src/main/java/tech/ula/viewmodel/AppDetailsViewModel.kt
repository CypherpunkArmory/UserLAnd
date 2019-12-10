package tech.ula.viewmodel

import android.net.Uri
import android.os.Build
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.R
import tech.ula.model.daos.SessionDao
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.App
import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.ServiceLocation
import tech.ula.model.entities.Session
import tech.ula.utils.AppDetails
import kotlin.coroutines.CoroutineContext

data class AppDetailsViewState(
    val appIconUri: Uri,
    val appTitle: String,
    val appDescription: String,
    val sshEnabled: Boolean,
    val vncEnabled: Boolean,
    val xsdlEnabled: Boolean,
    val localEnabled: Boolean,
    val remoteEnabled: Boolean,
    val describeStateHintEnabled: Boolean,
    @StringRes val describeStateText: Int?,
    @IdRes val selectedServiceTypeButton: Int?,
    @IdRes val selectedServiceLocationButton: Int?
)

sealed class AppDetailsEvent {
    data class SubmitApp(val app: App) : AppDetailsEvent()
    data class ServiceTypeChanged(@IdRes val selectedButton: Int, val app: App) : AppDetailsEvent()
    data class ServiceLocationChanged(@IdRes val selectedButton: Int, val app: App) : AppDetailsEvent()
}

class AppDetailsViewModel(private val appsDao: AppsDao, private val sessionDao: SessionDao, private val appDetails: AppDetails, private val buildVersion: Int) : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    val viewState = MutableLiveData<AppDetailsViewState>()

    fun submitEvent(event: AppDetailsEvent, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        return@launch when (event) {
            is AppDetailsEvent.SubmitApp -> constructView(event.app)
            is AppDetailsEvent.ServiceTypeChanged -> handleServiceTypeChanged(event)
            is AppDetailsEvent.ServiceLocationChanged -> handleServiceLocationChanged(event)
        }
    }

    private suspend fun constructView(app: App) {
        val appSession = getAppSession(app)
        viewState.postValue(buildViewState(app, appSession))
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
        val vncEnabled = app.supportsGui && enableRadioButtons && (app.serviceLocation != ServiceLocation.Remote)
        val xsdlEnabled = app.supportsGui && buildVersion <= Build.VERSION_CODES.O_MR1 && enableRadioButtons && (app.serviceLocation != ServiceLocation.Remote)
        val localEnabled = app.supportsLocal && enableRadioButtons
        val remoteEnabled = app.supportsRemote && enableRadioButtons

        val describeStateHintEnabled = getStateHintEnabled(appSession)
        val describeStateText = getStateDescription(appSession)

        val selectedServiceTypeButton = when (app.serviceType) {
            ServiceType.Ssh -> R.id.apps_ssh_preference
            ServiceType.Vnc -> R.id.apps_vnc_preference
            ServiceType.Xsdl -> R.id.apps_xsdl_preference
            else -> null
        }

        val selectedServiceLocationButton = when (app.serviceLocation) {
            ServiceLocation.Local -> R.id.apps_local_preference
            ServiceLocation.Remote -> R.id.apps_remote_preference
            else -> null
        }

        return AppDetailsViewState(
                appIconUri,
                appTitle,
                appDescription,
                sshEnabled,
                vncEnabled,
                xsdlEnabled,
                localEnabled,
                remoteEnabled,
                describeStateHintEnabled,
                describeStateText,
                selectedServiceTypeButton,
                selectedServiceLocationButton
        )
    }

    private fun handleServiceTypeChanged(event: AppDetailsEvent.ServiceTypeChanged) {
        this.launch {
            val selectedServiceType = when (event.selectedButton) {
                R.id.apps_ssh_preference -> ServiceType.Ssh
                R.id.apps_vnc_preference -> ServiceType.Vnc
                R.id.apps_xsdl_preference -> ServiceType.Xsdl
                else -> ServiceType.Unselected
            }

            event.app.serviceType = selectedServiceType
            this.launch {
                withContext(Dispatchers.IO) {
                    appsDao.updateApp(event.app)
                }
            }
        }
    }

    private suspend fun handleServiceLocationChanged(event: AppDetailsEvent.ServiceLocationChanged) {
        this.launch {
            val selectedServiceLocation = when (event.selectedButton) {
                R.id.apps_local_preference -> ServiceLocation.Local
                R.id.apps_remote_preference -> ServiceLocation.Remote
                else -> ServiceLocation.Unselected
            }

            event.app.serviceLocation = selectedServiceLocation
            if (event.app.serviceLocation == ServiceLocation.Remote)
                event.app.serviceType = ServiceType.Ssh

            this.launch {
                withContext(Dispatchers.IO) {
                    appsDao.updateApp(event.app)
                }
            }

            constructView(event.app)
        }
    }

    private fun getStateHintEnabled(appSession: Session?): Boolean {
        return !radioButtonsShouldBeEnabled(appSession)
    }

    @StringRes
    private fun getStateDescription(appSession: Session?): Int? {
        return when {
            appSession == null -> null
            appSession.active -> {
                R.string.info_stop_app
            }
            else -> {
                null
            }
        }
    }

    private fun radioButtonsShouldBeEnabled(appSession: Session?): Boolean {
        val isNotActive = appSession?.active == false
        return appSession == null || isNotActive
    }
}

class AppDetailsViewmodelFactory(private val appsDao: AppsDao, private val sessionDao: SessionDao, private val appDetails: AppDetails, private val buildVersion: Int) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppDetailsViewModel(appsDao, sessionDao, appDetails, buildVersion) as T
    }
}