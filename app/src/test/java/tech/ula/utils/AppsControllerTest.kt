package tech.ula.utils

import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session

@RunWith(MockitoJUnitRunner::class)
class AppsControllerTest {
    @Mock lateinit var filesystemDao: FilesystemDao

    @Mock lateinit var sessionDao: SessionDao

    @Mock lateinit var filesystemUtility: FilesystemUtility

    @Mock lateinit var appsPreferences: AppsPreferences

    @Mock lateinit var buildWrapper: BuildWrapper

    lateinit var appsController: AppsController

    val id = 0L
    val unusedName = "unused"
    val selectedAppName = "selected"
    val appsFilesystemName = "apps"
    val appsFilesystemType = "appsFilesystem"
    val archType = "arch"

    val selectedApp = App(name = selectedAppName, filesystemRequired = appsFilesystemType)
    val appsFilesystem = Filesystem(id = id, name = appsFilesystemName, archType = archType, distributionType = appsFilesystemType, isAppsFilesystem = true)
    val nonAppSession = Session(id = id, name = unusedName, filesystemId = id, filesystemName = unusedName)
    val appSession = Session(id = 0, name = selectedAppName, filesystemId = id,
            filesystemName = appsFilesystem.name, serviceType = "ssh", clientType = "ConnectBot",
            isAppsSession = true, port = 2022)

    @Before
    fun setup() {
        appsController = AppsController(filesystemDao, sessionDao, filesystemUtility, appsPreferences, buildWrapper)
    }

    fun setupEarlyExitState() {
        val activeSessions = listOf(nonAppSession)
        appsController.updateActiveSessions(activeSessions)
    }

    fun stubDbCalls() {
        val filesystemWithCredentials = Filesystem(id = id, name = appsFilesystemName, archType = archType, distributionType = appsFilesystemType, isAppsFilesystem = true, defaultUsername = unusedName, defaultPassword = unusedName, defaultVncPassword = unusedName)
        whenever(filesystemDao.findAppsFilesystemByType(appsFilesystemType)).thenReturn(listOf(filesystemWithCredentials))
        whenever(sessionDao.findAppsSession(selectedAppName)).thenReturn(listOf(appSession))
    }

    fun stubPreferenceCall() {
        whenever(appsPreferences.getAppServiceTypePreference(selectedAppName)).thenReturn(SshTypePreference)
    }

    @Test
    fun insertsAppsFilesystemIntoDbIfNotExists() {
        // Stub empty list for first call, and list with inserted filesystem for second
        whenever(filesystemDao.findAppsFilesystemByType(appsFilesystemType))
                .thenReturn(listOf())
                .thenReturn(listOf(appsFilesystem))

        whenever(buildWrapper.getArchType()).thenReturn(archType)
        setupEarlyExitState()

        appsController.prepareAppForActivation(selectedApp)

        verify(filesystemDao).insertFilesystem(appsFilesystem)
        verify(filesystemDao, times(2)).findAppsFilesystemByType(appsFilesystemType)
    }

    @Test
    fun insertsAppSessionIntoDbIfNotExists() {
        whenever(filesystemDao.findAppsFilesystemByType(appsFilesystemType)).thenReturn(listOf(appsFilesystem))
        whenever(sessionDao.findAppsSession(selectedAppName))
                .thenReturn(listOf())
                .thenReturn(listOf(appSession))
        stubPreferenceCall()

        setupEarlyExitState()

        appsController.prepareAppForActivation(selectedApp)
        Thread.sleep(10) // Allow async operation to complete

        verify(sessionDao).insertSession(appSession)
        verify(sessionDao, times(2)).findAppsSession(selectedAppName)
    }

    @Test
    fun stateIsActiveAppIsNotSelected() {
        val activeSessions = listOf(nonAppSession)
        appsController.updateActiveSessions(activeSessions)
        stubPreferenceCall()
        stubDbCalls()

        val state = appsController.prepareAppForActivation(selectedApp)

        assertEquals(ActiveAppIsNotSelectedApp, state)
    }

    @Test
    fun stateIsAppCanBeRestarted() {
        val activeSessions = listOf(nonAppSession, appSession)
        appsController.updateActiveSessions(activeSessions)
        stubPreferenceCall()
        stubDbCalls()

        val state = appsController.prepareAppForActivation(selectedApp)

        assertEquals(AppCanBeRestarted(appSession), state)
    }

    @Test
    fun stateIsFilesystemNeedsCredentialsWhenFilesystemIsNotExtracted() {
        val filesystem = Filesystem(id = id, defaultUsername = unusedName, defaultPassword = unusedName, defaultVncPassword = unusedName)
        appsController.updateActiveSessions(listOf())
        stubPreferenceCall()
        whenever(filesystemDao.findAppsFilesystemByType(selectedApp.filesystemRequired)).thenReturn(listOf(filesystem))
        whenever(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(false)

        val state = appsController.prepareAppForActivation(selectedApp)

        verify(filesystemUtility).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        assertEquals(FilesystemNeedsCredentials(filesystem), state)
    }

    @Test
    fun stateIsFilesystemNeedsCredentialsWhenUsernameIsNotSet() {
        val filesystem = Filesystem(id = id, defaultPassword = unusedName, defaultVncPassword = unusedName)
        appsController.updateActiveSessions(listOf())
        stubPreferenceCall()
        whenever(filesystemDao.findAppsFilesystemByType(selectedApp.filesystemRequired)).thenReturn(listOf(filesystem))
        whenever(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(true)

        val state = appsController.prepareAppForActivation(selectedApp)

        verify(filesystemUtility).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        assertEquals(FilesystemNeedsCredentials(filesystem), state)
    }

    @Test
    fun stateIsFilesystemNeedsCredentialsWhenPasswordIsNotSet() {
        val filesystem = Filesystem(id = id, defaultUsername = unusedName, defaultVncPassword = unusedName)
        appsController.updateActiveSessions(listOf())
        stubPreferenceCall()
        whenever(filesystemDao.findAppsFilesystemByType(selectedApp.filesystemRequired)).thenReturn(listOf(filesystem))
        whenever(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(true)

        val state = appsController.prepareAppForActivation(selectedApp)

        verify(filesystemUtility).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        assertEquals(FilesystemNeedsCredentials(filesystem), state)
    }

    @Test
    fun stateIsFilesystemNeedsCredentialsWhenVncPasswordIsNotSet() {
        val filesystem = Filesystem(id = id, defaultUsername = unusedName, defaultPassword = unusedName)
        appsController.updateActiveSessions(listOf())
        stubPreferenceCall()
        whenever(filesystemDao.findAppsFilesystemByType(selectedApp.filesystemRequired)).thenReturn(listOf(filesystem))
        whenever(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(true)

        val state = appsController.prepareAppForActivation(selectedApp)

        verify(filesystemUtility).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        assertEquals(FilesystemNeedsCredentials(filesystem), state)
    }

    @Test
    fun updatesDbWithCredentialsWhenSet() {
        assertTrue(true)
    }

    @Test
    fun stateIsServiceTypePreferenceMustBeSet() {
        stubDbCalls()
        whenever(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${appsFilesystem.id}")).thenReturn(true)
        whenever(appsPreferences.getAppServiceTypePreference(selectedAppName)).thenReturn(PreferenceHasNotBeenSelected)

        val state = appsController.prepareAppForActivation(selectedApp)

        assertEquals(ServiceTypePreferenceMustBeSet, state)
    }

    @Test
    fun updatesPreferenceWhenSet() {
        assertTrue(true)
    }
}