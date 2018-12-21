package tech.ula.model.state

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
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

    lateinit var appsFsm: AppsStartupFsm

    // Test setup variables
    val appsFilesystemName = "apps"
    val appsFilesystemType = "type"
    val appName = "app"

    val defaultUsername = "user"
    val defaultPassword = "password"
    val appsFilesystem = Filesystem(id = 0, name = appsFilesystemName, distributionType = appsFilesystemType, isAppsFilesystem = true)
    val appsFilesystemWithCredentials = Filesystem(id = 0, name = appsFilesystemName, distributionType = appsFilesystemType, isAppsFilesystem = true, defaultUsername = defaultUsername, defaultPassword = defaultPassword, defaultVncPassword = defaultPassword)

    val appSession = Session(id = 0, name = appName, filesystemId = 0, isAppsSession = true)

    val app = App(name = appName, filesystemRequired = appsFilesystemType)

    val incorrectTransitionEvent = AppSelected(app)
    val incorrectTransitionState = FetchingDatabaseEntries
    val possibleEvents = listOf(
            AppSelected(app),
            CheckAppsFilesystemCredentials(appsFilesystem),
            SubmitAppsFilesystemCredentials(appsFilesystem, "", "", ""),
            CheckAppServicePreference(app),
            SubmitAppServicePreference(app, PreferenceHasNotBeenSelected),
            CopyAppScriptToFilesystem(app, appsFilesystem),
            SyncDatabaseEntries(app, appSession, appsFilesystem),
            ResetAppState
    )
    val possibleStates = listOf(
            IncorrectAppTransition(incorrectTransitionEvent, incorrectTransitionState),
            WaitingForAppSelection,
            FetchingDatabaseEntries,
            DatabaseEntriesFetched(appsFilesystem, appSession),
            AppsFilesystemHasCredentials,
            AppsFilesystemRequiresCredentials(appsFilesystem),
            AppHasServiceTypePreferenceSet,
            AppRequiresServiceTypePreference,
            CopyingAppScript,
            AppScriptCopySucceeded,
            AppScriptCopyFailed,
            SyncingDatabaseEntries,
            AppDatabaseEntriesSynced
    )

    @Before
    fun setup() {
        whenever(mockUlaDatabase.filesystemDao()).thenReturn(mockFilesystemDao)
        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)

        appsFsm = AppsStartupFsm(mockUlaDatabase, mockAppsPreferences, mockFilesystemUtility, mockBuildWrapper) // TODO Showerthought: default initialization of arch in db entry and then erroring to BuildWrapper?
    }

    @Test
    fun `Only allows correct state transitions`() = runBlocking {
        appsFsm.getState().observeForever(mockStateObserver)

        val session = Session(0, "", 0)
        for (event in possibleEvents) {
            for (state in possibleStates) {
                appsFsm.setState(state)
                val result = appsFsm.transitionIsAcceptable(event)
                when {
                    event is AppSelected && state is WaitingForAppSelection -> assertTrue(result)
                    event is CheckAppsFilesystemCredentials && state is DatabaseEntriesFetched -> assertTrue(result)
                    event is SubmitAppsFilesystemCredentials && state is AppsFilesystemRequiresCredentials -> assertTrue(result)
                    event is CheckAppServicePreference && state is AppsFilesystemHasCredentials -> assertTrue(result)
                    event is SubmitAppServicePreference && state is AppRequiresServiceTypePreference -> assertTrue(result)
                    event is CopyAppScriptToFilesystem && state is AppHasServiceTypePreferenceSet -> assertTrue(result)
                    event is SyncDatabaseEntries && state is AppScriptCopySucceeded -> assertTrue(result)
                    event is ResetAppState -> assertTrue(result)
                    else -> assertFalse(result)
                }
            }
        }
    }
    
    @Test
    fun `Exits early when an incorrect transition is submitted`() {
        val state = WaitingForAppSelection
        appsFsm.setState(state)
        appsFsm.getState().observeForever(mockStateObserver)

        val event = CheckAppsFilesystemCredentials(appsFilesystem)
        runBlocking { appsFsm.submitEvent(event) }

        verify(mockStateObserver).onChanged(IncorrectAppTransition(event, state))
        verify(mockStateObserver, times(2)).onChanged(any())
    }

    @Test
    fun `Initial state is WaitingForApps`() {
        appsFsm.getState().observeForever(mockStateObserver)

        val expectedState = WaitingForAppSelection
        verify(mockStateObserver).onChanged(expectedState)
    }

    @Test
    fun `State can be reset`() {
        appsFsm.getState().observeForever(mockStateObserver)

        for (state in possibleStates) {
            appsFsm.setState(state)
            runBlocking { appsFsm.submitEvent(ResetAppState) }
        }

        val numberOfStates = possibleStates.size
        // Will initially be WaitingForAppSelection (+1), the test for that state (+1), and then reset for each
        verify(mockStateObserver, times(numberOfStates + 2)).onChanged(WaitingForAppSelection)
    }

    @Test
    fun `Inserts apps filesystem DB entry if not yet present in DB`() {
        appsFsm.setState(WaitingForAppSelection)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockSessionDao.findAppsSession(app.name))
                .thenReturn(listOf(appSession))
        whenever(mockBuildWrapper.getArchType())
                .thenReturn("")
        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf())
                .thenReturn(listOf(appsFilesystem))

        runBlocking { appsFsm.submitEvent(AppSelected(app)) }

        verify(mockStateObserver).onChanged(FetchingDatabaseEntries)
        verify(mockStateObserver).onChanged(DatabaseEntriesFetched(appsFilesystem, appSession))
        verify(mockFilesystemDao, times(2)).findAppsFilesystemByType(app.filesystemRequired)
        verify(mockFilesystemDao).insertFilesystem(appsFilesystem)
    }

    @Test
    fun `Inserts app session DB entry if not yet present in DB`() {
        appsFsm.setState(WaitingForAppSelection)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(appsFilesystem))
        whenever(mockSessionDao.findAppsSession(app.name))
                .thenReturn(listOf())
                .thenReturn(listOf(appSession))

        runBlocking { appsFsm.submitEvent(AppSelected(app)) }

        verify(mockStateObserver).onChanged(FetchingDatabaseEntries)
        verify(mockStateObserver).onChanged(DatabaseEntriesFetched(appsFilesystem, appSession))
        verify(mockSessionDao, times(2)).findAppsSession(app.name)
        verify(mockSessionDao).insertSession(appSession)
    }

    @Test
    fun `Fetches database entries when app selected`() {
        appsFsm.setState(WaitingForAppSelection)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf(appsFilesystem))
        whenever(mockSessionDao.findAppsSession(app.name))
                .thenReturn(listOf(appSession))

        runBlocking { appsFsm.submitEvent(AppSelected(app)) }

        verify(mockStateObserver).onChanged(FetchingDatabaseEntries)
        verify(mockStateObserver).onChanged(DatabaseEntriesFetched(appsFilesystem, appSession))
    }

    @Test
    fun `Posts failure state if database fetching fails`() {
        appsFsm.setState(WaitingForAppSelection)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockBuildWrapper.getArchType())
                .thenReturn("")
        whenever(mockFilesystemDao.findAppsFilesystemByType(app.filesystemRequired))
                .thenReturn(listOf())
                .thenReturn(listOf()) // Simulate failure to retrieve previous insertion

        runBlocking { appsFsm.submitEvent(AppSelected(app)) }

        verify(mockFilesystemDao, times(2)).findAppsFilesystemByType(app.filesystemRequired)
        verify(mockFilesystemDao).insertFilesystem(appsFilesystem)
        verify(mockStateObserver).onChanged(FetchingDatabaseEntries)
        verify(mockStateObserver).onChanged(DatabaseEntriesFetchFailed)
    }

    @Test
    fun `Requires credentials to be set if username is missing`() {
        appsFsm.setState(DatabaseEntriesFetched(appsFilesystem, appSession))
        appsFsm.getState().observeForever(mockStateObserver)

        val filesystemWithoutUsername = appsFilesystemWithCredentials
        filesystemWithoutUsername.defaultUsername = ""
        runBlocking { appsFsm.submitEvent(CheckAppsFilesystemCredentials(filesystemWithoutUsername)) }

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(filesystemWithoutUsername))
    }

    @Test
    fun `Requires credentials to be set if password is missing`() {
        appsFsm.setState(DatabaseEntriesFetched(appsFilesystem, appSession))
        appsFsm.getState().observeForever(mockStateObserver)

        val filesystemWithoutPassword = appsFilesystemWithCredentials
        filesystemWithoutPassword.defaultPassword = ""
        runBlocking { appsFsm.submitEvent(CheckAppsFilesystemCredentials(filesystemWithoutPassword)) }

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(filesystemWithoutPassword))
    }

    @Test
    fun `Requires credentials to be set if vnc password is missing`() {
        appsFsm.setState(DatabaseEntriesFetched(appsFilesystem, appSession))
        appsFsm.getState().observeForever(mockStateObserver)

        val filesystemWithoutVncPassword = appsFilesystemWithCredentials
        filesystemWithoutVncPassword.defaultVncPassword = ""
        runBlocking { appsFsm.submitEvent(CheckAppsFilesystemCredentials(filesystemWithoutVncPassword)) }

        verify(mockStateObserver).onChanged(AppsFilesystemRequiresCredentials(filesystemWithoutVncPassword))
    }

    @Test
    fun `State is AppsFilesystemHasCredentials if they are set`() {
        appsFsm.setState(DatabaseEntriesFetched(appsFilesystem, appSession))
        appsFsm.getState().observeForever(mockStateObserver)

        runBlocking { appsFsm.submitEvent(CheckAppsFilesystemCredentials(appsFilesystemWithCredentials)) }

        verify(mockStateObserver).onChanged(AppsFilesystemHasCredentials)
    }

    @Test
    fun `Sets credentials and updates state to AppsFilesystemHasCredentials on event submission`() {
        appsFsm.setState(AppsFilesystemRequiresCredentials(appsFilesystem))
        appsFsm.getState().observeForever(mockStateObserver)

        runBlocking { appsFsm.submitEvent(SubmitAppsFilesystemCredentials(appsFilesystem, defaultUsername, defaultPassword, defaultPassword)) }

        verify(mockFilesystemDao).updateFilesystem(appsFilesystemWithCredentials)
        verify(mockStateObserver).onChanged(AppsFilesystemHasCredentials)
    }

    @Test
    fun `State is AppServiceTypePreferenceSet if already set`() {
        appsFsm.setState(AppsFilesystemHasCredentials)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockAppsPreferences.getAppServiceTypePreference(app))
                .thenReturn(SshTypePreference)

        runBlocking { appsFsm.submitEvent(CheckAppServicePreference(app)) }

        verify(mockStateObserver).onChanged(AppHasServiceTypePreferenceSet)
    }

    @Test
    fun `State is AppRequiresServiceTypePreference is it is not set`() {
        appsFsm.setState(AppsFilesystemHasCredentials)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockAppsPreferences.getAppServiceTypePreference(app))
                .thenReturn(PreferenceHasNotBeenSelected)

        runBlocking { appsFsm.submitEvent(CheckAppServicePreference(app)) }

        verify(mockStateObserver).onChanged(AppRequiresServiceTypePreference)
    }

    @Test
    fun `State is CopySucceeded`() {
        appsFsm.setState(AppHasServiceTypePreferenceSet)
        appsFsm.getState().observeForever(mockStateObserver)

        runBlocking { appsFsm.submitEvent(CopyAppScriptToFilesystem(app, appsFilesystem)) }

        verify(mockStateObserver).onChanged(CopyingAppScript)
        verify(mockStateObserver).onChanged(AppScriptCopySucceeded)
    }

    @Test
    fun `State is CopyFailed`() {
        appsFsm.setState(AppHasServiceTypePreferenceSet)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.moveAppScriptToRequiredLocation(app.name, appsFilesystem))
                .thenThrow(Exception())

        runBlocking { appsFsm.submitEvent(CopyAppScriptToFilesystem(app, appsFilesystem)) }

        verify(mockStateObserver).onChanged(CopyingAppScript)
        verify(mockStateObserver).onChanged(AppScriptCopyFailed)
    }

    @Test
    fun `Syncs database entries correctly for ssh sessions`() {
        appsFsm.setState(AppScriptCopySucceeded)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockAppsPreferences.getAppServiceTypePreference(app))
                .thenReturn(SshTypePreference)

        runBlocking { appsFsm.submitEvent(SyncDatabaseEntries(app, appSession, appsFilesystemWithCredentials)) }

        val updatedAppSession = appSession
        updatedAppSession.filesystemId = appsFilesystemWithCredentials.id
        updatedAppSession.filesystemName = appsFilesystemWithCredentials.name
        updatedAppSession.serviceType = "ssh"
        updatedAppSession.port = 2022
        updatedAppSession.username = appsFilesystemWithCredentials.defaultUsername
        updatedAppSession.password = appsFilesystemWithCredentials.defaultPassword
        updatedAppSession.vncPassword = appsFilesystemWithCredentials.defaultVncPassword

        verify(mockSessionDao).updateSession(updatedAppSession)
        verify(mockStateObserver).onChanged(SyncingDatabaseEntries)
        verify(mockStateObserver).onChanged(AppDatabaseEntriesSynced)
    }

    @Test
    fun `Syncs database entries correctly for vnc sessions`() {
        appsFsm.setState(AppScriptCopySucceeded)
        appsFsm.getState().observeForever(mockStateObserver)

        whenever(mockAppsPreferences.getAppServiceTypePreference(app))
                .thenReturn(VncTypePreference)

        runBlocking { appsFsm.submitEvent(SyncDatabaseEntries(app, appSession, appsFilesystemWithCredentials)) }

        val updatedAppSession = appSession
        updatedAppSession.filesystemId = appsFilesystemWithCredentials.id
        updatedAppSession.filesystemName = appsFilesystemWithCredentials.name
        updatedAppSession.serviceType = "vnc"
        updatedAppSession.port = 51
        updatedAppSession.username = appsFilesystemWithCredentials.defaultUsername
        updatedAppSession.password = appsFilesystemWithCredentials.defaultPassword
        updatedAppSession.vncPassword = appsFilesystemWithCredentials.defaultVncPassword

        verify(mockSessionDao).updateSession(updatedAppSession)
        verify(mockStateObserver).onChanged(SyncingDatabaseEntries)
        verify(mockStateObserver).onChanged(AppDatabaseEntriesSynced)
    }
}