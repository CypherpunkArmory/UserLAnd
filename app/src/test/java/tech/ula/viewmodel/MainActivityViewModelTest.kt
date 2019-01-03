package tech.ula.viewmodel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.*
import tech.ula.utils.SshTypePreference

@RunWith(MockitoJUnitRunner::class)
class MainActivityViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var mockAppsStartupFsm: AppsStartupFsm

    @Mock lateinit var mockSessionStartupFsm: SessionStartupFsm

    @Mock lateinit var  mockStateObserver: Observer<State>

    private lateinit var appsStartupStateLiveData: MutableLiveData<AppsStartupState>

    private lateinit var sessionStartupStateLiveData: MutableLiveData<SessionStartupState>

    private lateinit var mainActivityViewModel: MainActivityViewModel

    private val selectedApp = App(name = "app")
    private val selectedSession = Session(id = 0, filesystemId = 0)
    private val selectedFilesystem = Filesystem(0)

    private val unselectedApp = App(name = "UNSELECTED")
    private val unselectedSession = Session(id = -1, name = "UNSELECTED", filesystemId = -1)
    private val unselectedFilesystem = Filesystem(id = -1, name = "UNSELECTED")

    private fun makeAppSelections() {
        mainActivityViewModel.lastSelectedApp = selectedApp
        mainActivityViewModel.lastSelectedSession = selectedSession
        mainActivityViewModel.lastSelectedFilesystem = selectedFilesystem
    }

    private fun makeSessionSelections() {
        mainActivityViewModel.lastSelectedSession = selectedSession
        mainActivityViewModel.lastSelectedFilesystem = selectedFilesystem
    }

    @Before
    fun setup() {
        appsStartupStateLiveData = MutableLiveData()
        whenever(mockAppsStartupFsm.getState()).thenReturn(appsStartupStateLiveData)
        appsStartupStateLiveData.postValue(WaitingForAppSelection)

        sessionStartupStateLiveData = MutableLiveData()
        whenever(mockSessionStartupFsm.getState()).thenReturn(sessionStartupStateLiveData)
        sessionStartupStateLiveData.postValue(WaitingForSessionSelection)

        mainActivityViewModel = MainActivityViewModel(mockAppsStartupFsm, mockSessionStartupFsm)
        mainActivityViewModel.getState().observeForever(mockStateObserver)
    }

    @Test
    fun `State does not initially publish onChanged event`() {
        verify(mockStateObserver, never()).onChanged(any())
    }

    @Test
    fun `Restarts app setup after permissions are granted if an app was selected first`() {
        mainActivityViewModel.waitForPermissions(appToContinue = selectedApp)
        mainActivityViewModel.permissionsHaveBeenGranted()
        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(AppSelected(selectedApp))
        }
    }

    @Test
    fun `Restarts session setup after permissions are granted if a session was selected first`() {
        mainActivityViewModel.waitForPermissions(sessionToContinue = selectedSession)
        mainActivityViewModel.permissionsHaveBeenGranted()
        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(SessionSelected(selectedSession))
        }
    }

    @Test
    fun `Posts IllegalState if both a session and app have been selected when permissions are granted`() {
        mainActivityViewModel.waitForPermissions(selectedApp, selectedSession)
        mainActivityViewModel.permissionsHaveBeenGranted()

        verify(mockStateObserver).onChanged(IllegalState("Both a session and an app have been selected when permissions are granted"))
    }

    @Test
    fun `Posts IllegalState if neither a session nor an app has been selected when permissions are granted`() {
        mainActivityViewModel.waitForPermissions()
        mainActivityViewModel.permissionsHaveBeenGranted()

        verify(mockStateObserver).onChanged(IllegalState("Neither a session nor app have been selected when permissions are granted."))
    }

    @Test
    fun `Apps and sessions cannot be selected if apps state is not WaitingForAppSelection`() {
        appsStartupStateLiveData.postValue(FetchingDatabaseEntries)

        mainActivityViewModel.submitAppSelection(selectedApp)
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockAppsStartupFsm, never()).submitEvent(AppSelected(selectedApp))
            verify(mockSessionStartupFsm, never()).submitEvent(SessionSelected(selectedSession))
        }
    }

    @Test
    fun `Apps and sessions cannot be selected if session state is not WaitingForSessionSelection`() {
        sessionStartupStateLiveData.postValue(SessionIsReadyForPreparation(selectedSession, Filesystem(id = 0)))

        mainActivityViewModel.submitAppSelection(selectedApp)
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockAppsStartupFsm, never()).submitEvent(AppSelected(selectedApp))
            verify(mockSessionStartupFsm, never()).submitEvent(SessionSelected(selectedSession))
        }
    }

    @Test
    fun `Submits app selection if selections can be made`() {
        // App and session state are initialized to waiting for selection
        mainActivityViewModel.submitAppSelection(selectedApp)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(AppSelected(selectedApp))
        }
    }

    @Test
    fun `Submits session selection if selections can be made`() {
        // App and session state are initialized to waiting for selection
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(SessionSelected(selectedSession))
        }
    }

    @Test
    fun `Submits completed download ids`() {
        mainActivityViewModel.submitCompletedDownloadId(1)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(AssetDownloadComplete(1))
        }
    }

    @Test
    fun `Posts IllegalState if filesystem credentials are submitted while no filesystem is selected`() {
        mainActivityViewModel.submitFilesystemCredentials("", "", "")

        verify(mockStateObserver).onChanged(IllegalState("Submitting credentials for an unselected filesystem"))
    }

    @Test
    fun `Submits filesystem credentials for last selected filesystem`() {
        val username = "user"
        val password = "pass"
        val vncPassword = "vnc"
        mainActivityViewModel.lastSelectedFilesystem = selectedFilesystem

        mainActivityViewModel.submitFilesystemCredentials(username, password, vncPassword)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(SubmitAppsFilesystemCredentials(selectedFilesystem, username, password, vncPassword))
        }
    }

    @Test
    fun `Posts IllegalState if service preference submitted while no app is selected`() {
        mainActivityViewModel.submitAppServicePreference(SshTypePreference)

        verify(mockStateObserver).onChanged(IllegalState("Submitting a preference for an unselected app"))
    }

    @Test
    fun `Submits app service preference for last selected app`() {
        mainActivityViewModel.lastSelectedApp = selectedApp

        mainActivityViewModel.submitAppServicePreference(SshTypePreference)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(SubmitAppServicePreference(selectedApp, SshTypePreference))
        }
    }

    @Test
    fun `Resets startup state when user input is cancelled`() {
        mainActivityViewModel.lastSelectedApp = selectedApp
        mainActivityViewModel.lastSelectedSession = selectedSession
        mainActivityViewModel.lastSelectedFilesystem = selectedFilesystem

        mainActivityViewModel.handleUserInputCancelled()

        assertEquals(mainActivityViewModel.lastSelectedApp, unselectedApp)
        assertEquals(mainActivityViewModel.lastSelectedSession, unselectedSession)
        assertEquals(mainActivityViewModel.lastSelectedFilesystem, unselectedFilesystem)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(ResetAppState)
            verify(mockSessionStartupFsm).submitEvent(ResetSessionState)
        }
    }

    @Test
    fun `Submits DownloadAssets event`() {
        val downloads = listOf(Asset(name = "asset", architectureType = "arch", distributionType = "dist", remoteTimestamp = 0))

        mainActivityViewModel.startAssetDownloads(downloads)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(DownloadAssets(downloads))
        }
    }

    @Test
    fun `Does not post IllegalState if app, session, and filesystem have not been selected and observed event is WaitingForAppSelection`() {
        appsStartupStateLiveData.postValue(WaitingForAppSelection)

        verify(mockStateObserver, never()).onChanged(IllegalState("Trying to handle app preparation before it has been selected."))
    }

    @Test
    fun `Does not post IllegalState if app, session, and filesystem have not been selected and observed event is FetchingDatabaseEntries`() {
        appsStartupStateLiveData.postValue(FetchingDatabaseEntries)

        verify(mockStateObserver, never()).onChanged(IllegalState("Trying to handle app preparation before it has been selected."))
    }

    @Test
    fun `Posts IllegalState if app, session, and filesystem have not been selected and an app state event is observed that is not the above`() {
        appsStartupStateLiveData.postValue(DatabaseEntriesFetchFailed)

        verify(mockStateObserver).onChanged(IllegalState("Trying to handle app preparation before it has been selected."))
    }

    @Test
    fun `Posts IllegalState on incorrect app transitions`() {
        makeAppSelections()

        val event = SubmitAppServicePreference(selectedApp, SshTypePreference)
        val state = WaitingForAppSelection
        val badTransition = IncorrectAppTransition(event, state)
        appsStartupStateLiveData.postValue(IncorrectAppTransition(event, state))

        verify(mockStateObserver).onChanged(IllegalState("Bad state transition: $badTransition"))
    }

    @Test
    fun `Updates last selected session and filesystem and submits check credentials event when app database entries are fetched`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(DatabaseEntriesFetched(selectedFilesystem, selectedSession))

        assertEquals(selectedFilesystem, mainActivityViewModel.lastSelectedFilesystem)
        assertEquals(selectedSession, mainActivityViewModel.lastSelectedSession)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(CheckAppsFilesystemCredentials(selectedFilesystem))
        }
    }

    @Test
    fun `Posts IllegalState if app database entry fetch fails`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(DatabaseEntriesFetchFailed)

        verify(mockStateObserver).onChanged(IllegalState("Couldn't fetch apps database entries."))
    }

    @Test
    fun `Submit preference check event if observed event is filesystem has credentials`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppsFilesystemHasCredentials)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(CheckAppServicePreference(selectedApp))
        }
    }

    @Test
    fun `Posts FilesystemCredentialsRequired if observed event is filesystem requires credentials`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppsFilesystemRequiresCredentials(selectedFilesystem))

        verify(mockStateObserver).onChanged(FilesystemCredentialsRequired)
    }

    @Test
    fun `Submits CopyAppScript event if observed event is app has service preference set`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppHasServiceTypePreferenceSet)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(CopyAppScriptToFilesystem(selectedApp, selectedFilesystem))
        }
    }

    @Test
    fun `Posts PreferenceRequired if equivalent event observed`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppRequiresServiceTypePreference)

        verify(mockStateObserver).onChanged(AppServiceTypePreferenceRequired)
    }

    @Test
    fun `Submits SyncDataBaseEntries when observed event is app script copying succeeded`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppScriptCopySucceeded)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(SyncDatabaseEntries(selectedApp, selectedSession, selectedFilesystem))
        }
    }

    @Test
    fun `Posts IllegalState if app script copy fails`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppScriptCopyFailed)

        verify(mockStateObserver).onChanged(IllegalState("Couldn't copy app script."))
    }

    @Test
    fun `Updates session and filesystem and submits session selected event once database entries sync`() {
        makeAppSelections()

        val updatedSession = Session(0, name = "updated", filesystemId = 0)
        val updatedFilesystem = Filesystem(0, name = "updated")
        appsStartupStateLiveData.postValue(AppDatabaseEntriesSynced(selectedApp, updatedSession, updatedFilesystem))

        assertEquals(updatedSession, mainActivityViewModel.lastSelectedSession)
        assertEquals(updatedFilesystem, mainActivityViewModel.lastSelectedFilesystem)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(SessionSelected(updatedSession))
        }
    }
}