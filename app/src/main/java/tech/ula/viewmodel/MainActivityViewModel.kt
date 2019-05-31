package tech.ula.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.DownloadMetadata
import tech.ula.model.state.* // ktlint-disable no-wildcard-imports
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

class MainActivityViewModel(
    private val appsStartupFsm: AppsStartupFsm,
    private val sessionStartupFsm: SessionStartupFsm,
    private val logger: Logger = SentryLogger()
) : ViewModel(), CoroutineScope {

    private var appsAreWaitingForSelection = false
    private var sessionsAreWaitingForSelection = false

    private val unselectedApp = App(name = "UNSELECTED")
    var lastSelectedApp = unselectedApp

    private val unselectedSession = Session(id = -1, name = "UNSELECTED", filesystemId = -1)
    var lastSelectedSession = unselectedSession

    private val unselectedFilesystem = Filesystem(id = -1, name = "UNSELECTED")
    var lastSelectedFilesystem = unselectedFilesystem

    private val appsState = appsStartupFsm.getState()

    private val sessionState = sessionStartupFsm.getState()

    private val state = MediatorLiveData<State>()

    private fun postIllegalStateWithLog(newState: IllegalState) {
        logger.sendIllegalStateLog(newState)
        state.postValue(newState)
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

    init {
        state.addSource(appsState) { it?.let { update ->
            logger.addBreadcrumb("Last observed app state from viewmodel", "$update")
            // Update stateful variables before handling the update so they can be used during it
            if (update !is WaitingForAppSelection) {
                appsAreWaitingForSelection = false
            }
            when (update) {
                is WaitingForAppSelection -> {
                    appsAreWaitingForSelection = true
                }
                is DatabaseEntriesFetched -> {
                    lastSelectedSession = update.appSession
                    lastSelectedFilesystem = update.appsFilesystem
                }
                is AppDatabaseEntriesSynced -> {
                    lastSelectedApp = update.app
                    lastSelectedSession = update.session
                    lastSelectedFilesystem = update.filesystem
                }
            }
            handleAppsPreparationState(update)
        } }
        state.addSource(sessionState) { it?.let { update ->
            logger.addBreadcrumb("Last observed session state from viewmodel", "$update")
            handleSessionPreparationState(update)
        } }
    }

    fun getState(): LiveData<State> {
        return state
    }

    fun handleOnResume() {
        submitSessionStartupEvent(SyncDownloadState)
    }

    fun waitForPermissions(appToContinue: App = unselectedApp, sessionToContinue: Session = unselectedSession) {
        resetStartupState()
        lastSelectedApp = appToContinue
        lastSelectedSession = sessionToContinue
    }

    fun permissionsHaveBeenGranted() {
        when {
            lastSelectedApp != unselectedApp && lastSelectedSession != unselectedSession -> {
                postIllegalStateWithLog(TooManySelectionsMadeWhenPermissionsGranted)
            }
            lastSelectedApp == unselectedApp && lastSelectedSession == unselectedSession -> {
                postIllegalStateWithLog(NoSelectionsMadeWhenPermissionsGranted)
            }
            lastSelectedApp != unselectedApp -> {
                submitAppsStartupEvent(AppSelected(lastSelectedApp))
            }
            lastSelectedSession != unselectedSession -> {
                submitSessionStartupEvent(SessionSelected(lastSelectedSession))
            }
        }
    }

    fun submitAppSelection(app: App) {
        if (!selectionsCanBeMade()) return
        lastSelectedApp = app
        submitAppsStartupEvent(AppSelected(app))
    }

    fun submitSessionSelection(session: Session) {
        if (!selectionsCanBeMade()) return
        lastSelectedSession = session
        submitSessionStartupEvent(SessionSelected(session))
    }

    fun submitCompletedDownloadId(id: Long) {
        submitSessionStartupEvent(AssetDownloadComplete(id))
    }

    fun submitFilesystemCredentials(username: String, password: String, vncPassword: String) {
        if (lastSelectedFilesystem == unselectedFilesystem) {
            postIllegalStateWithLog(NoFilesystemSelectedWhenCredentialsSubmitted)
            return
        }
        submitAppsStartupEvent(SubmitAppsFilesystemCredentials(lastSelectedFilesystem, username, password, vncPassword))
    }

    fun lowAvailableStorageAcknowledged() {
        submitSessionStartupEvent(VerifyAvailableStorageComplete)
    }

    fun submitAppServicePreference(preference: AppServiceTypePreference) {
        if (lastSelectedApp == unselectedApp) {
            postIllegalStateWithLog(NoAppSelectedWhenPreferenceSubmitted)
            return
        }
        submitAppsStartupEvent(SubmitAppServicePreference(lastSelectedApp, preference))
    }

    fun handleUserInputCancelled() {
        resetStartupState()
    }

    // Exposed so that downloads can be continued from activity
    fun startAssetDownloads(downloadRequirements: List<DownloadMetadata>) {
        submitSessionStartupEvent(DownloadAssets(downloadRequirements))
    }

    suspend fun handleClearSupportFiles(assetFileClearer: AssetFileClearer) {
        if (sessionStartupFsm.sessionsAreActive()) {
            state.postValue(ActiveSessionsMustBeDeactivated)
            return
        }
        state.postValue(ClearingSupportFiles)
        try {
            assetFileClearer.clearAllSupportAssets()
            state.postValue(ProgressBarOperationComplete)
        } catch (err: Exception) {
            postIllegalStateWithLog(FailedToClearSupportFiles)
        }
    }

    private fun handleAppsPreparationState(newState: AppsStartupState) {
        // Exit early if we aren't expecting preparation requirements to have been met
        if (newState is WaitingForAppSelection || newState is FetchingDatabaseEntries) {
            return
        }
        if (!appsPreparationRequirementsHaveBeenSelected()) {
            postIllegalStateWithLog(NoAppSelectedWhenTransitionNecessary)
            return
        }
        // Return when statement for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectAppTransition -> {
                postIllegalStateWithLog(IllegalStateTransition("$newState"))
            }
            is WaitingForAppSelection -> {}
            is FetchingDatabaseEntries -> {}
            is DatabaseEntriesFetched -> {
                submitAppsStartupEvent(CheckAppsFilesystemCredentials(lastSelectedFilesystem))
            }
            is DatabaseEntriesFetchFailed -> {
                postIllegalStateWithLog(ErrorFetchingAppDatabaseEntries)
            }
            is AppsFilesystemHasCredentials -> {
                submitAppsStartupEvent(CheckAppServicePreference(lastSelectedApp))
            }
            is AppsFilesystemRequiresCredentials -> {
                state.postValue(FilesystemCredentialsRequired)
            }
            is AppHasServiceTypePreferenceSet -> {
                submitAppsStartupEvent(CopyAppScriptToFilesystem(lastSelectedApp, lastSelectedFilesystem))
            }
            is AppRequiresServiceTypePreference -> {
                state.postValue(AppServiceTypePreferenceRequired)
            }
            is CopyingAppScript -> {}
            is AppScriptCopySucceeded -> {
                submitAppsStartupEvent(SyncDatabaseEntries(lastSelectedApp, lastSelectedSession, lastSelectedFilesystem))
            }
            is AppScriptCopyFailed -> {
                postIllegalStateWithLog(ErrorCopyingAppScript)
            }
            is SyncingDatabaseEntries -> {}
            is AppDatabaseEntriesSynced -> {
                submitSessionStartupEvent(SessionSelected(lastSelectedSession))
            }
        }
    }

    // Post state values and delegate responsibility appropriately
    private fun handleSessionPreparationState(newState: SessionStartupState) {
        // Update stateful variables before handling the update so they can be used during it
        if (newState !is WaitingForSessionSelection) {
            sessionsAreWaitingForSelection = false
        }
        // Return for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectSessionTransition -> {
                postIllegalStateWithLog(IllegalStateTransition("$newState"))
            }
            is WaitingForSessionSelection -> {
                sessionsAreWaitingForSelection = true
            }
            is SingleSessionSupported -> {
                state.postValue(CanOnlyStartSingleSession)
                resetStartupState()
            }
            is SessionIsRestartable -> {
                state.postValue(SessionCanBeRestarted(newState.session))
                resetStartupState()
            }
            is SessionIsReadyForPreparation -> {
                lastSelectedSession = newState.session
                lastSelectedFilesystem = newState.filesystem
                state.postValue(StartingSetup)
                doTransitionIfRequirementsAreSelected {
                    submitSessionStartupEvent(RetrieveAssetLists(lastSelectedFilesystem))
                }
            }
            is AssetRetrievalState -> {
                handleAssetRetrievalState(newState)
            }
            is DownloadRequirementsGenerationState -> {
                handleDownloadRequirementsGenerationState(newState)
            }
            is DownloadingAssetsState -> {
                handleDownloadingAssetsState(newState)
            }
            is CopyingFilesLocallyState -> {
                handleCopyingFilesLocallyState(newState)
            }
            is AssetVerificationState -> {
                handleAssetVerificationState(newState)
            }
            is ExtractionState -> {
                handleExtractionState(newState)
            }
            is StorageVerificationState -> {
                handleStorageVerificationState(newState)
            }
        }
    }

    private fun handleAssetRetrievalState(newState: AssetRetrievalState) {
        return when (newState) {
            is RetrievingAssetLists -> state.postValue(FetchingAssetLists)
            is AssetListsRetrievalSucceeded -> { doTransitionIfRequirementsAreSelected {
                    submitSessionStartupEvent(GenerateDownloads(lastSelectedFilesystem, newState.assetList))
            } }
            is AssetListsRetrievalFailed -> postIllegalStateWithLog(ErrorFetchingAssetLists)
        }
    }

    private fun handleDownloadRequirementsGenerationState(newState: DownloadRequirementsGenerationState) {
        return when (newState) {
            is GeneratingDownloadRequirements -> state.postValue(CheckingForAssetsUpdates)
            is RemoteUnreachableForGeneration -> {
                postIllegalStateWithLog(ErrorGeneratingDownloads(R.string.illegal_state_remote_unreachable_during_generation))
            }
            is DownloadsRequired -> {
                if (newState.largeDownloadRequired) {
                    state.postValue(LargeDownloadRequired(newState.downloadsRequired))
                } else {
                    startAssetDownloads(newState.downloadsRequired)
                }
            }
            is NoDownloadsRequired -> { doTransitionIfRequirementsAreSelected {
                    submitSessionStartupEvent(VerifyFilesystemAssets(lastSelectedFilesystem))
            } }
        }
    }

    private fun handleDownloadingAssetsState(newState: DownloadingAssetsState) {
        return when (newState) {
            is DownloadingAssets -> state.postValue(DownloadProgress(newState.numCompleted, newState.numTotal))
            is DownloadsHaveSucceeded -> submitSessionStartupEvent(CopyDownloadsToLocalStorage)
            is DownloadsHaveFailed -> postIllegalStateWithLog(DownloadsDidNotCompleteSuccessfully(newState.reason))
            is AttemptedCacheAccessWhileEmpty -> {
                postIllegalStateWithLog(DownloadCacheAccessedWhileEmpty)
            }
            is AttemptedCacheAccessInIncorrectState -> {
                postIllegalStateWithLog(DownloadCacheAccessedInAnIncorrectState)
            }
        }
    }

    private fun handleCopyingFilesLocallyState(newState: CopyingFilesLocallyState) {
        return when (newState) {
            is CopyingFilesToLocalDirectories -> state.postValue(CopyingDownloads)
            is LocalDirectoryCopySucceeded -> {
                if (sessionPreparationRequirementsHaveBeenSelected()) {
                    submitSessionStartupEvent(VerifyFilesystemAssets(lastSelectedFilesystem))
                } else {
                    state.postValue(ProgressBarOperationComplete)
                    resetStartupState()
                }
            }
            is LocalDirectoryCopyFailed -> postIllegalStateWithLog(FailedToCopyAssetsToLocalStorage)
        }
    }

    private fun handleAssetVerificationState(newState: AssetVerificationState) {
        return when (newState) {
            is VerifyingFilesystemAssets -> state.postValue(VerifyingFilesystem)
            is FilesystemAssetVerificationSucceeded -> { doTransitionIfRequirementsAreSelected {
                    submitSessionStartupEvent(VerifyAvailableStorage)
            } }
            is AssetsAreMissingFromSupportDirectories -> postIllegalStateWithLog(AssetsHaveNotBeenDownloaded)
            is FilesystemAssetCopyFailed -> postIllegalStateWithLog(FailedToCopyAssetsToFilesystem)
        }
    }

    private fun handleStorageVerificationState(newState: StorageVerificationState) {
        return when (newState) {
            is VerifyingSufficientStorage -> state.postValue(VerifyingAvailableStorage)
            is VerifyingSufficientStorageFailed -> postIllegalStateWithLog(InsufficientAvailableStorage)
            is LowAvailableStorage -> state.postValue(LowStorageAcknowledgementRequired)
            is StorageVerificationCompletedSuccessfully -> { doTransitionIfRequirementsAreSelected {
                submitSessionStartupEvent(ExtractFilesystem(lastSelectedFilesystem))
            } }
        }
    }

    private fun handleExtractionState(newState: ExtractionState) {
        return when (newState) {
            is ExtractingFilesystem -> state.postValue(FilesystemExtractionStep(newState.extractionTarget))
            is ExtractionHasCompletedSuccessfully -> { doTransitionIfRequirementsAreSelected {
                state.postValue(SessionCanBeStarted(lastSelectedSession))
                resetStartupState()
            } }
            is ExtractionFailed -> postIllegalStateWithLog(FailedToExtractFilesystem(newState.reason))
        }
    }

    private fun resetStartupState() {
        lastSelectedApp = unselectedApp
        lastSelectedSession = unselectedSession
        lastSelectedFilesystem = unselectedFilesystem
        submitAppsStartupEvent(ResetAppState)
        submitSessionStartupEvent(ResetSessionState)
    }

    private fun selectionsCanBeMade(): Boolean {
        return appsAreWaitingForSelection && sessionsAreWaitingForSelection
    }

    private fun appsPreparationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedApp != unselectedApp && sessionPreparationRequirementsHaveBeenSelected()
    }

    private fun doTransitionIfRequirementsAreSelected(transition: () -> Unit) {
        if (!sessionPreparationRequirementsHaveBeenSelected()) {
            postIllegalStateWithLog(NoSessionSelectedWhenTransitionNecessary)
            return
        }
        transition()
    }

    private fun sessionPreparationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedSession != unselectedSession && lastSelectedFilesystem != unselectedFilesystem
    }

    private fun submitAppsStartupEvent(event: AppsStartupEvent) {
        logger.addBreadcrumb("Last viewmodel apps event submission", "$event")
        appsStartupFsm.submitEvent(event, this)
    }

    private fun submitSessionStartupEvent(event: SessionStartupEvent) {
        logger.addBreadcrumb("Last viewmodel session event submission", "$event")
        sessionStartupFsm.submitEvent(event, this)
    }
}

