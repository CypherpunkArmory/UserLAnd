package tech.ula.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.Session
import tech.ula.model.repositories.DownloadMetadata
import tech.ula.model.state.AppDatabaseEntriesSynced
import tech.ula.model.state.AppHasServiceTypeSet
import tech.ula.model.state.AppRequiresServiceType
import tech.ula.model.state.AppScriptCopyFailed
import tech.ula.model.state.AppScriptCopySucceeded
import tech.ula.model.state.AppSelected
import tech.ula.model.state.AppsFilesystemHasCredentials
import tech.ula.model.state.AppsFilesystemRequiresCredentials
import tech.ula.model.state.AppsStartupFsm
import tech.ula.model.state.AppsStartupState
import tech.ula.model.state.AssetDownloadComplete
import tech.ula.model.state.AssetListsRetrievalFailed
import tech.ula.model.state.AssetListsRetrievalSucceeded
import tech.ula.model.state.AssetsAreMissingFromSupportDirectories
import tech.ula.model.state.AttemptedCacheAccessWhileEmpty
import tech.ula.model.state.CheckAppSessionServiceType
import tech.ula.model.state.CheckAppsFilesystemCredentials
import tech.ula.model.state.CopyAppScriptToFilesystem
import tech.ula.model.state.CopyDownloadsToLocalStorage
import tech.ula.model.state.CopyingFilesToLocalDirectories
import tech.ula.model.state.DatabaseEntriesFetchFailed
import tech.ula.model.state.DatabaseEntriesFetched
import tech.ula.model.state.DownloadAssets
import tech.ula.model.state.DownloadingAssets
import tech.ula.model.state.DownloadsHaveFailed
import tech.ula.model.state.DownloadsHaveSucceeded
import tech.ula.model.state.DownloadsRequired
import tech.ula.model.state.ExtractFilesystem
import tech.ula.model.state.ExtractingFilesystem
import tech.ula.model.state.ExtractionFailed
import tech.ula.model.state.ExtractionHasCompletedSuccessfully
import tech.ula.model.state.FetchingDatabaseEntries
import tech.ula.model.state.FilesystemAssetCopyFailed
import tech.ula.model.state.FilesystemAssetVerificationSucceeded
import tech.ula.model.state.GenerateDownloads
import tech.ula.model.state.GeneratingDownloadRequirements
import tech.ula.model.state.IncorrectAppTransition
import tech.ula.model.state.IncorrectSessionTransition
import tech.ula.model.state.LocalDirectoryCopyFailed
import tech.ula.model.state.LocalDirectoryCopySucceeded
import tech.ula.model.state.NoDownloadsRequired
import tech.ula.model.state.ResetAppState
import tech.ula.model.state.ResetSessionState
import tech.ula.model.state.RetrieveAssetLists
import tech.ula.model.state.RetrievingAssetLists
import tech.ula.model.state.SessionIsReadyForPreparation
import tech.ula.model.state.SessionIsRestartable
import tech.ula.model.state.SessionSelected
import tech.ula.model.state.SessionStartupFsm
import tech.ula.model.state.SessionStartupState
import tech.ula.model.state.SingleSessionSupported
import tech.ula.model.state.StorageVerificationCompletedSuccessfully
import tech.ula.model.state.SubmitAppSessionServiceType
import tech.ula.model.state.SubmitAppsFilesystemCredentials
import tech.ula.model.state.SyncDatabaseEntries
import tech.ula.model.state.SyncDownloadState
import tech.ula.model.state.VerifyAvailableStorage
import tech.ula.model.state.VerifyFilesystemAssets
import tech.ula.model.state.VerifyingFilesystemAssets
import tech.ula.model.state.WaitingForAppSelection
import tech.ula.model.state.WaitingForSessionSelection
import tech.ula.utils.AssetFileClearer
import tech.ula.utils.DownloadFailureLocalizationData
import tech.ula.utils.Logger
import java.io.FileNotFoundException

