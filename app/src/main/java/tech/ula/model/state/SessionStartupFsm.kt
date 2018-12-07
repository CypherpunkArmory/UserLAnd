package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import java.io.File

class SessionStartupFsm {

    private val state = MutableLiveData<SessionStartupState>().apply { WaitingForSessionSelection }

    fun getState(): LiveData<SessionStartupState> {
        return state
    }

    // Exposed for testing purposes. This should not be called during real use cases.
    internal fun setState(newState: SessionStartupState) {
        state.postValue(newState)
    }

    fun submitEvent(event: SessionStartupEvent) {
        return when (event) {
            is SessionSelected -> {}
            is DownloadAssets -> {}
            is AssetDownloadComplete -> {}
        }
    }
}

sealed class SessionStartupState
object WaitingForSessionSelection : SessionStartupState()
object SingleSessionSupported : SessionStartupState()
data class SessionIsRestartable(val session: Session) : SessionStartupState()
data class SessionIsReadyForPreparation(val session: Session) : SessionStartupState()
object RetrievingAssetLists : SessionStartupState()
object AssetListsRetrievalSucceeded : SessionStartupState()
object AssetListsRetrievalFailed : SessionStartupState()
object GeneratingDownloadRequirements : SessionStartupState()
object LargeDownloadRequired : SessionStartupState()
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
