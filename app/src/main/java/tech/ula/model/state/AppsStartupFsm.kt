package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class AppsStartupFsm(
    ulaDatabase: UlaDatabase,
    private val appsPreferences: AppsPreferences,
    private val filesystemUtility: FilesystemUtility,
    private val buildWrapper: BuildWrapper = BuildWrapper()
) {

    private val sessionDao = ulaDatabase.sessionDao()
    private val filesystemDao = ulaDatabase.filesystemDao()

    private val state = MutableLiveData<AppsStartupState>().apply { postValue(WaitingForAppSelection) }

    fun getState(): LiveData<AppsStartupState> {
        return state
    }

    internal fun setState(newState: AppsStartupState) {
        state.postValue(newState)
    }

    fun transitionIsAcceptable(event: AppsStartupEvent): Boolean {
        val currentState = state.value!!
        return when (event) {
            is AppSelected -> currentState is WaitingForAppSelection
            is CheckAppsFilesystemCredentials -> currentState is DatabaseEntriesFetched
            is SubmitAppsFilesystemCredentials -> currentState is AppsFilesystemRequiresCredentials
            is CheckAppsServicePreference -> currentState is AppsFilesystemHasCredentials
            is SubmitAppServicePreference -> currentState is AppRequiresServiceTypePreference
            is CopyAppScript -> currentState is AppHasServiceTypePreferenceSet
            is SyncDatabaseEntries -> currentState is AppScriptCopySucceeded
            is ResetAppState -> true
        }
    }

    suspend fun submitEvent(event: AppsStartupEvent) {
        if (!transitionIsAcceptable(event)) {
            state.postValue(IncorrectAppTransition(event, state.value!!))
            return
        }
        return when (event) {
            is AppSelected -> appWasSelected(event.app)
            is SubmitAppsFilesystemCredentials -> {
                setAppsFilesystemCredentials(event.filesystem, event.username, event.password, event.vncPassword)
                appWasSelected(event.app)
            }
            is SubmitAppServicePreference -> {
                val lastSelectedApp = event.app
                setAppServicePreference(lastSelectedApp.name, event.serviceTypePreference)
                appWasSelected(lastSelectedApp)
            }
        }
    }

    private suspend fun appWasSelected(app: App) {
        val appsFilesystem = findAppsFilesystem(app)
        if (appsFilesystemRequiresCredentials(appsFilesystem)) {
            state.postValue(AppsFilesystemRequiresCredentials(app, appsFilesystem))
            return
        }

        val preferredServiceType = appsPreferences.getAppServiceTypePreference(app)
        if (preferredServiceType is PreferenceHasNotBeenSelected) {
            state.postValue(AppRequiresServiceTypePreference(app))
            return
        }

        try {
            filesystemUtility.moveAppScriptToRequiredLocation(app.name, appsFilesystem)
        } catch(err: Exception) {
            state.postValue(CopyingScriptFailed(app))
        }

        val appSession = findAppSession(app)
//        updateAppSession(appSession, preferredServiceType, appsFilesystem)

        state.postValue(AppCanBeStarted(appSession, appsFilesystem))
    }

    private fun setAppServicePreference(appName: String, serviceTypePreference: AppServiceTypePreference) {
        appsPreferences.setAppServiceTypePreference(appName, serviceTypePreference)
    }

    private suspend fun findAppsFilesystem(app: App): Filesystem {
        val potentialAppFilesystem = withContext(Dispatchers.IO) {
            filesystemDao.findAppsFilesystemByType(app.filesystemRequired)
        }

        if (potentialAppFilesystem.isEmpty()) {
            val deviceArchitecture = buildWrapper.getArchType()
            val fsToInsert = Filesystem(0, name = "apps", archType = deviceArchitecture,
                    distributionType = app.filesystemRequired, isAppsFilesystem = true)
            withContext(Dispatchers.IO) { filesystemDao.insertFilesystem(fsToInsert) }
        }

        return withContext(Dispatchers.IO) { filesystemDao.findAppsFilesystemByType(app.filesystemRequired).first() }
    }

    private suspend fun findAppSession(app: App): Session {
        val potentialAppSession = withContext(Dispatchers.IO) {
            sessionDao.findAppsSession(app.name)
        }

        if (potentialAppSession.isEmpty()) {
            val sessionToInsert = Session(id = 0, name = app.name, filesystemId = -1, isAppsSession = true)
            withContext(Dispatchers.IO) { sessionDao.insertSession(sessionToInsert) }
        }

        return withContext(Dispatchers.IO) { sessionDao.findAppsSession(app.name).first() }
    }

    private fun appsFilesystemRequiresCredentials(appsFilesystem: Filesystem): Boolean {
        return appsFilesystem.defaultUsername.isEmpty() ||
                appsFilesystem.defaultPassword.isEmpty() ||
                appsFilesystem.defaultVncPassword.isEmpty()
    }

    private suspend fun setAppsFilesystemCredentials(filesystem: Filesystem, username: String, password: String, vncPassword: String) {
        filesystem.defaultUsername = username
        filesystem.defaultPassword = password
        filesystem.defaultVncPassword = vncPassword
        withContext(Dispatchers.IO) { filesystemDao.updateFilesystem(filesystem) }
    }

    private suspend fun updateAppSession(appSession: Session,
                                         serviceTypePreference: AppServiceTypePreference,
                                         appsFilesystem: Filesystem){
        state.postValue(SyncingDatabaseEntries)
        appSession.filesystemId = appsFilesystem.id
        appSession.filesystemName = appsFilesystem.name
        appSession.serviceType = serviceTypePreference.toString()
        appSession.port = if (serviceTypePreference is SshTypePreference) 2022 else 51
        appSession.username = appsFilesystem.defaultUsername
        appSession.password = appsFilesystem.defaultPassword
        appSession.vncPassword = appsFilesystem.defaultVncPassword
        withContext(Dispatchers.IO) { sessionDao.updateSession(appSession) }
    }
}