sealed class State
object CanOnlyStartSingleSession : State()
data class SessionCanBeStarted(val session: Session) : State()
data class SessionCanBeRestarted(val session: Session) : State()

sealed class IllegalState : State()
data class IllegalStateTransition(val transition: String) : IllegalState()
object TooManySelectionsMadeWhenPermissionsGranted : IllegalState()
object NoSelectionsMadeWhenPermissionsGranted : IllegalState()

object NoFilesystemSelectedWhenCredentialsSubmitted : IllegalState()
object NoAppSelectedWhenPreferenceSubmitted : IllegalState()
object NoAppSelectedWhenTransitionNecessary : IllegalState()
object ErrorFetchingAppDatabaseEntries : IllegalState()
object ErrorCopyingAppScript : IllegalState()

object NoSessionSelectedWhenTransitionNecessary : IllegalState()
object ErrorFetchingAssetLists : IllegalState()
data class ErrorGeneratingDownloads(val errorId: Int) : IllegalState()
data class DownloadsDidNotCompleteSuccessfully(val reason: DownloadFailureLocalizationData) : IllegalState()
object DownloadCacheAccessedWhileEmpty : IllegalState()
object DownloadCacheAccessedInAnIncorrectState : IllegalState()
object FailedToCopyAssetsToLocalStorage : IllegalState()
object AssetsHaveNotBeenDownloaded : IllegalState()
object FailedToCopyAssetsToFilesystem : IllegalState()
data class FailedToExtractFilesystem(val reason: String) : IllegalState()
object FailedToClearSupportFiles : IllegalState()
object InsufficientAvailableStorage : IllegalState()