@RunWith(MockitoJUnitRunner::class)
class MainActivityViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var mockAppsStartupFsm: AppsStartupFsm

    @Mock lateinit var mockSessionStartupFsm: SessionStartupFsm

    @Mock lateinit var mockAssetFileClearer: AssetFileClearer

    @Mock lateinit var mockLogger: Logger

    @Mock lateinit var mockStateObserver: Observer<State>

    // Test variables
    private val selectedApp = App(name = "app")
    private val selectedSession = Session(id = 0, filesystemId = 0)
    private val selectedFilesystem = Filesystem(0)

    private val unselectedApp = App(name = "UNSELECTED")
    private val unselectedSession = Session(id = -1, name = "UNSELECTED", filesystemId = -1)
    private val unselectedFilesystem = Filesystem(id = -1, name = "UNSELECTED")

    private val asset = Asset(name = "asset", type = "dist")
    private val assetList = listOf(asset)

    private val downloadMetadata = DownloadMetadata("asset", "type", "v0", "url")
    private val downloadList = listOf(downloadMetadata)

    private lateinit var appsStartupStateLiveData: MutableLiveData<AppsStartupState>

    private lateinit var sessionStartupStateLiveData: MutableLiveData<SessionStartupState>

    private lateinit var mainActivityViewModel: MainActivityViewModel

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

        mainActivityViewModel = MainActivityViewModel(mockAppsStartupFsm, mockSessionStartupFsm, mockLogger)
        mainActivityViewModel.getState().observeForever(mockStateObserver)
    }

    @Test
    fun `State is initially WaitingForInput`() {
        verify(mockStateObserver).onChanged(WaitingForInput)
    }

    @Test
    fun `Handling onResume submits SyncDownloadState session event`() {
        mainActivityViewModel.handleOnResume()

        verify(mockSessionStartupFsm).submitEvent(SyncDownloadState, mainActivityViewModel)
    }

    @Test
    fun `Restarts app setup after permissions are granted if an app was selected first`() {
        mainActivityViewModel.waitForPermissions(appToContinue = selectedApp)
        mainActivityViewModel.permissionsHaveBeenGranted()
        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(AppSelected(selectedApp), mainActivityViewModel)
        }
    }

    @Test
    fun `Restarts session setup after permissions are granted if a session was selected first`() {
        mainActivityViewModel.waitForPermissions(sessionToContinue = selectedSession)
        mainActivityViewModel.permissionsHaveBeenGranted()
        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(SessionSelected(selectedSession), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if both a session and app have been selected when permissions are granted`() {
        mainActivityViewModel.waitForPermissions(selectedApp, selectedSession)
        mainActivityViewModel.permissionsHaveBeenGranted()

        verify(mockStateObserver).onChanged(TooManySelectionsMadeWhenPermissionsGranted)
        verify(mockLogger).sendIllegalStateLog(TooManySelectionsMadeWhenPermissionsGranted)
    }

    @Test
    fun `Posts IllegalState if neither a session nor an app has been selected when permissions are granted`() {
        mainActivityViewModel.waitForPermissions()
        mainActivityViewModel.permissionsHaveBeenGranted()

        verify(mockStateObserver).onChanged(NoSelectionsMadeWhenPermissionsGranted)
        verify(mockLogger).sendIllegalStateLog(NoSelectionsMadeWhenPermissionsGranted)
    }

    @Test
    fun `Apps and sessions cannot be selected if apps state is not WaitingForAppSelection`() {
        appsStartupStateLiveData.postValue(FetchingDatabaseEntries)

        mainActivityViewModel.submitAppSelection(selectedApp)
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockAppsStartupFsm, never()).submitEvent(AppSelected(selectedApp), mainActivityViewModel)
            verify(mockSessionStartupFsm, never()).submitEvent(SessionSelected(selectedSession), mainActivityViewModel)
        }
    }

    @Test
    fun `Apps and sessions cannot be selected if session state is not WaitingForSessionSelection`() {
        sessionStartupStateLiveData.postValue(SessionIsReadyForPreparation(selectedSession, Filesystem(id = 0)))

        mainActivityViewModel.submitAppSelection(selectedApp)
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockAppsStartupFsm, never()).submitEvent(AppSelected(selectedApp), mainActivityViewModel)
            verify(mockSessionStartupFsm, never()).submitEvent(SessionSelected(selectedSession), mainActivityViewModel)
        }
    }

    @Test
    fun `Submits app selection if selections can be made`() {
        // App and session state are initialized to waiting for selection
        mainActivityViewModel.submitAppSelection(selectedApp)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(AppSelected(selectedApp), mainActivityViewModel)
        }
    }

    @Test
    fun `Submits session selection if selections can be made`() {
        // App and session state are initialized to waiting for selection
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(SessionSelected(selectedSession), mainActivityViewModel)
        }
    }

    @Test
    fun `Submits completed download ids`() {
        mainActivityViewModel.submitCompletedDownloadId(1)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(AssetDownloadComplete(1), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if filesystem credentials are submitted while no filesystem is selected`() {
        mainActivityViewModel.submitFilesystemCredentials("", "", "")

        verify(mockStateObserver).onChanged(NoFilesystemSelectedWhenCredentialsSubmitted)
        verify(mockLogger).sendIllegalStateLog(NoFilesystemSelectedWhenCredentialsSubmitted)
    }

    @Test
    fun `Submits filesystem credentials for last selected filesystem`() {
        val username = "user"
        val password = "pass"
        val vncPassword = "vnc"
        mainActivityViewModel.lastSelectedFilesystem = selectedFilesystem

        mainActivityViewModel.submitFilesystemCredentials(username, password, vncPassword)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(SubmitAppsFilesystemCredentials(selectedFilesystem, username, password, vncPassword), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if service preference submitted while no app is selected`() {
        mainActivityViewModel.submitAppServiceType(ServiceType.Ssh)

        verify(mockStateObserver).onChanged(NoAppSelectedWhenPreferenceSubmitted)
        verify(mockLogger).sendIllegalStateLog(NoAppSelectedWhenPreferenceSubmitted)
    }

    @Test
    fun `Submits app service preference for last selected app`() {
        mainActivityViewModel.lastSelectedApp = selectedApp

        mainActivityViewModel.submitAppServiceType(ServiceType.Ssh)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(SubmitAppSessionServiceType(selectedApp, ServiceType.Ssh), mainActivityViewModel)
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
            verify(mockStateObserver, times(2)).onChanged(WaitingForInput)
            verify(mockAppsStartupFsm).submitEvent(ResetAppState, mainActivityViewModel)
            verify(mockSessionStartupFsm).submitEvent(ResetSessionState, mainActivityViewModel)
        }
    }

    @Test
    fun `Submits DownloadAssets event`() {
        mainActivityViewModel.startAssetDownloads(downloadList)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(DownloadAssets(downloadList), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts ActiveSessionsMustBeDeactivatedState if handling clear support files with active session`() = runBlocking {
        whenever(mockSessionStartupFsm.sessionsAreActive())
                .thenReturn(true)

        mainActivityViewModel.handleClearSupportFiles(mockAssetFileClearer)

        verify(mockStateObserver).onChanged(ActiveSessionsMustBeDeactivated)
    }

    @Test
    fun `handleSessionHasBeenActivated resets startup state`() {
        mainActivityViewModel.lastSelectedApp = selectedApp
        mainActivityViewModel.lastSelectedSession = selectedSession
        mainActivityViewModel.lastSelectedFilesystem = selectedFilesystem

        mainActivityViewModel.handleSessionHasBeenActivated()

        assertEquals(mainActivityViewModel.lastSelectedApp, unselectedApp)
        assertEquals(mainActivityViewModel.lastSelectedSession, unselectedSession)
        assertEquals(mainActivityViewModel.lastSelectedFilesystem, unselectedFilesystem)

        verify(mockStateObserver, times(2)).onChanged(WaitingForInput)
        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(ResetAppState, mainActivityViewModel)
            verify(mockSessionStartupFsm).submitEvent(ResetSessionState, mainActivityViewModel)
        }
    }

    @Test
    fun `Posts ClearingSupportFiles and ProgressBarOperationComplete if clearing succeeds`() = runBlocking {
        whenever(mockSessionStartupFsm.sessionsAreActive())
                .thenReturn(false)

        mainActivityViewModel.handleClearSupportFiles(mockAssetFileClearer)

        verify(mockAssetFileClearer).clearAllSupportAssets()
        verify(mockStateObserver).onChanged(ClearingSupportFiles)
        verify(mockStateObserver).onChanged(ProgressBarOperationComplete)
    }

    @Test
    fun `Posts ClearingSupportFiles and FailedToClearSupportFiles if clearing fails`() = runBlocking {
        whenever(mockSessionStartupFsm.sessionsAreActive())
                .thenReturn(false)
        whenever(mockAssetFileClearer.clearAllSupportAssets())
                .thenThrow(FileNotFoundException())

        mainActivityViewModel.handleClearSupportFiles(mockAssetFileClearer)

        verify(mockAssetFileClearer).clearAllSupportAssets()
        verify(mockStateObserver).onChanged(ClearingSupportFiles)
        verify(mockStateObserver).onChanged(FailedToClearSupportFiles)
        verify(mockLogger).sendIllegalStateLog(FailedToClearSupportFiles)
    }

    @Test
    fun `Posts BusyboxMissing if busybox is missing during clearing`() = runBlocking {
        whenever(mockSessionStartupFsm.sessionsAreActive())
                .thenReturn(false)
        whenever(mockAssetFileClearer.clearAllSupportAssets())
                .thenThrow(IllegalStateException())

        mainActivityViewModel.handleClearSupportFiles(mockAssetFileClearer)

        verify(mockAssetFileClearer).clearAllSupportAssets()
        verify(mockStateObserver).onChanged(ClearingSupportFiles)
        verify(mockStateObserver).onChanged(BusyboxMissing)
        verify(mockLogger).sendIllegalStateLog(BusyboxMissing)
    }

    // Private function tests.

    @Test
    fun `Does not post IllegalState if app, session, and filesystem have not been selected and observed event is WaitingForAppSelection`() {
        appsStartupStateLiveData.postValue(WaitingForAppSelection)

        verify(mockStateObserver, never()).onChanged(NoAppSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Does not post IllegalState if app, session, and filesystem have not been selected and observed event is FetchingDatabaseEntries`() {
        appsStartupStateLiveData.postValue(FetchingDatabaseEntries)

        verify(mockStateObserver, never()).onChanged(NoAppSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Posts IllegalState if app, session, and filesystem have not been selected and an app state event is observed that is not the above`() {
        appsStartupStateLiveData.postValue(DatabaseEntriesFetchFailed)

        verify(mockStateObserver).onChanged(NoAppSelectedWhenTransitionNecessary)
        verify(mockLogger).sendIllegalStateLog(NoAppSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Posts IllegalState on incorrect app transitions`() {
        makeAppSelections()

        val event = SubmitAppSessionServiceType(selectedApp, ServiceType.Ssh)
        val state = WaitingForAppSelection
        val badTransition = IncorrectAppTransition(event, state)
        appsStartupStateLiveData.postValue(IncorrectAppTransition(event, state))

        val expectedState = IllegalStateTransition("$badTransition")
        verify(mockStateObserver).onChanged(expectedState)
        verify(mockLogger).sendIllegalStateLog(expectedState)
    }

    @Test
    fun `Updates last selected session and filesystem and submits check credentials event when app database entries are fetched`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(DatabaseEntriesFetched(selectedFilesystem, selectedSession))

        assertEquals(selectedFilesystem, mainActivityViewModel.lastSelectedFilesystem)
        assertEquals(selectedSession, mainActivityViewModel.lastSelectedSession)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(CheckAppsFilesystemCredentials(selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if app database entry fetch fails`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(DatabaseEntriesFetchFailed)

        verify(mockStateObserver).onChanged(ErrorFetchingAppDatabaseEntries)
        verify(mockLogger).sendIllegalStateLog(ErrorFetchingAppDatabaseEntries)
    }

    @Test
    fun `Submit preference check event if observed event is filesystem has credentials`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppsFilesystemHasCredentials)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(CheckAppSessionServiceType(selectedApp), mainActivityViewModel)
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

        appsStartupStateLiveData.postValue(AppHasServiceTypeSet)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(CopyAppScriptToFilesystem(selectedApp, selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts PreferenceRequired if equivalent event observed`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppRequiresServiceType)

        verify(mockStateObserver).onChanged(AppServiceTypePreferenceRequired)
    }

    @Test
    fun `Submits SyncDataBaseEntries when observed event is app script copying succeeded`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppScriptCopySucceeded)

        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(SyncDatabaseEntries(selectedApp, selectedSession, selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if app script copy fails`() {
        makeAppSelections()

        appsStartupStateLiveData.postValue(AppScriptCopyFailed)

        verify(mockStateObserver).onChanged(ErrorCopyingAppScript)
        verify(mockLogger).sendIllegalStateLog(ErrorCopyingAppScript)
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
            verify(mockSessionStartupFsm).submitEvent(SessionSelected(updatedSession), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if session preparation event is observed that is not WaitingForSelection and prep reqs have not been met`() {
        sessionStartupStateLiveData.postValue(NoDownloadsRequired)

        verify(mockStateObserver).onChanged(NoSessionSelectedWhenTransitionNecessary)
        verify(mockLogger).sendIllegalStateLog(NoSessionSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Posts IllegalState if observes incorrect session transition`() {
        makeSessionSelections()

        val event = SessionSelected(selectedSession)
        val state = SingleSessionSupported
        val badTransition = IncorrectSessionTransition(event, state)
        sessionStartupStateLiveData.postValue(badTransition)

        val expectedState = IllegalStateTransition("$badTransition")
        verify(mockStateObserver).onChanged(expectedState)
        verify(mockLogger).sendIllegalStateLog(expectedState)
    }

    @Test
    fun `Posts CanOnlyStartSingleSession if equivalent event observed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(SingleSessionSupported)

        verify(mockStateObserver).onChanged(CanOnlyStartSingleSession)
    }

    @Test
    fun `Posts SessionIsRestartable when equivalent event observed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(SessionIsRestartable(selectedSession))

        verify(mockStateObserver).onChanged(SessionCanBeRestarted(selectedSession))
    }

    @Test
    fun `Posts NoSessionsSelectedWhenTransitionNecessary if incorrectly transitioning out of SessionIsReadyForPreparation observation`() {
        sessionStartupStateLiveData.postValue(SessionIsReadyForPreparation(unselectedSession, unselectedFilesystem))

        verify(mockStateObserver).onChanged(NoSessionSelectedWhenTransitionNecessary)
        verify(mockLogger).sendIllegalStateLog(NoSessionSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Updates selected session and filesystem, posts StartingSetup, and submits RetrieveAssetLists when session is ready for prep is observed`() {
        sessionStartupStateLiveData.postValue(SessionIsReadyForPreparation(selectedSession, selectedFilesystem))

        assertEquals(selectedSession, mainActivityViewModel.lastSelectedSession)
        assertEquals(selectedFilesystem, mainActivityViewModel.lastSelectedFilesystem)

        verify(mockStateObserver).onChanged(StartingSetup)
        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(RetrieveAssetLists(selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts FetchingAssetLists when equivalent state is observed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(RetrievingAssetLists)

        verify(mockStateObserver).onChanged(FetchingAssetLists)
    }

    @Test
    fun `Posts NoSessionsSelectedWhenTransitionNecessary if AssetRetrievalSucceeded state is observed while no selections have been made`() {
        sessionStartupStateLiveData.postValue(AssetListsRetrievalSucceeded(assetList))

        verify(mockStateObserver).onChanged(NoSessionSelectedWhenTransitionNecessary)
        verify(mockLogger).sendIllegalStateLog(NoSessionSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Submits GenerateDownload event if asset list retrieval success observed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(AssetListsRetrievalSucceeded(assetList))

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(GenerateDownloads(selectedFilesystem, assetList), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if asset list retrieval failure observed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(AssetListsRetrievalFailed)

        verify(mockStateObserver).onChanged(ErrorFetchingAssetLists)
        verify(mockLogger).sendIllegalStateLog(ErrorFetchingAssetLists)
    }

    @Test
    fun `Posts CheckingForAssetsUpdates when generating download requirements is observed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(GeneratingDownloadRequirements)

        verify(mockStateObserver).onChanged(CheckingForAssetsUpdates)
    }

    @Test
    fun `Posts LargeDownloadRequired if downloads are required and include a large one`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(DownloadsRequired(downloadList, largeDownloadRequired = true))

        verify(mockStateObserver).onChanged(LargeDownloadRequired(downloadList))
    }

    @Test
    fun `Submits DownloadAssets event if downloads are required but do not include a large one`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(DownloadsRequired(downloadList, largeDownloadRequired = false))

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(DownloadAssets(downloadList), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts NoSessionSelectedWhenTransitionNecessary if attempted to transition from NoDownloadsRequired while selections have not been made`() {
        sessionStartupStateLiveData.postValue(NoDownloadsRequired)

        verify(mockStateObserver).onChanged(NoSessionSelectedWhenTransitionNecessary)
        verify(mockLogger).sendIllegalStateLog(NoSessionSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Submits VerifyFilesystemAssets event when observing NoDownloadsRequired`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(NoDownloadsRequired)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(VerifyFilesystemAssets(selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts DownloadProgress as it observes requirements downloading`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(DownloadingAssets(0, 0))

        verify(mockStateObserver).onChanged(DownloadProgress(0, 0))
    }

    @Test
    fun `Submits CopyDownloadsToLocalStorage event when observing download success`() {
        sessionStartupStateLiveData.postValue(DownloadsHaveSucceeded)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(CopyDownloadsToLocalStorage, mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState if downloads fail`() {
        makeSessionSelections()

        val reason = DownloadFailureLocalizationData(0)
        sessionStartupStateLiveData.postValue(DownloadsHaveFailed(reason))

        verify(mockStateObserver).onChanged(DownloadsDidNotCompleteSuccessfully(reason))
        verify(mockLogger).sendIllegalStateLog(DownloadsDidNotCompleteSuccessfully(reason))
    }

    @Test
    fun `Posts DownloadCacheAccessedWhileEmpty and resets state if it observes similar state`() {
        sessionStartupStateLiveData.postValue(AttemptedCacheAccessWhileEmpty)

        verify(mockStateObserver).onChanged(DownloadCacheAccessedWhileEmpty)
        verify(mockLogger).sendIllegalStateLog(DownloadCacheAccessedWhileEmpty)
    }

    @Test
    fun `Posts CopyingDownloads when equivalent state is observed`() {
        sessionStartupStateLiveData.postValue(CopyingFilesToLocalDirectories)

        verify(mockStateObserver).onChanged(CopyingDownloads)
    }

    @Test
    fun `Submits VerifyFilesystemAssets event when copying success is observed and session selections have been made`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(LocalDirectoryCopySucceeded)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(VerifyFilesystemAssets(selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts ProgressBarOperationComplete and resets session state when LocalDirectoryCopySucceeded observed while selections have not been made`() {
        sessionStartupStateLiveData.postValue(LocalDirectoryCopySucceeded)

        verify(mockStateObserver).onChanged(ProgressBarOperationComplete)
        assertEquals(unselectedApp, mainActivityViewModel.lastSelectedApp)
        assertEquals(unselectedSession, mainActivityViewModel.lastSelectedSession)
        assertEquals(unselectedFilesystem, mainActivityViewModel.lastSelectedFilesystem)
        runBlocking {
            verify(mockStateObserver, times(2)).onChanged(WaitingForInput)
            verify(mockSessionStartupFsm).submitEvent(ResetSessionState, mainActivityViewModel)
            verify(mockAppsStartupFsm).submitEvent(ResetAppState, mainActivityViewModel)
        }
    }

    @Test
    fun `Posts IllegalState when copying failure is observed`() {
        sessionStartupStateLiveData.postValue(LocalDirectoryCopyFailed)

        verify(mockStateObserver).onChanged(FailedToCopyAssetsToLocalStorage)
        verify(mockLogger).sendIllegalStateLog(FailedToCopyAssetsToLocalStorage)
    }

    @Test
    fun `Posts VerifyingFilesystem when observing verification state`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(VerifyingFilesystemAssets)

        verify(mockStateObserver).onChanged(VerifyingFilesystem)
    }

    @Test
    fun `Posts NoSessionSelectedWhenTransitionNecessary if transitioning from FilesystemAssetVerificationSucceeded while selections have not been made`() {
        sessionStartupStateLiveData.postValue(FilesystemAssetVerificationSucceeded)

        verify(mockStateObserver).onChanged(NoSessionSelectedWhenTransitionNecessary)
        verify(mockLogger).sendIllegalStateLog(NoSessionSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Submits VerifyAvailableStorage when observing FilesystemAssetVerificationSucceeded`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(FilesystemAssetVerificationSucceeded)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(VerifyAvailableStorage(selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Submits ExtractFilesystem when observing StorageVerificationCompletedSuccessfully`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(StorageVerificationCompletedSuccessfully)

        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(ExtractFilesystem(selectedFilesystem), mainActivityViewModel)
        }
    }

    @Test
    fun `Posts AssetsHaveNotBeenDownloaded when observing AssetsAreMissingFromSupportDirectories`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(AssetsAreMissingFromSupportDirectories)

        verify(mockStateObserver).onChanged(AssetsHaveNotBeenDownloaded)
    }

    @Test
    fun `Posts FailedToCopyAssetsToFilesystem when observing FilesystemAssetCopyFailed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(FilesystemAssetCopyFailed)

        verify(mockStateObserver).onChanged(FailedToCopyAssetsToFilesystem)
        verify(mockLogger).sendIllegalStateLog(FailedToCopyAssetsToFilesystem)
    }

    @Test
    fun `Posts FilesystemExtraction when observing filesystem extraction`() {
        makeSessionSelections()

        val target = "bullseye"
        sessionStartupStateLiveData.postValue(ExtractingFilesystem(target))

        verify(mockStateObserver).onChanged(FilesystemExtractionStep(target))
    }

    @Test
    fun `Posts NoSessionSelectedWhenTransitionNecessary when transitioning from ExtractionHasCompletedSuccessfully if no selections have been made`() {
        sessionStartupStateLiveData.postValue(ExtractionHasCompletedSuccessfully)

        verify(mockStateObserver).onChanged(NoSessionSelectedWhenTransitionNecessary)
        verify(mockLogger).sendIllegalStateLog(NoSessionSelectedWhenTransitionNecessary)
    }

    @Test
    fun `Posts SessionCanBeStarted  when observing ExtractionHasCompletedSuccessfully`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(ExtractionHasCompletedSuccessfully)

        verify(mockStateObserver).onChanged(SessionCanBeStarted(selectedSession))
    }

    @Test
    fun `Posts IllegalState when extraction failure is observed`() {
        makeSessionSelections()

        sessionStartupStateLiveData.postValue(ExtractionFailed("reason"))

        verify(mockStateObserver).onChanged(FailedToExtractFilesystem("reason"))
        verify(mockLogger).sendIllegalStateLog(FailedToExtractFilesystem("reason"))
    }
}