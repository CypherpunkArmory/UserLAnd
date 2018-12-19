package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class AppsStartupFsm(
    ulaDatabase: UlaDatabase,
    private val appsPreferences: AppsPreferences,
    private val buildWrapper: BuildWrapper = BuildWrapper()
) {

    private val sessionDao = ulaDatabase.sessionDao()
    private val filesystemDao = ulaDatabase.filesystemDao()
    private val appsDao = ulaDatabase.appsDao()

    private val activeSessions = mutableListOf<Session>()

    private val activeAppsSessionsLiveData = appsDao.getActiveApps()
    private val activeAppsSessions = mutableListOf<App>()

    private val state = MutableLiveData<AppsStartupState>().apply { postValue(WaitingForAppSelection) }

    // TODO Is there a way to combine these observers?
    init {
        activeAppsSessionsLiveData.observeForever {
            it?.let { list ->
                activeAppsSessions.clear()
                activeAppsSessions.addAll(list)
            }
        }
    }

    fun getState(): LiveData<AppsStartupState> {
        return state
    }

    fun submitEvent(event: AppsStartupEvent) {
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

    private fun appWasSelected(app: App) = runBlocking {
        // TODO more robust check
        if (activeSessions.isNotEmpty() && !activeSessions.any { it.name == app.name }) {
            state.postValue(SingleSessionPermitted)
            return@runBlocking
        }

        val preferredServiceType = appsPreferences.getAppServiceTypePreference(app)
        val appsFilesystem = withContext(coroutineContext) { findAppsFilesystem(app) }
        val deferredAppsSession = async { findAppSession(app, preferredServiceType, appsFilesystem) }

        if (appIsRestartable(app)) {
            state.postValue(AppCanBeRestarted(deferredAppsSession.await()))
            return@runBlocking
        }

        if (appsFilesystemRequiresCredentials(appsFilesystem)) {
            state.postValue(AppsFilesystemRequiresCredentials(app, appsFilesystem))
            return@runBlocking
        }

        if (preferredServiceType is PreferenceHasNotBeenSelected) {
            state.postValue(AppRequiresServiceTypePreference(app))
            return@runBlocking
        }

        val appSession = deferredAppsSession.await()
        updateAppSession(appSession, preferredServiceType, appsFilesystem)

        state.postValue(AppCanBeStarted(appSession, appsFilesystem))
    }

    private fun setAppServicePreference(appName: String, serviceTypePreference: AppServiceTypePreference) {
        appsPreferences.setAppServiceTypePreference(appName, serviceTypePreference)
    }

    private suspend fun findAppsFilesystem(app: App): Filesystem = coroutineScope {
        val potentialAppFilesystem = withContext(coroutineContext) { filesystemDao.findAppsFilesystemByType(app.filesystemRequired) }

        if (potentialAppFilesystem.isEmpty()) {
            val deviceArchitecture = buildWrapper.getArchType()
            val fsToInsert = Filesystem(0, name = "apps", archType = deviceArchitecture,
                    distributionType = app.filesystemRequired, isAppsFilesystem = true)
            withContext(coroutineContext) { filesystemDao.insertFilesystem(fsToInsert) }
        }

        return@coroutineScope withContext(coroutineContext) { filesystemDao.findAppsFilesystemByType(app.filesystemRequired).first() }
    }

    // TODO possible to remove dependency on filesystem being immediately present and updating session
    // TODO appropriately later?
    private suspend fun findAppSession(
        app: App,
        serviceTypePreference: AppServiceTypePreference,
        appsFilesystem: Filesystem
    ): Session = coroutineScope {
        val serviceType = serviceTypePreference.toString()
        val potentialAppSession = withContext(coroutineContext) {
            sessionDao.findAppsSession(app.name)
        }

        // TODO revisit this when multiple sessions are supported
        val portOrDisplay: Long = if (serviceType.toLowerCase() == "ssh") 2022 else 51

        if (potentialAppSession.isEmpty()) {
            val sessionToInsert = Session(id = 0, name = app.name, filesystemId = appsFilesystem.id,
                    filesystemName = appsFilesystem.name, serviceType = serviceType.toLowerCase(),
                    username = appsFilesystem.defaultUsername,
                    password = appsFilesystem.defaultPassword,
                    vncPassword = appsFilesystem.defaultVncPassword, isAppsSession = true,
                    port = portOrDisplay)
            withContext(coroutineContext) { sessionDao.insertSession(sessionToInsert) }
        }

        return@coroutineScope withContext(coroutineContext) { sessionDao.findAppsSession(app.name).first() }
    }

    private fun appIsRestartable(app: App): Boolean {
        return activeAppsSessions.contains(app)
    }

    private fun appsFilesystemRequiresCredentials(appsFilesystem: Filesystem): Boolean {
        return appsFilesystem.defaultUsername.isEmpty() ||
                appsFilesystem.defaultPassword.isEmpty() ||
                appsFilesystem.defaultVncPassword.isEmpty()
    }

    private fun setAppsFilesystemCredentials(filesystem: Filesystem, username: String, password: String, vncPassword: String) = runBlocking {
        filesystem.defaultUsername = username
        filesystem.defaultPassword = password
        filesystem.defaultVncPassword = vncPassword
        async { filesystemDao.updateFilesystem(filesystem) }
    }

    private fun updateAppSession(appSession: Session, serviceTypePreference: AppServiceTypePreference, appsFilesystem: Filesystem) = runBlocking {
        appSession.serviceType = serviceTypePreference.toString()
        appSession.port = if (serviceTypePreference is SshTypePreference) 2022 else 51
        appSession.username = appsFilesystem.defaultUsername
        appSession.password = appsFilesystem.defaultPassword
        appSession.vncPassword = appsFilesystem.defaultVncPassword
        async { sessionDao.updateSession(appSession) }
    }
}

sealed class AppsStartupState
object WaitingForAppSelection : AppsStartupState()
object SingleSessionPermitted : AppsStartupState()
data class AppsFilesystemRequiresCredentials(val app: App, val filesystem: Filesystem) : AppsStartupState()
data class AppRequiresServiceTypePreference(val app: App) : AppsStartupState()
data class AppCanBeStarted(val appSession: Session, val appsFilesystem: Filesystem) : AppsStartupState()
data class AppCanBeRestarted(val appSession: Session) : AppsStartupState()

sealed class AppsStartupEvent
data class AppSelected(val app: App) : AppsStartupEvent()
data class SubmitAppsFilesystemCredentials(val app: App, val filesystem: Filesystem, val username: String, val password: String, val vncPassword: String) : AppsStartupEvent()
data class SubmitAppServicePreference(val app: App, val serviceTypePreference: AppServiceTypePreference) : AppsStartupEvent()