sealed class UserInputRequiredState : State()
object FilesystemCredentialsRequired : UserInputRequiredState()
object LowStorageAcknowledgementRequired : UserInputRequiredState()
object AppServiceTypePreferenceRequired : UserInputRequiredState()
data class LargeDownloadRequired(val downloadRequirements: List<DownloadMetadata>) : UserInputRequiredState()
object ActiveSessionsMustBeDeactivated : UserInputRequiredState()

sealed class ProgressBarUpdateState : State()
object StartingSetup : ProgressBarUpdateState()
object FetchingAssetLists : ProgressBarUpdateState()
object CheckingForAssetsUpdates : ProgressBarUpdateState()
data class DownloadProgress(val numComplete: Int, val numTotal: Int) : ProgressBarUpdateState()
object CopyingDownloads : ProgressBarUpdateState()
object VerifyingFilesystem : ProgressBarUpdateState()
object VerifyingAvailableStorage : ProgressBarUpdateState()
data class FilesystemExtractionStep(val extractionTarget: String) : ProgressBarUpdateState()
object ClearingSupportFiles : ProgressBarUpdateState()
object ProgressBarOperationComplete : ProgressBarUpdateState()

class MainActivityViewModelFactory(private val appsStartupFsm: AppsStartupFsm, private val sessionStartupFsm: SessionStartupFsm) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainActivityViewModel(appsStartupFsm, sessionStartupFsm) as T
    }
}