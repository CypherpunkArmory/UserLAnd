package tech.ula.model.state

import kotlinx.coroutines.experimental.runBlocking
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.*

class AppsController(
        private val filesystemDao: FilesystemDao,
        private val sessionDao: SessionDao,
        private val appsPreferences: AppsPreferences,
        private val buildWrapper: BuildWrapper = BuildWrapper()
) {

    private var activeSessions = listOf<Session>()
    private val unselectedSession = Session(id = -1, name = "unselected", filesystemId = -1, filesystemName = "unselected")

    fun updateActiveSessions(newActiveSessions: List<Session>) { activeSessions = newActiveSessions }

    fun prepareAppForActivation(app: App): AppPrepState = runBlocking {
        val preferredServiceType = getAppServicePreference(app)
        val appsFilesystem = async { findAppsFilesystem(app.filesystemRequired) }.await()
        val deferredAppSession = async { findAppSession(app.name, preferredServiceType.toString(), appsFilesystem) }

        if (activeSessions.isNotEmpty()) {
            val activeSession =
                    activeSessions.find {
                        it.name == app.name && it.serviceType == preferredServiceType.toString()
                    } ?: unselectedSession

            if (activeSession == unselectedSession) return@runBlocking ActiveAppIsNotSelectedApp
//            return@runBlocking AppCanBeRestarted(activeSession)
        }

        if (!appsFilesystemHasCredentialsSet(appsFilesystem)) {
            return@runBlocking FilesystemNeedsCredentials(appsFilesystem, app)
        }

        if (preferredServiceType is PreferenceHasNotBeenSelected) {
            return@runBlocking ServiceTypePreferenceMustBeSet(app)
        }

        updateAppSession(deferredAppSession.await(), preferredServiceType, appsFilesystem)

        return@runBlocking AppIsPrepped(deferredAppSession.await(), appsFilesystem)
    }

    fun appsFilesystemHasCredentialsSet(filesystem: Filesystem): Boolean {
        return filesystem.defaultUsername.isNotEmpty() && filesystem.defaultPassword.isNotEmpty() &&
                filesystem.defaultVncPassword.isNotEmpty()
    }

    fun setAppsFilesystemCredentials(filesystem: Filesystem, username: String, password: String,
                                     vncPassword: String, app: App) = runBlocking {
        filesystem.defaultUsername = username
        filesystem.defaultPassword = password
        filesystem.defaultVncPassword = vncPassword
        async { filesystemDao.updateFilesystem(filesystem) }
        prepareAppForActivation(app)
    }

    fun getAppServicePreference(app: App): AppServiceTypePreference {
        return when {
            app.supportsCli && app.supportsGui -> appsPreferences.getAppServiceTypePreference(app.name)
            app.supportsCli && !app.supportsGui -> SshTypePreference
            !app.supportsCli && app.supportsGui -> VncTypePreference
            else -> PreferenceHasNotBeenSelected
        }
    }

    fun setAppServicePreference(app: App, serviceTypePreference: AppServiceTypePreference) {
        appsPreferences.setAppServiceTypePreference(app.name, serviceTypePreference)
        prepareAppForActivation(app)
    }

    suspend fun updateAppSession(appSession: Session, serviceTypePreference: AppServiceTypePreference, appsFilesystem: Filesystem) {
        // TODO this will force the session the have values defined on the filesystem. is that harmful?
        appSession.serviceType = serviceTypePreference.toString()
        appSession.port = if (serviceTypePreference is SshTypePreference) 2022 else 51
        appSession.username = appsFilesystem.defaultUsername
        appSession.password = appsFilesystem.defaultPassword
        appSession.vncPassword = appsFilesystem.defaultVncPassword
        async { sessionDao.updateSession(appSession) }
    }

    @Throws // If device architecture is unsupported
    suspend fun findAppsFilesystem(
        requiredFilesystemType: String
    ): Filesystem {
        val potentialAppFilesystem = asyncAwait {
            filesystemDao.findAppsFilesystemByType(requiredFilesystemType)
        }

        if (potentialAppFilesystem.isEmpty()) {
            val deviceArchitecture = buildWrapper.getArchType()
            val fsToInsert = Filesystem(0, name = "apps", archType = deviceArchitecture,
                    distributionType = requiredFilesystemType, isAppsFilesystem = true)
            asyncAwait { filesystemDao.insertFilesystem(fsToInsert) }
        }

        return asyncAwait { filesystemDao.findAppsFilesystemByType(requiredFilesystemType).first() }
    }

    suspend fun findAppSession(
        appName: String,
        serviceType: String,
        appsFilesystem: Filesystem
    ): Session {
        val potentialAppSession = asyncAwait {
            sessionDao.findAppsSession(appName)
        }

        // TODO revisit this when multiple sessions are supported
        val portOrDisplay: Long = if (serviceType.toLowerCase() == "ssh") 2022 else 51

        if (potentialAppSession.isEmpty()) {
            val sessionToInsert = Session(id = 0, name = appName, filesystemId = appsFilesystem.id,
                    filesystemName = appsFilesystem.name, serviceType = serviceType.toLowerCase(),
                    username = appsFilesystem.defaultUsername,
                    password = appsFilesystem.defaultPassword,
                    vncPassword = appsFilesystem.defaultVncPassword, isAppsSession = true,
                    port = portOrDisplay)
            asyncAwait { sessionDao.insertSession(sessionToInsert) }
        }

        return asyncAwait {
            sessionDao.findAppsSession(appName).first()
        }
    }
}

sealed class AppPrepState
object ActiveAppIsNotSelectedApp : AppPrepState()
//data class AppCanBeRestarted(val appSession: Session) : AppPrepState()
data class FilesystemNeedsCredentials(val filesystem: Filesystem, val app: App) : AppPrepState()
data class ServiceTypePreferenceMustBeSet(val app: App) : AppPrepState()
data class AppIsPrepped(val appSession: Session, val appsFilesystem: Filesystem) : AppPrepState()
data class PrepFailed(val error: String) : AppPrepState()