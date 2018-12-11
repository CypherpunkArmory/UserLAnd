package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.utils.DownloadUtility
import tech.ula.utils.FilesystemUtility
import java.io.File

class SessionStartupFsm(
        sessionDao: SessionDao,
        private val assetRepository: AssetRepository,
        private val filesystemUtility: FilesystemUtility,
        private val downloadUtility: DownloadUtility) {

    private val state = MutableLiveData<SessionStartupState>().apply { postValue(WaitingForSessionSelection) }

    private val activeSessionsLiveData = sessionDao.findActiveSessions()
    private val activeSessions = mutableListOf<Session>()

    private val downloadingAssets = mutableListOf<Pair<Asset, Long>>()
    private val downloadedIds = mutableListOf<Long>()

    init {
        activeSessionsLiveData.observeForever {
            it?.let {list ->
                activeSessions.clear()
                activeSessions.addAll(list)
            }
        }
    }

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
        return when (event) {
            is SessionSelected -> { handleSessionSelected(event.session) }
            is RetrieveAssetLists -> { handleRetrieveAssetLists(event.filesystem) }
            is GenerateDownloads -> { handleGenerateDownloads(event.filesystem, event.assetLists) }
            is DownloadAssets -> { handleDownloadAssets(event.assetsToDownload) }
            is AssetDownloadComplete -> { handleAssetsDownloadComplete(event.downloadAssetId) }
            is CopyDownloadsToLocalStorage -> {}
            is ExtractFilesystem -> {}
            is VerifyFilesystemAssets -> {}
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

    private fun handleSessionSelected(session: Session) {
        if (activeSessions.isNotEmpty()) {
            if (activeSessions.contains(session)) {
                state.postValue(SessionIsRestartable(session))
                return
            }

            state.postValue(SingleSessionSupported)
            return
        }

        state.postValue(SessionIsReadyForPreparation(session))
    }

    private fun handleRetrieveAssetLists(filesystem: Filesystem) {
        state.postValue(RetrievingAssetLists)

        val assetLists = assetRepository.getAllAssetLists(filesystem.distributionType, filesystem.archType)

        if (assetLists.any { it.isEmpty() }) {
            state.postValue(AssetListsRetrievalFailed)
            return
        }

        state.postValue(AssetListsRetrievalSucceeded(assetLists))
    }

    private fun handleGenerateDownloads(filesystem: Filesystem, assetLists: List<List<Asset>>) {
        state.postValue(GeneratingDownloadRequirements)

        val requiredDownloads = assetLists.map { assetList ->
            assetList.filter { asset ->  
                val needsUpdate = assetRepository.doesAssetNeedToUpdated(asset)

                if (asset.isLarge && needsUpdate && filesystemUtility.
                                hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")) {
                    return@filter false
                }
                needsUpdate
            }
        }.flatten()

        if (requiredDownloads.isEmpty()) {
            state.postValue(NoDownloadsRequired)
            return
        }

        val largeDownloadRequired = requiredDownloads.any { it.isLarge }
        state.postValue(DownloadsRequired(requiredDownloads, largeDownloadRequired))
    }

    private fun handleDownloadAssets(assetsToDownload: List<Asset>) {
        state.postValue(DownloadingRequirements)

        downloadingAssets.clear()
        downloadedIds.clear()

        val newDownloads = downloadUtility.downloadRequirements(assetsToDownload)
        downloadingAssets.addAll(newDownloads)
    }

    private fun handleAssetsDownloadComplete(downloadId: Long) {
        if (!downloadUtility.downloadedSuccessfully(downloadId)) state.postValue(DownloadsHaveFailed)

        downloadedIds.add(downloadId)
        if (downloadingAssets.size != downloadedIds.size) return

        state.postValue(DownloadsHaveSucceeded)
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
data class DownloadsRequired(val requiredDownloads: List<Asset>, val largeDownloadRequired: Boolean) : SessionStartupState()
object NoDownloadsRequired : SessionStartupState()
object DownloadingRequirements : SessionStartupState()
object DownloadsHaveSucceeded : SessionStartupState()
object DownloadsHaveFailed : SessionStartupState()
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
data class DownloadAssets(val assetsToDownload: List<Asset>) : SessionStartupEvent()
data class AssetDownloadComplete(val downloadAssetId: Long) : SessionStartupEvent()
data class CopyDownloadsToLocalStorage(val filesDir: File, val assetLists: List<List<Asset>>) : SessionStartupEvent()
data class ExtractFilesystem(val filesystem: Filesystem) : SessionStartupEvent()
data class VerifyFilesystemAssets(val filesystem: Filesystem) : SessionStartupEvent()
