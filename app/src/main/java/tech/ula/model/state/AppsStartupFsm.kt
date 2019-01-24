package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import com.crashlytics.android.Crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            is CheckAppServicePreference -> currentState is AppsFilesystemHasCredentials
            is SubmitAppServicePreference -> currentState is AppRequiresServiceTypePreference
            is CopyAppScriptToFilesystem -> currentState is AppHasServiceTypePreferenceSet
            is SyncDatabaseEntries -> currentState is AppScriptCopySucceeded
            is ResetAppState -> true
        }
    }

    suspend fun submitEvent(event: AppsStartupEvent) {
        Crashlytics.setString("Last submitted apps fsm event", "$event")
        Crashlytics.setString("State during apps fsm event submission", "${state.value}")
        if (!transitionIsAcceptable(event)) {
            state.postValue(IncorrectAppTransition(event, state.value!!))
            return
        }
        return when (event) {
            is AppSelected -> fetchDatabaseEntries(event.app)
            is CheckAppsFilesystemCredentials -> checkAppsFilesystemCredentials(event.appsFilesystem)
            is SubmitAppsFilesystemCredentials -> {
                setAppsFilesystemCredentials(event.filesystem, event.username, event.password, event.vncPassword)
            }
            is CheckAppServicePreference -> checkAppServiceTypePreference(event.app)
            is SubmitAppServicePreference -> { setAppServicePreference(event.app, event.serviceTypePreference) }
            is CopyAppScriptToFilesystem -> { copyAppScriptToFilesystem(event.app, event.filesystem) }
            is SyncDatabaseEntries -> updateAppSession(event.app, event.session, event.filesystem)
            is ResetAppState -> state.postValue(WaitingForAppSelection)
        }
    }

    private suspend fun fetchDatabaseEntries(app: App) {
        state.postValue(FetchingDatabaseEntries)
        try {
            val appsFilesystem = findAppsFilesystem(app)
            val appSession = findAppSession(app, appsFilesystem.id)
            state.postValue(DatabaseEntriesFetched(appsFilesystem, appSession))
        } catch (err: Exception) {
            state.postValue(DatabaseEntriesFetchFailed)
        }
    }

    private fun checkAppsFilesystemCredentials(appsFilesystem: Filesystem) {
        val credentialsAreSet = appsFilesystem.defaultUsername.isNotEmpty() &&
                appsFilesystem.defaultPassword.isNotEmpty() &&
                appsFilesystem.defaultVncPassword.isNotEmpty()
        if (credentialsAreSet) {
            state.postValue(AppsFilesystemHasCredentials)
            return
        }
        state.postValue(AppsFilesystemRequiresCredentials(appsFilesystem))
    }

    private fun checkAppServiceTypePreference(app: App) {
        val serviceTypePreference = appsPreferences.getAppServiceTypePreference(app)
        if (serviceTypePreference == PreferenceHasNotBeenSelected) {
            state.postValue(AppRequiresServiceTypePreference)
            return
        }
        state.postValue(AppHasServiceTypePreferenceSet)
    }

    private suspend fun copyAppScriptToFilesystem(app: App, filesystem: Filesystem) {
        state.postValue(CopyingAppScript)
        try {
            withContext(Dispatchers.IO) {
                filesystemUtility.moveAppScriptToRequiredLocation(app.name, filesystem)
            }
            state.postValue(AppScriptCopySucceeded)
        } catch (err: Exception) {
            state.postValue(AppScriptCopyFailed)
        }
    }

    private fun setAppServicePreference(app: App, serviceTypePreference: AppServiceTypePreference) {
        appsPreferences.setAppServiceTypePreference(app.name, serviceTypePreference)
        state.postValue(AppHasServiceTypePreferenceSet)
    }

    @Throws(NoSuchElementException::class) // If second database call fails
    private suspend fun findAppsFilesystem(app: App): Filesystem = withContext(Dispatchers.IO) {
        val potentialAppFilesystem = filesystemDao.findAppsFilesystemByType(app.filesystemRequired)

        if (potentialAppFilesystem.isEmpty()) {
            val deviceArchitecture = buildWrapper.getArchType()
            val fsToInsert = Filesystem(0, name = "apps", archType = deviceArchitecture,
                    distributionType = app.filesystemRequired, isAppsFilesystem = true)
            filesystemDao.insertFilesystem(fsToInsert)
        }

        return@withContext filesystemDao.findAppsFilesystemByType(app.filesystemRequired).first()
    }

    @Throws(NoSuchElementException::class) // If second database call fails
    private suspend fun findAppSession(app: App, filesystemId: Long): Session = withContext(Dispatchers.IO) {
        val potentialAppSession = sessionDao.findAppsSession(app.name)

        if (potentialAppSession.isEmpty()) {
            val sessionToInsert = Session(id = 0, name = app.name, filesystemId = filesystemId, isAppsSession = true)
            sessionDao.insertSession(sessionToInsert)
        }

        return@withContext sessionDao.findAppsSession(app.name).first()
    }

    private suspend fun setAppsFilesystemCredentials(filesystem: Filesystem, username: String, password: String, vncPassword: String) {
        filesystem.defaultUsername = username
        filesystem.defaultPassword = password
        filesystem.defaultVncPassword = vncPassword
        withContext(Dispatchers.IO) { filesystemDao.updateFilesystem(filesystem) }
        state.postValue(AppsFilesystemHasCredentials)
    }

    private suspend fun updateAppSession(app: App, appSession: Session, appsFilesystem: Filesystem) {
        state.postValue(SyncingDatabaseEntries)
        val serviceTypePreference = appsPreferences.getAppServiceTypePreference(app)
        appSession.filesystemId = appsFilesystem.id
        appSession.filesystemName = appsFilesystem.name
        appSession.serviceType = serviceTypePreference.toString()
        appSession.port = if (serviceTypePreference is SshTypePreference) 2022 else 51
        appSession.username = appsFilesystem.defaultUsername
        appSession.password = appsFilesystem.defaultPassword
        appSession.vncPassword = appsFilesystem.defaultVncPassword
        withContext(Dispatchers.IO) { sessionDao.updateSession(appSession) }
        state.postValue(AppDatabaseEntriesSynced(app, appSession, appsFilesystem))
    }
}

