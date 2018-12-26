package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.*
import tech.ula.utils.AppServiceTypePreference

class MainActivityViewModel(private val appsStartupFsm: AppsStartupFsm, private val sessionStartupFsm: SessionStartupFsm) : ViewModel() {

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

    init {
        state.addSource(appsState) { it?.let { update ->
            Log.i("VIEWVIEWMODELMODEL", "$update")
            // Update stateful variables before handling the update so they can be used during it
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
            Log.i("VIEWVIEWMODELMODEL", "$update")
            // Update stateful variables before handling the update so they can be used during it
            when (update) {
                is WaitingForSessionSelection -> {
                    sessionsAreWaitingForSelection = true
                }
                is SessionIsReadyForPreparation -> {
                    lastSelectedSession = update.session
                    lastSelectedFilesystem = update.filesystem
                }
            }
            handleSessionPreparationState(update)
        } }
    }

    fun getState(): LiveData<State> {
        return state
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
            // TODO error
            return
        }
        submitAppsStartupEvent(SubmitAppsFilesystemCredentials(lastSelectedFilesystem, username, password, vncPassword))
    }

    fun submitAppServicePreference(preference: AppServiceTypePreference) {
        if (lastSelectedApp == unselectedApp) {
            // TODO error
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

    private fun handleAppsPreparationState(newState: AppsStartupState) {
        if (!appsPreparationRequirementsHaveBeenSelected()) {
            // TODO error
            return
        }
        // Return when statement for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectAppTransition -> {}
            is WaitingForAppSelection -> {}
            is FetchingDatabaseEntries -> {}
            is DatabaseEntriesFetched -> {
                submitAppsStartupEvent(CheckAppsFilesystemCredentials(lastSelectedFilesystem))
            }
            is DatabaseEntriesFetchFailed -> {}
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
            is AppScriptCopyFailed -> {}
            is SyncingDatabaseEntries -> {}
            is AppDatabaseEntriesSynced -> {
                submitSessionStartupEvent(SessionSelected(lastSelectedSession))
            }
        }
    }

    private fun handleSessionPreparationState(newState: SessionStartupState) {
        if (!sessionPreparationRequirementsHaveBeenSelected()) {
            // TODO error
            return
        }
        // Return when statement for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectSessionTransition -> {}
            is WaitingForSessionSelection -> {}
            is SingleSessionSupported -> {
                state.postValue(CanOnlyStartSingleSession)
                resetStartupState()
            }
            is SessionIsRestartable -> {
                state.postValue(SessionCanBeRestarted(newState.session))
                resetStartupState()
            }
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
            is AssetListsRetrievalFailed -> {}
            is GeneratingDownloadRequirements -> {
                state.postValue(CheckingForAssetsUpdates)
            }
            is DownloadsRequired -> {
                if (newState.largeDownloadRequired) {
                    state.postValue(LargeDownloadRequired(newState.requiredDownloads))
                }
                else {
                    startAssetDownloads(newState.requiredDownloads)
                }

            }
            is NoDownloadsRequired -> {
                submitSessionStartupEvent(ExtractFilesystem(lastSelectedFilesystem))
            }
            is DownloadingRequirements -> {
                state.postValue(DownloadProgress(newState.numCompleted, newState.numTotal))
            }
            is DownloadsHaveSucceeded -> {
                submitSessionStartupEvent(CopyDownloadsToLocalStorage)
            }
            is DownloadsHaveFailed -> {}
            is CopyingFilesToRequiredDirectories -> {
                state.postValue(CopyingDownloads)
            }
            is CopyingSucceeded -> {
                submitSessionStartupEvent(ExtractFilesystem(lastSelectedFilesystem))
            }
            is CopyingFailed -> {}
            is DistributionCopyFailed -> {}
            is ExtractingFilesystem -> {
                state.postValue(FilesystemExtraction(newState.extractionTarget))
            }
            is ExtractionSucceeded -> {
                submitSessionStartupEvent(VerifyFilesystemAssets(lastSelectedFilesystem))
            }
            is ExtractionFailed -> {}
            is VerifyingFilesystemAssets -> {
                state.postValue(VerifyingFilesystem)
            }
            is FilesystemHasRequiredAssets -> {
                state.postValue(SessionCanBeStarted(lastSelectedSession))
                resetStartupState()
            }
            is FilesystemIsMissingRequiredAssets -> {}
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

    // TODO this should probably check that session and filesystem selections are for an app
    private fun appsPreparationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedApp != unselectedApp && sessionPreparationRequirementsHaveBeenSelected()
    }

    private fun sessionPreparationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedSession != unselectedSession && lastSelectedFilesystem != unselectedFilesystem
    }

    private fun submitAppsStartupEvent(event: AppsStartupEvent) {
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch { appsStartupFsm.submitEvent(event) }
    }

    private fun submitSessionStartupEvent(event: SessionStartupEvent) {
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch { sessionStartupFsm.submitEvent(event) }
    }
}

sealed class State
object CanOnlyStartSingleSession : State()
data class SessionCanBeStarted(val session: Session) : State()
data class SessionCanBeRestarted(val session: Session) : State()
data class IllegalState(val reason: String): State()

sealed class UserInputRequiredState : State()
object FilesystemCredentialsRequired : UserInputRequiredState()
object AppServiceTypePreferenceRequired : UserInputRequiredState()
data class LargeDownloadRequired(val requiredDownloads: List<Asset>) : UserInputRequiredState()

sealed class ProgressBarUpdateState : State()
object StartingSetup : ProgressBarUpdateState()
object FetchingAssetLists : ProgressBarUpdateState()
object CheckingForAssetsUpdates : ProgressBarUpdateState()
data class DownloadProgress(val numComplete: Int, val numTotal: Int) : ProgressBarUpdateState()
object CopyingDownloads : ProgressBarUpdateState()
data class FilesystemExtraction(val extractionTarget: String) : ProgressBarUpdateState()
object VerifyingFilesystem : ProgressBarUpdateState()

class MainActivityViewModelFactory(private val appsStartupFsm: AppsStartupFsm, private val sessionStartupFsm: SessionStartupFsm) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainActivityViewModel(appsStartupFsm, sessionStartupFsm) as T
    }
}