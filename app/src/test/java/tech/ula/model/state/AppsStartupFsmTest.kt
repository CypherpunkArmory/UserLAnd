package tech.ula.model.state

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.AppsDao
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

@RunWith(MockitoJUnitRunner::class)
class AppsStartupFsmTest {

    @get:Rule val instantExecutorRule = InstantTaskExecutorRule()

    // Mocks

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockSessionDao: SessionDao

    @Mock lateinit var mockUlaDatabase: UlaDatabase

    @Mock lateinit var mockAppsPreferences: AppsPreferences

    @Mock lateinit var mockFilesystemUtility: FilesystemUtility

    @Mock lateinit var mockBuildWrapper: BuildWrapper

    @Mock lateinit var mockStateObserver: Observer<AppsStartupState>

    lateinit var appsFSM: AppsStartupFsm

    // Test setup variables
    val appsFilesystemName = "apps"
    val appsFilesystemType = "type"
    val appName = "app"

    val defaultUsername = "user"
    val defaultPassword = "password"
    val appsFilesystem = Filesystem(id = 0, name = appsFilesystemName, distributionType = appsFilesystemType, isAppsFilesystem = true)
    val appsFilesystemWithCredentials = Filesystem(id = 0, name = appsFilesystemName, distributionType = appsFilesystemType, isAppsFilesystem = true, defaultUsername = defaultUsername, defaultPassword = defaultPassword, defaultVncPassword = defaultPassword)


    val app = App(name = appName, filesystemRequired = appsFilesystemType)

    @Before
    fun setup() {
        whenever(mockUlaDatabase.filesystemDao()).thenReturn(mockFilesystemDao)
        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)

        appsFSM = AppsStartupFsm(mockUlaDatabase, mockAppsPreferences, mockFilesystemUtility, mockBuildWrapper) // TODO Showerthought: default initialization of arch in db entry and then erroring to BuildWrapper?
    }

    @Test
    fun `Initial state is WaitingForApps`() {
        appsFSM.getState().observeForever(mockStateObserver)

        val expectedState = WaitingForAppSelection
        verify(mockStateObserver).onChanged(expectedState)
    }

    @Test
    fun `Apps filesystem is inserted if not in db`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        whenever(mockBuildWrapper.getArchType()).thenReturn("")
        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf())
                .thenReturn(listOf(appsFilesystemWithCredentials))

        try {
            appsFSM.submitEvent(AppSelected(app))
        } catch (err: NullPointerException) { } // Short-circuit test

        verify(mockFilesystemDao, times(2)).findAppsFilesystemByType(app.filesystemRequired)
        verify(mockFilesystemDao).insertFilesystem(appsFilesystem)
    }

    @Test
    fun `State is FilesystemRequiresCredentials if username is not set`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        val filesystemWithoutUsername = appsFilesystemWithCredentials
        filesystemWithoutUsername.defaultUsername = ""
        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(filesystemWithoutUsername))

        appsFSM.submitEvent(AppSelected(app))

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(app, filesystemWithoutUsername))
    }

    @Test
    fun `State is FilesystemRequiresCredentials if password is not set`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        appsFSM.getState().observeForever(mockStateObserver)

        val filesystemWithoutPassword = appsFilesystemWithCredentials
        filesystemWithoutPassword.defaultPassword = ""
        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(filesystemWithoutPassword))

        appsFSM.submitEvent(AppSelected(app))

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(app, filesystemWithoutPassword))
    }

    @Test
    fun `State is FilesystemRequiresCredentials if VNC password is not set`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        appsFSM.getState().observeForever(mockStateObserver)

        val filesystemWithoutVncPassword = appsFilesystemWithCredentials
        filesystemWithoutVncPassword.defaultVncPassword = ""
        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(filesystemWithoutVncPassword))

        appsFSM.submitEvent(AppSelected(app))

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(app, filesystemWithoutVncPassword))
    }

    @Test
    fun `Updates filesystem when event is submitted`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        val username = appsFilesystemWithCredentials.defaultUsername
        val password = appsFilesystemWithCredentials.defaultPassword
        val vncPassword = appsFilesystemWithCredentials.defaultVncPassword

        try {
            appsFSM.submitEvent(SubmitAppsFilesystemCredentials(app, appsFilesystem, username, password, vncPassword))
        } catch (err: IllegalArgumentException) { } // Short-circuit test

        verify(mockFilesystemDao).updateFilesystem(appsFilesystemWithCredentials)
    }

    @Test
    fun `State is AppRequiresServiceTypePreference if preference not set`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(appsFilesystemWithCredentials))
        whenever(mockAppsPreferences.getAppServiceTypePreference(app))
                .thenReturn(PreferenceHasNotBeenSelected)

        appsFSM.submitEvent(AppSelected(app))

        verify(mockStateObserver).onChanged(AppRequiresServiceTypePreference(app))
    }

    @Test
    fun `Updates service preference when event is submitted`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        try {
            appsFSM.submitEvent(SubmitAppServicePreference(app, SshTypePreference))
        } catch (err: IllegalArgumentException) { } // Short-circuit test

        verify(mockAppsPreferences).setAppServiceTypePreference(app.name, SshTypePreference)
    }

    @Test
    fun `Moves app script to filesystem`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(appsFilesystemWithCredentials))
        whenever(mockAppsPreferences.getAppServiceTypePreference(app))
                .thenReturn(SshTypePreference)

        try {
            appsFSM.submitEvent(AppSelected(app))
        } catch (err: NullPointerException) { } //Short-circuit test

        verify(mockFilesystemUtility).moveAppScriptToRequiredLocation(app.name, appsFilesystemWithCredentials)
    }

    @Test
    fun `App session is inserted if not in db`() {
        appsFSM.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(appsFilesystemWithCredentials))
        whenever(mockAppsPreferences.getAppServiceTypePreference(app))
                .thenReturn(SshTypePreference)

        val appSession = Session(id = 0, name = app.name, filesystemId = -1, isAppsSession = true)
        whenever(mockSessionDao.findAppsSession(app.name))
                .thenReturn(listOf())
                .thenReturn(listOf(appSession))

        runBlocking {
            try {
                appsFSM.submitEvent(AppSelected(app))
            } catch (err: NullPointerException) { } // Short-circuit test
        }

        verify(mockSessionDao, times(2)).findAppsSession(app.name)
        verify(mockSessionDao).insertSession(appSession)
    }

    @Test
    fun `State is AppCanBeStarted when everything is ready, and updates session with latest info`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)
    }

    @Test
    fun `State is AppCanBeStarted when everything is ready, and updates session with latest info, using 51 as port`() = runBlocking {
        appsFSM.getState().observeForever(mockStateObserver)
    }
}