sealed class AppsStartupState
data class IncorrectAppTransition(val event: AppsStartupEvent, val state: AppsStartupState) : AppsStartupState()
object WaitingForAppSelection : AppsStartupState()
object FetchingDatabaseEntries : AppsStartupState()
data class DatabaseEntriesFetched(val appsFilesystem: Filesystem, val appSession: Session) : AppsStartupState()
object DatabaseEntriesFetchFailed : AppsStartupState()
object AppsFilesystemHasCredentials : AppsStartupState()
data class AppsFilesystemRequiresCredentials(val appsFilesystem: Filesystem) : AppsStartupState()
object AppHasServiceTypePreferenceSet : AppsStartupState()
object AppRequiresServiceTypePreference : AppsStartupState()
object CopyingAppScript : AppsStartupState()
object AppScriptCopySucceeded : AppsStartupState()
object AppScriptCopyFailed : AppsStartupState()
object SyncingDatabaseEntries : AppsStartupState()
data class AppDatabaseEntriesSynced(val app: App, val session: Session, val filesystem: Filesystem) : AppsStartupState()

sealed class AppsStartupEvent
data class AppSelected(val app: App) : AppsStartupEvent()
data class CheckAppsFilesystemCredentials(val appsFilesystem: Filesystem) : AppsStartupEvent()
data class SubmitAppsFilesystemCredentials(val filesystem: Filesystem, val username: String, val password: String, val vncPassword: String) : AppsStartupEvent()
data class CheckAppServicePreference(val app: App) : AppsStartupEvent()
data class SubmitAppServicePreference(val app: App, val serviceTypePreference: AppServiceTypePreference) : AppsStartupEvent()
data class CopyAppScriptToFilesystem(val app: App, val filesystem: Filesystem) : AppsStartupEvent()
data class SyncDatabaseEntries(val app: App, val session: Session, val filesystem: Filesystem) : AppsStartupEvent()
object ResetAppState : AppsStartupEvent()