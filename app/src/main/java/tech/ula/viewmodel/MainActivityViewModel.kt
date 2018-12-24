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
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.*

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

    fun submitAppSelection(app: App) {
        if (!selectionsCanBeMade()) return
    }

    fun submitSessionSelection(session: Session) {
        if (!selectionsCanBeMade()) return
    }

    fun submitCompletedDownloadId(id: Long) {

    }

    private fun handleAppsPreparationState(newState: AppsStartupState) {
        if (!appsPreparationRequirementsHaveBeenSelected()) {
            // TODO error
        }
        // Return when statement for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectAppTransition -> {}
            is WaitingForAppSelection -> {}
            is FetchingDatabaseEntries -> {}
            is DatabaseEntriesFetched -> {}
            is DatabaseEntriesFetchFailed -> {}
            is AppsFilesystemHasCredentials -> {}
            is AppsFilesystemRequiresCredentials -> {}
            is AppHasServiceTypePreferenceSet -> {}
            is AppRequiresServiceTypePreference -> {}
            is CopyingAppScript -> {}
            is AppScriptCopySucceeded -> {}
            is AppScriptCopyFailed -> {}
            is SyncingDatabaseEntries -> {}
            is AppDatabaseEntriesSynced -> {}
        }
    }

    private fun handleSessionPreparationState(newState: SessionStartupState) {
        if (!sessionPreparationRequirementsHaveBeenSelected()) {
            // TODO error
        }
        // Return when statement for compile-time exhaustiveness check
        return when (newState) {
            is IncorrectSessionTransition -> {}
            is WaitingForSessionSelection -> {}
            is SingleSessionSupported -> {}
            is SessionIsRestartable -> {}
            is SessionIsReadyForPreparation -> {}
            is RetrievingAssetLists -> {}
            is AssetListsRetrievalSucceeded -> {}
            is AssetListsRetrievalFailed -> {}
            is GeneratingDownloadRequirements -> {}
            is DownloadsRequired -> {}
            is NoDownloadsRequired -> {}
            is DownloadingRequirements -> {}
            is DownloadsHaveSucceeded -> {}
            is DownloadsHaveFailed -> {}
            is CopyingFilesToRequiredDirectories -> {}
            is CopyingSucceeded -> {}
            is CopyingFailed -> {}
            is DistributionCopyFailed -> {}
            is ExtractingFilesystem -> {}
            is ExtractionSucceeded -> {}
            is ExtractionFailed -> {}
            is VerifyingFilesystemAssets -> {}
            is FilesystemHasRequiredAssets -> {}
            is FilesystemIsMissingRequiredAssets -> {}
        }
    }

    // Handle this internally in viewmodel
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
sealed class UserInputRequiredState : State() {
    object FilesystemCredentialsRequired : UserInputRequiredState()
    object AppServiceTypePreferenceRequired : UserInputRequiredState()
}
sealed class ProgressBarUpdate(val stepResId: Int, val detailsResId: Int) : State() {
    data class DownloadProgressUpdate(val numComplete: Int, val numTotal: Int) :
            ProgressBarUpdate(R.string.progress_downloading, R.string.progress_downloading_out_of)
    data class FilesystemExtractionProgressUpdate(val extractionTarget: String) :
            ProgressBarUpdate(R.string.progress_setting_up_extract_text, R.string.progress_extraction_details)
}
data class SessionIsStartable(val session: Session) : State()
data class SessionIsRestartable(val session: Session) : State()
data class IllegalState(val reason: String): State()

class MainActivityViewModelFactory(private val appsStartupFsm: AppsStartupFsm, private val sessionStartupFsm: SessionStartupFsm) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainActivityViewModel(appsStartupFsm, sessionStartupFsm) as T
    }
}