package tech.ula.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.RadioButton
import androidx.annotation.IdRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.frag_app_details.view.*
import kotlinx.coroutines.*
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.ServiceTypeConverter
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
        val finishSetupHelpEnabled: Boolean,
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
        } }
    }

    private val appDetails = AppDetails(context.filesDir.path, context.resources)
    val viewState = MutableLiveData<AppDetailsViewState>().apply {
        postValue(buildViewState())
    }

    private fun buildViewState(): AppDetailsViewState {
        val appIconUri = appDetails.findIconUri(app.name)
        val appTitle = app.name
        val appDescription = appDetails.findAppDescription(app.name)
        val sshEnabled = app.supportsCli && appSession != null
        val vncEnabled = app.supportsGui && appSession != null
        val xsdlEnabled = app.supportsGui && buildVersion <= Build.VERSION_CODES.O_MR1 && appSession != null
        val finishSetupHelpEnabled = appSession == null
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
                finishSetupHelpEnabled,
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
}

class AppDetailsViewmodelFactory(private val context: Context, private val buildVersion: Int, private val app: App) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppDetailsViewModel(context, buildVersion, app) as T
    }
}