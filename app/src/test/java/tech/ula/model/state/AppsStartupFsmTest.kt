package tech.ula.model.state

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
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
    lateinit var activeSessionLiveData: MutableLiveData<List<Session>>

    @Mock lateinit var mockAppsDao: AppsDao
    lateinit var appsListLiveData: MutableLiveData<List<App>>

    @Mock lateinit var mockUlaDatabase: UlaDatabase

    @Mock lateinit var mockAppsPreferences: AppsPreferences

    @Mock lateinit var mockBuildWrapper: BuildWrapper

    @Mock lateinit var mockStateObserver: Observer<AppsStartupState>

    lateinit var appsFSM: AppsStartupFsm

    // Test setup variables
    val app1Name = "testApp1"
    val app2Name = "testApp2"
    val appsFilesystemName = "apps"
    val appsFilesystemType = "type"

    val app1 = App(name = app1Name, supportsCli = true, supportsGui = true, filesystemRequired = appsFilesystemType)
    val app2 = App(name = app2Name, supportsCli = true, supportsGui = true, filesystemRequired = appsFilesystemType)

    val nonAppActiveSession = Session(id = -1, name = "notAnApp", filesystemId = -1, active = true)
    val app1ActiveSession = Session(id = -1, name = app1Name, filesystemId = -1, active = true)
    val app2ActiveSession = Session(id = -1, name = app2Name, filesystemId = -1, active = true)
    val app1InactiveSession = Session(id = -1, name = app1Name, filesystemId = -1, active = false)

    val defaultUsername = "user"
    val defaultPassword = "password"
    val appsFilesystem = Filesystem(id = 0, name = appsFilesystemName, distributionType = appsFilesystemType, isAppsFilesystem = true)
    val appsFilesystemWithCredentials = Filesystem(id = 0, name = appsFilesystemName, distributionType = appsFilesystemType, isAppsFilesystem = true, defaultUsername = defaultUsername, defaultPassword = defaultPassword, defaultVncPassword = defaultPassword)

    // NOTE: order of live data posts will matter because of how observers in FSM interact

    @Before
    fun setup() {
        activeSessionLiveData = MutableLiveData()
        appsListLiveData = MutableLiveData()

        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)
        whenever(mockSessionDao.findActiveSessions()).thenReturn(activeSessionLiveData)

        whenever(mockUlaDatabase.appsDao()).thenReturn(mockAppsDao)
        whenever(mockAppsDao.getAllApps()).thenReturn(appsListLiveData)

        whenever(mockUlaDatabase.filesystemDao()).thenReturn(mockFilesystemDao)

        appsFSM = AppsStartupFsm(mockUlaDatabase, mockAppsPreferences, mockBuildWrapper) // TODO Showerthought: default initialization of arch in db entry and then erroring to BuildWrapper?
    }

    @After
    fun teardown() {
        activeSessionLiveData = MutableLiveData()
        appsListLiveData = MutableLiveData()
    }

    fun stubEmptyActiveSessions() {
        activeSessionLiveData.postValue(listOf())
    }

    fun stubEmptyAppsList() {
        appsListLiveData.postValue(listOf())
    }

    fun stubFullAppsList() {
        appsListLiveData.postValue(listOf(app1, app2))
    }

    fun setupAppIsRestartableEarlyExitState() {
        stubFullAppsList()
        activeSessionLiveData.postValue(listOf(app1ActiveSession))

        whenever(mockFilesystemDao.findAppsFilesystemByType(app1.filesystemRequired)).thenReturn(listOf(appsFilesystemWithCredentials))
        whenever(mockSessionDao.findAppsSession(app1.name)).thenReturn(listOf(app1InactiveSession))
        whenever(mockAppsPreferences.getAppServiceTypePreference(app1)).thenReturn(SshTypePreference)
    }

    fun setupAppIsNotActiveSessionEarlyExitState() {
        stubFullAppsList()
        activeSessionLiveData.postValue(listOf(nonAppActiveSession))
    }

    @Test
    fun `Initial state is WaitingForApps`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubEmptyActiveSessions()
        stubFullAppsList()

        val expectedState = WaitingForAppSelection
        verify(mockStateObserver).onChanged(expectedState)
    }

    @Test
    fun `Initial state is AppsListIsEmpty when apps db entries are not populated`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubEmptyActiveSessions()
        stubEmptyAppsList()

        val expectedState = AppsListIsEmpty
        verify(mockStateObserver).onChanged(expectedState)
    }

    @Test
    fun `State is SingleSessionPermitted when a session is running that is not an app`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        activeSessionLiveData.postValue(listOf(nonAppActiveSession))

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(SingleSessionPermitted)
    }

    @Test
    fun `State is SingleSessionPermitted when the selected app is not the active one`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        activeSessionLiveData.postValue(listOf(app2ActiveSession))

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(SingleSessionPermitted)
    }

    @Test
    fun `State is AppIsRestartable if selected app is active one`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        whenever(mockAppsPreferences.getAppServiceTypePreference(app1)).thenReturn(SshTypePreference)
        whenever(mockFilesystemDao.findAppsFilesystemByType(appsFilesystemType)).thenReturn(listOf(appsFilesystem))
        whenever(mockSessionDao.findAppsSession(app1.name)).thenReturn(listOf(app1ActiveSession))

        activeSessionLiveData.postValue(listOf(app1ActiveSession))

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(WaitingForAppSelection)
        verify(mockStateObserver).onChanged(AppCanBeRestarted(app1ActiveSession))
    }

    @Test
    fun `Apps filesystem is inserted if not in db`() {
        appsFSM.getState().observeForever(mockStateObserver)
        setupAppIsRestartableEarlyExitState()
        whenever(mockSessionDao.findAppsSession(app1.name)).thenReturn(listOf(app1ActiveSession))

        whenever(mockFilesystemDao.findAppsFilesystemByType(app1.filesystemRequired))
                .thenReturn(listOf())
                .thenReturn(listOf(appsFilesystem))
        whenever(mockBuildWrapper.getArchType()).thenReturn("")

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(AppCanBeRestarted(app1ActiveSession))
        verify(mockFilesystemDao, times(2)).findAppsFilesystemByType(appsFilesystemType)
        verify(mockFilesystemDao).insertFilesystem(appsFilesystem)
    }

    @Test
    fun `App session is inserted if not in db`() {
        appsFSM.getState().observeForever(mockStateObserver)
        setupAppIsRestartableEarlyExitState()
        whenever(mockFilesystemDao.findAppsFilesystemByType(app1.filesystemRequired)).thenReturn(listOf(appsFilesystem))

        whenever(mockSessionDao.findAppsSession(app1.name))
                .thenReturn(listOf())
                .thenReturn(listOf(app1ActiveSession))

        appsFSM.submitEvent(AppSelected(app1))

        val insertedSession = Session(id = 0, name = app1.name, filesystemId = appsFilesystem.id, filesystemName = appsFilesystem.name, serviceType = "ssh", isAppsSession = true, port = 2022)
        verify(mockStateObserver).onChanged(AppCanBeRestarted(app1ActiveSession))
        verify(mockSessionDao, times(2)).findAppsSession(app1.name)
        verify(mockSessionDao).insertSession(insertedSession)
    }

    @Test
    fun `State is FilesystemRequiresCredentials if username is not set`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        stubEmptyActiveSessions()
        whenever(mockAppsPreferences.getAppServiceTypePreference(app1)).thenReturn(SshTypePreference)
        whenever(mockSessionDao.findAppsSession(app1.name)).thenReturn(listOf(app1ActiveSession))

        val filesystemWithoutUsername = appsFilesystemWithCredentials
        filesystemWithoutUsername.defaultUsername = ""
        whenever(mockFilesystemDao.findAppsFilesystemByType(appsFilesystemType)).thenReturn(listOf(filesystemWithoutUsername))

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(app1, filesystemWithoutUsername))
    }

    @Test
    fun `State is FilesystemRequiresCredentials if password is not set`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        stubEmptyActiveSessions()
        whenever(mockAppsPreferences.getAppServiceTypePreference(app1)).thenReturn(SshTypePreference)
        whenever(mockSessionDao.findAppsSession(app1.name)).thenReturn(listOf(app1ActiveSession))

        val filesystemWithoutPassword = appsFilesystemWithCredentials
        filesystemWithoutPassword.defaultPassword = ""
        whenever(mockFilesystemDao.findAppsFilesystemByType(appsFilesystemType)).thenReturn(listOf(filesystemWithoutPassword))

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(app1, filesystemWithoutPassword))
    }

    @Test
    fun `State is FilesystemRequiresCredentials if VNC password is not set`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        stubEmptyActiveSessions()
        whenever(mockAppsPreferences.getAppServiceTypePreference(app1)).thenReturn(SshTypePreference)
        whenever(mockSessionDao.findAppsSession(app1.name)).thenReturn(listOf(app1ActiveSession))

        val filesystemWithoutVncPassword = appsFilesystemWithCredentials
        filesystemWithoutVncPassword.defaultVncPassword = ""
        whenever(mockFilesystemDao.findAppsFilesystemByType(appsFilesystemType)).thenReturn(listOf(filesystemWithoutVncPassword))

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(app1, filesystemWithoutVncPassword))
    }

    @Test
    fun `Updates filesystem when event is submitted`() {
        appsFSM.getState().observeForever(mockStateObserver)
        setupAppIsNotActiveSessionEarlyExitState()
        val testFilesystem = Filesystem(id = -1, name = "test")

        appsFSM.submitEvent(SubmitAppsFilesystemCredentials(app1, testFilesystem, defaultUsername, defaultPassword, defaultPassword))

        testFilesystem.defaultUsername = defaultUsername
        testFilesystem.defaultPassword = defaultPassword
        testFilesystem.defaultVncPassword = defaultPassword
        verify(mockFilesystemDao).updateFilesystem(testFilesystem)
    }

    @Test
    fun `State is AppRequiresServiceTypePreference if preference not set`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        stubEmptyActiveSessions()

        whenever(mockFilesystemDao.findAppsFilesystemByType(app1.filesystemRequired))
                .thenReturn(listOf(appsFilesystemWithCredentials))
        whenever(mockSessionDao.findAppsSession(app1.name))
                .thenReturn(listOf(app1InactiveSession))

        whenever(mockAppsPreferences.getAppServiceTypePreference(app1))
                .thenReturn(PreferenceHasNotBeenSelected)

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(AppRequiresServiceTypePreference(app1))
    }

    @Test
    fun `Updates service preference when event is submitted`() {
        appsFSM.getState().observeForever(mockStateObserver)
        setupAppIsNotActiveSessionEarlyExitState()

        appsFSM.submitEvent(SubmitAppServicePreference(app1, VncTypePreference))

        verify(mockAppsPreferences).setAppServiceTypePreference(app1.name, VncTypePreference)
    }

    @Test
    fun `State is AppCanBeStarted when everything is ready, and updates session with latest info`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        stubEmptyActiveSessions()

        whenever(mockAppsPreferences.getAppServiceTypePreference(app1))
                .thenReturn(SshTypePreference)
        whenever(mockFilesystemDao.findAppsFilesystemByType(app1.filesystemRequired))
                .thenReturn(listOf(appsFilesystemWithCredentials))
        whenever(mockSessionDao.findAppsSession(app1.name))
                .thenReturn(listOf(app1InactiveSession))

        appsFSM.submitEvent(AppSelected(app1))

        val updatedSession = Session(id = -1, name = app1Name, filesystemId = -1, active = false, serviceType = "ssh", port = 2022, username = defaultUsername, password = defaultPassword, vncPassword = defaultPassword)
        verify(mockStateObserver)
                .onChanged(AppCanBeStarted(app1InactiveSession, appsFilesystemWithCredentials))
        verify(mockSessionDao).updateSession(updatedSession)
    }

    @Test
    fun `State is AppCanBeStarted when everything is ready, and updates session with latest info, using 51 as port`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        stubEmptyActiveSessions()

        whenever(mockAppsPreferences.getAppServiceTypePreference(app1))
                .thenReturn(VncTypePreference)
        whenever(mockFilesystemDao.findAppsFilesystemByType(app1.filesystemRequired))
                .thenReturn(listOf(appsFilesystemWithCredentials))
        whenever(mockSessionDao.findAppsSession(app1.name))
                .thenReturn(listOf(app1InactiveSession))

        appsFSM.submitEvent(AppSelected(app1))

        val updatedSession = Session(id = -1, name = app1Name, filesystemId = -1, active = false, serviceType = "vnc", port = 51, username = defaultUsername, password = defaultPassword, vncPassword = defaultPassword)
        verify(mockStateObserver)
                .onChanged(AppCanBeStarted(app1InactiveSession, appsFilesystemWithCredentials))
        verify(mockSessionDao).updateSession(updatedSession)
    }

    @Test
    fun `State becomes AppsHaveActivated when an apps session is activated`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        activeSessionLiveData.postValue(listOf())

        activeSessionLiveData.postValue(listOf(app1ActiveSession))

        verify(mockStateObserver).onChanged(WaitingForAppSelection)
        verify(mockStateObserver).onChanged(AppsHaveActivated(listOf(app1)))
    }

    @Test
    fun `State becomes AppsHaveDeactivated when an apps session is deactivated`() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubFullAppsList()
        activeSessionLiveData.postValue(listOf(app1ActiveSession))

        activeSessionLiveData.postValue((listOf()))

        verify(mockStateObserver).onChanged(WaitingForAppSelection)
        verify(mockStateObserver).onChanged(AppsHaveActivated(listOf(app1)))
        verify(mockStateObserver).onChanged(AppsHaveDeactivated(listOf(app1)))
    }
}