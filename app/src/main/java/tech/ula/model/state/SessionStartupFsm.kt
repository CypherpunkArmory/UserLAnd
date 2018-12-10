package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import java.io.File

class SessionStartupFsm {

    private val state = MutableLiveData<SessionStartupState>().apply { postValue(WaitingForSessionSelection) }



    fun getState(): LiveData<SessionStartupState> {
        return state
    }

    // Exposed for testing purposes. This should not be called during real use cases.
    internal fun setState(newState: SessionStartupState) {
        state.postValue(newState)
    }

    fun submitEvent(event: SessionStartupEvent) {
        if (!transitionIsAcceptable(event)) {
            state.postValue(IncorrectTransition(event, state.value!!))
            return
        }
        when (event) {
            is SessionSelected -> {}
            is DownloadAssets -> {}
            is AssetDownloadComplete -> {}
        }
    }

    fun transitionIsAcceptable(event: SessionStartupEvent): Boolean {
        val currentState = state.value!!
        return when (event) {
            is SessionSelected -> currentState is WaitingForSessionSelection
            is RetrieveAssetLists -> currentState is SessionIsReadyForPreparation
            is GenerateDownloads -> currentState is AssetListsRetrievalSucceeded
            is DownloadAssets -> currentState is DownloadsRequired
            is AssetDownloadComplete -> currentState is DownloadingRequirements
            is CopyDownloadsToLocalStorage -> currentState is DownloadsHaveSucceeded
            is ExtractFilesystem -> currentState is NoDownloadsRequired || currentState is CopyingSucceeded
            is VerifyFilesystemAssets -> currentState is ExtractionSucceeded
        }
    }
}

sealed class SessionStartupState
data class IncorrectTransition(val event: SessionStartupEvent, val state: SessionStartupState) : SessionStartupState()
object WaitingForSessionSelection : SessionStartupState()
object SingleSessionSupported : SessionStartupState()
data class SessionIsRestartable(val session: Session) : SessionStartupState()
data class SessionIsReadyForPreparation(val session: Session) : SessionStartupState()
object RetrievingAssetLists : SessionStartupState()
data class AssetListsRetrievalSucceeded(val assetLists: List<List<Asset>>) : SessionStartupState()
object AssetListsRetrievalFailed : SessionStartupState()
object GeneratingDownloadRequirements : SessionStartupState()
data class DownloadsRequired(val largeDownloadIsRequired: Boolean) : SessionStartupState()
object NoDownloadsRequired : SessionStartupState()
object DownloadingRequirements : SessionStartupState()
object DownloadsHaveSucceeded : SessionStartupState()
data class DownloadsHaveFailed(val failedDownloads: List<Asset>) : SessionStartupState()
object CopyingFilesToRequiredDirectories : SessionStartupState()
object CopyingSucceeded : SessionStartupState()
object CopyingFailed : SessionStartupState()
object ExtractingFilesystem : SessionStartupState()
object ExtractionSucceeded : SessionStartupState()
object ExtractionFailed : SessionStartupState()
object VerifyingFilesystemAssets : SessionStartupState()
object FilesystemHasRequiredAssets : SessionStartupState()
object FilesystemIsMissingRequiredAssets : SessionStartupState()

sealed class SessionStartupEvent
data class SessionSelected(val session: Session) : SessionStartupEvent()
data class RetrieveAssetLists(val filesystem: Filesystem) : SessionStartupEvent()
data class GenerateDownloads(val filesystem: Filesystem, val assetLists: List<List<Asset>>) : SessionStartupEvent()
data class DownloadAssets(val assetLists: List<List<Asset>>) : SessionStartupEvent()
data class AssetDownloadComplete(val downloadAssetId: Long) : SessionStartupEvent()
data class CopyDownloadsToLocalStorage(val filesDir: File, val assetLists: List<List<Asset>>) : SessionStartupEvent()
data class ExtractFilesystem(val filesystem: Filesystem) : SessionStartupEvent()
data class VerifyFilesystemAssets(val filesystem: Filesystem) : SessionStartupEvent()
