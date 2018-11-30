package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.util.Log
import kotlinx.coroutines.experimental.runBlocking
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.*

class AppsStartupFsm (
        ulaDatabase: UlaDatabase,
        private val appsPreferences: AppsPreferences,
        private val buildWrapper: BuildWrapper = BuildWrapper()) {

    private val sessionDao = ulaDatabase.sessionDao()
    private val filesystemDao = ulaDatabase.filesystemDao()
    private val appsDao = ulaDatabase.appsDao()

    private val sessionList = sessionDao.findActiveSessions()
    private val activeSessionsObserver = Transformations.map(sessionList) { sessions ->
        sessions.filter { it.active }
    }
    private val activeSessions = mutableListOf<Session>()

    private val appsList = appsDao.getAllApps()
    private val appsSessionsObserver = Transformations.map(sessionList) { sessions ->
        appsList.value?.let { list ->
            list.filter { app ->
                sessions.any { session ->
                    app.name == session.name
                }
            }
        }
    }
    private val activeApps = mutableListOf<App>()

    private val unselectedFilesystem = Filesystem(id = -1, name = "unselected")
    private var lastSelectedAppsFilesystem = unselectedFilesystem

    private val unselectedApp = App(name = "unselected")
    private var lastSelectedApp = unselectedApp

    private val state = MutableLiveData<AppsStartupState>().apply { postValue(WaitingForAppSelection) }

    // TODO Is there a way to combine these observers?
    // TODO If an apps session is activated, it doesn't show the app as active
    init {
        activeSessionsObserver.observeForever {
            it?.let { list ->
                activeSessions.clear()
                activeSessions.addAll(list)
            }
        }
        // The appsList must be observed to propagate data. Otherwise the value will always be null.
        appsList.observeForever {
            it?.let { list ->
                if (list.isEmpty()) state.postValue(AppsListIsEmpty)
            }
        }
        appsSessionsObserver.observeForever {
            it?.let { list ->
                if (list != activeApps)
                updateActiveApps(list)
            }
        }
    }

    private fun updateActiveApps(newActiveApps: List<App>) {
        if (newActiveApps.size > activeApps.size) {
            val newlyActiveApps = newActiveApps.subtract(activeApps)
            state.postValue(AppsHaveActivated(newlyActiveApps.toList()))
        }
        else {
            val newlyInactiveApps = activeApps.subtract(newActiveApps)
            state.postValue(AppsHaveDeactivated(newlyInactiveApps.toList()))
        }
        activeApps.clear()
        activeApps.addAll(newActiveApps)
    }

    fun getState() : MutableLiveData<AppsStartupState> {
        return state
    }

    fun submitEvent(event: AppsStartupEvent) = runBlocking {
        return@runBlocking when (event) {
            is AppSelected -> appWasSelected(event.app)
            is SubmitAppsFilesystemCredentials -> {
                setAppsFilesystemCredentials(event.username, event.password, event.vncPassword)
                appWasSelected(lastSelectedApp)
            }
            is SubmitAppServicePreference -> {
                setAppServicePreference(lastSelectedApp.name, event.serviceTypePreference)
                appWasSelected(lastSelectedApp)
            }
        }
    }

    private suspend fun appWasSelected(app: App) {
        // TODO more robust check
        if (activeSessions.isNotEmpty() && !activeSessions.any { it.name == app.name }) {
            state.postValue(SingleSessionPermitted)
            return
        }
        lastSelectedApp = app
        val preferredServiceType = getAppServiceTypePreference(app)
        lastSelectedAppsFilesystem = async { findAppsFilesystem(app) }.await()
        val deferredAppsSession = async { findAppSession(app, preferredServiceType, lastSelectedAppsFilesystem) }

        if (appIsRestartable(app)) {
            state.postValue(AppCanBeRestarted(deferredAppsSession.await()))
            return
        }

        if (appsFilesystemRequiresCredentials(lastSelectedAppsFilesystem)) {
            state.postValue(AppsFilesystemRequiresCredentials)
            return
        }

        if (preferredServiceType is PreferenceHasNotBeenSelected) {
            state.postValue(AppRequiresServiceTypePreference)
            return
        }

        val appSession = deferredAppsSession.await()
        updateAppSession(appSession, preferredServiceType, lastSelectedAppsFilesystem)

        state.postValue(AppCanBeStarted(appSession, lastSelectedAppsFilesystem))
    }

    private fun getAppServiceTypePreference(app: App): AppServiceTypePreference {
        return when {
            app.supportsCli && app.supportsGui -> appsPreferences.getAppServiceTypePreference(app.name)
            app.supportsCli && !app.supportsGui -> SshTypePreference
            !app.supportsCli && app.supportsGui -> VncTypePreference
            else -> PreferenceHasNotBeenSelected
        }
    }

    private fun setAppServicePreference(appName: String, serviceTypePreference: AppServiceTypePreference) {
        appsPreferences.setAppServiceTypePreference(appName, serviceTypePreference)
    }

    private suspend fun findAppsFilesystem(app: App): Filesystem {
        val potentialAppFilesystem = asyncAwait {
            filesystemDao.findAppsFilesystemByType(app.filesystemRequired)
        }

        if (potentialAppFilesystem.isEmpty()) {
            val deviceArchitecture = buildWrapper.getArchType()
            val fsToInsert = Filesystem(0, name = "apps", archType = deviceArchitecture,
                    distributionType = app.filesystemRequired, isAppsFilesystem = true)
            asyncAwait { filesystemDao.insertFilesystem(fsToInsert) }
        }

        return asyncAwait { filesystemDao.findAppsFilesystemByType(app.filesystemRequired).first() }
    }

    // TODO possible to remove dependency on filesystem being immediately present and updating session
    // TODO appropriately later?
    private suspend fun findAppSession(app: App, serviceTypePreference: AppServiceTypePreference, appsFilesystem: Filesystem): Session {
        val serviceType = serviceTypePreference.toString()
        val potentialAppSession = asyncAwait {
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
            asyncAwait { sessionDao.insertSession(sessionToInsert) }
        }

        return asyncAwait {
            sessionDao.findAppsSession(app.name).first()
        }
    }

    private fun appIsRestartable(app: App): Boolean {
        return activeApps.contains(app)
    }

    private fun appsFilesystemRequiresCredentials(appsFilesystem: Filesystem): Boolean {
        return appsFilesystem.defaultUsername.isEmpty() ||
                appsFilesystem.defaultPassword.isEmpty() ||
                appsFilesystem.defaultVncPassword.isEmpty()
    }

    private suspend fun setAppsFilesystemCredentials(username: String, password: String, vncPassword: String) {
        // TODO verify last selected is not unselected and error if not
        lastSelectedAppsFilesystem.defaultUsername = username
        lastSelectedAppsFilesystem.defaultPassword = password
        lastSelectedAppsFilesystem.defaultVncPassword = vncPassword
        async { filesystemDao.updateFilesystem(lastSelectedAppsFilesystem) }.await()
    }

    private suspend fun updateAppSession(appSession: Session, serviceTypePreference: AppServiceTypePreference, appsFilesystem: Filesystem) {
        // TODO this will force the session to have values defined on the filesystem. is that harmful?
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
object AppsListIsEmpty : AppsStartupState()
object SingleSessionPermitted : AppsStartupState()
object AppsFilesystemRequiresCredentials : AppsStartupState()
object AppRequiresServiceTypePreference : AppsStartupState()
data class AppCanBeStarted(val appSession: Session, val appsFilesystem: Filesystem) : AppsStartupState()
data class AppCanBeRestarted(val appSession: Session) : AppsStartupState()
data class AppsHaveActivated(val activeApps: List<App>) : AppsStartupState()
data class AppsHaveDeactivated(val inactiveApps: List<App>) : AppsStartupState()

sealed class AppsStartupEvent
data class AppSelected(val app: App) : AppsStartupEvent()
data class SubmitAppsFilesystemCredentials(val username: String, val password: String, val vncPassword: String) : AppsStartupEvent()
data class SubmitAppServicePreference(val serviceTypePreference: AppServiceTypePreference) : AppsStartupEvent()