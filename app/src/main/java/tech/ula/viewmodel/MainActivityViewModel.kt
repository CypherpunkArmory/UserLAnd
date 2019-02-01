package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.* // ktlint-disable no-wildcard-imports
import tech.ula.utils.AppServiceTypePreference
import tech.ula.utils.AssetFileClearer
import tech.ula.utils.CrashlyticsWrapper
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

class MainActivityViewModel(
    private val appsStartupFsm: AppsStartupFsm,
    private val sessionStartupFsm: SessionStartupFsm,
    private val crashlyticsWrapper: CrashlyticsWrapper = CrashlyticsWrapper()
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

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

    init {
        state.addSource(appsState) { it?.let { update ->
            crashlyticsWrapper.setString("Last observed app state from viewmodel", "$update")
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
            crashlyticsWrapper.setString("Last observed session state from viewmodel", "$update")
            // Update stateful variables before handling the update so they can be used during it
            if (update !is WaitingForSessionSelection) {
                sessionsAreWaitingForSelection = false
            }
            when (update) {
                is WaitingForSessionSelection -> {
                    sessionsAreWaitingForSelection = true
                }
                is SessionIsReadyForPreparation -> {
                    lastSelectedSession = update.session
                    lastSelectedFilesystem = update.filesystem
                }
                is SessionIsRestartable -> {
                    state.postValue(SessionCanBeRestarted(update.session))
                    resetStartupState()
                }
                is SingleSessionSupported -> {
                    state.postValue(CanOnlyStartSingleSession)
                    resetStartupState()
                }
            }
            handleSessionPreparationState(update)
        } }
    }

    fun getState(): LiveData<State> {
        return state
    }

    fun waitForPermissions(appToContinue: App = unselectedApp, sessionToContinue: Session = unselectedSession) {
        resetStartupState()
        lastSelectedApp = appToContinue
        lastSelectedSession = sessionToContinue
    }

    fun permissionsHaveBeenGranted() {
        when {
            lastSelectedApp != unselectedApp && lastSelectedSession != unselectedSession -> {
                state.postValue(TooManySelectionsMadeWhenPermissionsGranted)
            }
            lastSelectedApp == unselectedApp && lastSelectedSession == unselectedSession -> {
                state.postValue(NoSelectionsMadeWhenPermissionsGranted)
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
            state.postValue(NoFilesystemSelectedWhenCredentialsSubmitted)
            return
        }
        submitAppsStartupEvent(SubmitAppsFilesystemCredentials(lastSelectedFilesystem, username, password, vncPassword))
    }

    fun submitAppServicePreference(preference: AppServiceTypePreference) {
        if (lastSelectedApp == unselectedApp) {
            state.postValue(NoAppSelectedWhenPreferenceSubmitted)
            return
        }
        submitAppsStartupEvent(SubmitAppServicePreference(lastSelectedApp, preference))
    }

    fun handleUserInputCancelled() {
        resetStartupState()
    }

    // Exposed so that downloads can be continued from activity
    fun startAssetDownloads(requiredDownloads: List<Asset>) {
        submitSessionStartupEvent(DownloadAssets(requiredDownloads))
    }

    suspend fun handleClearSupportFiles(assetFileClearer: AssetFileClearer) = withContext(Dispatchers.IO) {
        if (sessionStartupFsm.sessionsAreActive()) {
            state.postValue(ActiveSessionsMustBeDeactivated)
            return@withContext
        }
        state.postValue(ClearingSupportFiles)
        try {
            assetFileClearer.clearAllSupportAssets()
            state.postValue(ProgressBarOperationComplete)
        } catch (err: Exception) {
            state.postValue(FailedToClearSupportFiles)
        }
    }

    private fun handleAppsPreparationState(newState: AppsStartupState) {
        // Exit early if we aren't expecting preparation requirements to have been met
        if (newState is WaitingForAppSelection || newState is FetchingDatabaseEntries) {
            return
        }
        if (!appsPreparationRequirementsHaveBeenSelected()) {
            state.postValue(NoAppSelectedWhenPreparationStarted)
            return
        }
        // Return when statement for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectAppTransition -> {
                state.postValue(IllegalStateTransition("$newState"))
            }
            is WaitingForAppSelection -> {}
            is FetchingDatabaseEntries -> {}
            is DatabaseEntriesFetched -> {
                submitAppsStartupEvent(CheckAppsFilesystemCredentials(lastSelectedFilesystem))
            }
            is DatabaseEntriesFetchFailed -> {
                state.postValue(ErrorFetchingAppDatabaseEntries)
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
                state.postValue(ErrorCopyingAppScript)
            }
            is SyncingDatabaseEntries -> {}
            is AppDatabaseEntriesSynced -> {
                submitSessionStartupEvent(SessionSelected(lastSelectedSession))
            }
        }
    }

    private fun handleSessionPreparationState(newState: SessionStartupState) {
        // Exit early if we aren't expecting preparation requirements to have been met
        if (newState is WaitingForSessionSelection || newState is SingleSessionSupported ||
                newState is SessionIsRestartable) {
            return
        }
        if (!sessionPreparationRequirementsHaveBeenSelected()) {
            state.postValue(NoSessionSelectedWhenPreparationStarted)
            return
        }
        // Return when statement for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectSessionTransition -> {
                state.postValue(IllegalStateTransition("$newState"))
            }
            is WaitingForSessionSelection -> {}
            is SingleSessionSupported -> {}
            is SessionIsRestartable -> {}
            is SessionIsReadyForPreparation -> {
                state.postValue(StartingSetup)
                submitSessionStartupEvent(RetrieveAssetLists(lastSelectedFilesystem))
            }
            is RetrievingAssetLists -> {
                state.postValue(FetchingAssetLists)
            }
            is AssetListsRetrievalSucceeded -> {
                submitSessionStartupEvent(GenerateDownloads(lastSelectedFilesystem, newState.assetLists))
            }
            is AssetListsRetrievalFailed -> {
                state.postValue(ErrorFetchingAssetLists)
            }
            is GeneratingDownloadRequirements -> {
                state.postValue(CheckingForAssetsUpdates)
            }
            is DownloadsRequired -> {
                if (newState.largeDownloadRequired) {
                    state.postValue(LargeDownloadRequired(newState.requiredDownloads))
                } else {
                    startAssetDownloads(newState.requiredDownloads)
                }
            }
            is NoDownloadsRequired -> {
                submitSessionStartupEvent(VerifyFilesystemAssets(lastSelectedFilesystem))
            }
            is DownloadingRequirements -> {
                state.postValue(DownloadProgress(newState.numCompleted, newState.numTotal))
            }
            is DownloadsHaveSucceeded -> {
                submitSessionStartupEvent(CopyDownloadsToLocalStorage(lastSelectedFilesystem))
            }
            is DownloadsHaveFailed -> {
                state.postValue(DownloadsDidNotCompleteSuccessfully(newState.reason))
            }
            is CopyingFilesToLocalDirectories -> {
                state.postValue(CopyingDownloads)
            }
            is LocalDirectoryCopySucceeded -> {
                submitSessionStartupEvent(VerifyFilesystemAssets(lastSelectedFilesystem))
            }
            is LocalDirectoryCopyFailed -> {
                state.postValue(FailedToCopyAssetsToLocalStorage)
            }
            is VerifyingFilesystemAssets -> {
                state.postValue(VerifyingFilesystem)
            }
            is FilesystemAssetVerificationSucceeded -> {
                submitSessionStartupEvent(ExtractFilesystem(lastSelectedFilesystem))
            }
            is AssetsAreMissingFromSupportDirectories -> {
                state.postValue(AssetsHaveNotBeenDownloaded)
            }
            is FilesystemAssetCopyFailed -> {
                state.postValue(FailedToCopyAssetsToFilesystem)
            }
            is ExtractingFilesystem -> {
                state.postValue(FilesystemExtraction(newState.extractionTarget))
            }
            is ExtractionHasCompletedSuccessfully -> {
                state.postValue(SessionCanBeStarted(lastSelectedSession))
                resetStartupState()
            }
            is ExtractionFailed -> {
                state.postValue(FailedToExtractFilesystem)
            }
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

    private fun sessionPreparationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedSession != unselectedSession && lastSelectedFilesystem != unselectedFilesystem
    }

    private fun submitAppsStartupEvent(event: AppsStartupEvent) {
        crashlyticsWrapper.setString("Last viewmodel apps event submission", "$event")
        appsStartupFsm.submitEvent(event, this)
    }

    private fun submitSessionStartupEvent(event: SessionStartupEvent) {
        crashlyticsWrapper.setString("Last viewmodel session event submission", "$event")
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
object NoAppSelectedWhenPreparationStarted : IllegalState()
object ErrorFetchingAppDatabaseEntries : IllegalState()
object ErrorCopyingAppScript : IllegalState()
object NoSessionSelectedWhenPreparationStarted : IllegalState()
object ErrorFetchingAssetLists : IllegalState()
data class DownloadsDidNotCompleteSuccessfully(val reason: String) : IllegalState()
object FailedToCopyAssetsToLocalStorage : IllegalState()
object AssetsHaveNotBeenDownloaded : IllegalState()
object FailedToCopyAssetsToFilesystem : IllegalState()
object FailedToExtractFilesystem : IllegalState()
object FailedToClearSupportFiles : IllegalState()

sealed class UserInputRequiredState : State()
object FilesystemCredentialsRequired : UserInputRequiredState()
object AppServiceTypePreferenceRequired : UserInputRequiredState()
data class LargeDownloadRequired(val requiredDownloads: List<Asset>) : UserInputRequiredState()
object ActiveSessionsMustBeDeactivated : UserInputRequiredState()

sealed class ProgressBarUpdateState : State()
object StartingSetup : ProgressBarUpdateState()
object FetchingAssetLists : ProgressBarUpdateState()
object CheckingForAssetsUpdates : ProgressBarUpdateState()
data class DownloadProgress(val numComplete: Int, val numTotal: Int) : ProgressBarUpdateState()
object CopyingDownloads : ProgressBarUpdateState()
data class FilesystemExtraction(val extractionTarget: String) : ProgressBarUpdateState()
object VerifyingFilesystem : ProgressBarUpdateState()
object ClearingSupportFiles : ProgressBarUpdateState()
object ProgressBarOperationComplete : ProgressBarUpdateState()

class MainActivityViewModelFactory(private val appsStartupFsm: AppsStartupFsm, private val sessionStartupFsm: SessionStartupFsm) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainActivityViewModel(appsStartupFsm, sessionStartupFsm) as T
    }
}