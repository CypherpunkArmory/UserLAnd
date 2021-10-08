package tech.ula.viewmodel

import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.R
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.ServiceType
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
    val describeStateHintEnabled: Boolean,
    @StringRes val describeStateText: Int?,
    @IdRes val selectedServiceTypeButton: Int?,
    val autoStartEnabled: Boolean
)

sealed class AppDetailsEvent {
    data class SubmitApp(val app: App) : AppDetailsEvent()
    data class ServiceTypeChanged(@IdRes val selectedButton: Int, val app: App) : AppDetailsEvent()
    data class AutoStartChanged(val autoStartEnabled: Boolean, val app: App) : AppDetailsEvent()
}

class AppDetailsViewModel(private val sessionDao: SessionDao, private val appDetails: AppDetails, private val buildVersion: Int, private val prefs: SharedPreferences) : ViewModel(), CoroutineScope {    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    val viewState = MutableLiveData<AppDetailsViewState>()

    fun submitEvent(event: AppDetailsEvent, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        return@launch when (event) {
            is AppDetailsEvent.SubmitApp -> constructView(event.app)
            is AppDetailsEvent.ServiceTypeChanged -> handleServiceTypeChanged(event)
            is AppDetailsEvent.AutoStartChanged -> handleAutoStartChanged(event)
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

        var autoAppEnabled = false
        val gson = Gson()
        val json = prefs.getString("AutoApp", " ")
        if (json != null)
            if (json.compareTo(" ") != 0) {
                val autoApp = gson.fromJson(json, App::class.java)
                if (autoApp.name.compareTo(appTitle) == 0)
                    autoAppEnabled = true
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
                selectedServiceTypeButton,
                autoAppEnabled
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
            this.launch {
                withContext(Dispatchers.IO) {
                    sessionDao.updateSession(appSession)
                }
            }
        }
    }

    private fun handleAutoStartChanged(event: AppDetailsEvent.AutoStartChanged) {
        this.launch {
            if (event.autoStartEnabled)
                with(prefs.edit()) {
                    val gson = Gson()
                    val json= gson.toJson(event.app)
                    putString("AutoApp", json)
                    apply()
                }
            else
                with(prefs.edit()) {
                    remove("AutoApp")
                    apply()
                }
        }
    }

    private fun getStateHintEnabled(appSession: Session?): Boolean {
        return !radioButtonsShouldBeEnabled(appSession)
    }

    @StringRes
    private fun getStateDescription(appSession: Session?): Int? {
        return when {
            appSession == null || appSession.serviceType == ServiceType.Unselected -> {
                R.string.info_finish_app_setup
            }
            appSession.active -> {
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

class AppDetailsViewmodelFactory(private val sessionDao: SessionDao, private val appDetails: AppDetails, private val buildVersion: Int, private val prefs: SharedPreferences) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppDetailsViewModel(sessionDao, appDetails, buildVersion, prefs) as T
    }
}