sealed class AppsStartupState
data class IncorrectAppTransition(val event: AppsStartupEvent, val state: AppsStartupState)
object WaitingForAppSelection : AppsStartupState()
object FetchingDatabaseEntries : AppsStartupState()
object DatabaseEntriesFetched : AppsStartupState()
object DatabaseEntriesFetchFailed : AppsStartupState()
object AppsFilesystemHasCredentials : AppsStartupState()
data class AppsFilesystemRequiresCredentials(val app: App, val appsFilesystem: Filesystem) : AppsStartupState()
object CheckingAppServiceTypePreference : AppsStartupState()
object AppHasServiceTypePreferenceSet : AppsStartupState()
data class AppRequiresServiceTypePreference(val app: App) : AppsStartupState()
object CopyingAppScript : AppsStartupState()
object AppScriptCopySucceeded : AppsStartupState()
data class AppScriptCopyFailed(val app: App) : AppsStartupState()
object SyncingDatabaseEntries : AppsStartupState()
object DatabaseSyncSucceeded : AppsStartupState()
object DatabaseSyncFailed : AppsStartupState()
object AppDatabaseEntriesSynced : AppsStartupState()

sealed class AppsStartupEvent
data class AppSelected(val app: App) : AppsStartupEvent()
object CheckAppsFilesystemCredentials : AppsStartupEvent()
data class SubmitAppsFilesystemCredentials(val app: App, val filesystem: Filesystem, val username: String, val password: String, val vncPassword: String) : AppsStartupEvent()
object CheckAppsServicePreference : AppsStartupEvent()
data class SubmitAppServicePreference(val app: App, val serviceTypePreference: AppServiceTypePreference) : AppsStartupEvent()
object CopyAppScript : AppsStartupEvent()
object SyncDatabaseEntries : AppsStartupEvent()
object ResetAppState : AppsStartupEvent()