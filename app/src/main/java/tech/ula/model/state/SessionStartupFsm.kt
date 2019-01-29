package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.CrashlyticsWrapper
import tech.ula.utils.DownloadUtility
import tech.ula.utils.FilesystemUtility
import tech.ula.utils.TimeUtility

class SessionStartupFsm(
    ulaDatabase: UlaDatabase,
    private val assetRepository: AssetRepository,
    private val filesystemUtility: FilesystemUtility,
    private val downloadUtility: DownloadUtility,
    private val timeUtility: TimeUtility = TimeUtility(),
    private val crashlyticsWrapper: CrashlyticsWrapper = CrashlyticsWrapper()
) {

    private val state = MutableLiveData<SessionStartupState>().apply { postValue(WaitingForSessionSelection) }

    private val sessionDao = ulaDatabase.sessionDao()
    private val activeSessionsLiveData = sessionDao.findActiveSessions()
    private val activeSessions = mutableListOf<Session>()

    private val filesystemDao = ulaDatabase.filesystemDao()
    private val filesystemsLiveData = filesystemDao.getAllFilesystems()
    private val filesystems = mutableListOf<Filesystem>()

    private val downloadingIds = mutableListOf<Long>()
    private val downloadedIds = mutableListOf<Long>()

    private val extractionLogger: (String) -> Unit = { line ->
        state.postValue(ExtractingFilesystem(line))
    }

    init {
        activeSessionsLiveData.observeForever {
            it?.let { list ->
                activeSessions.clear()
                activeSessions.addAll(list)
            }
        }
        filesystemsLiveData.observeForever {
            it?.let { list ->
                filesystems.clear()
                filesystems.addAll(list)
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

    fun sessionsAreActive(): Boolean {
        return activeSessions.size > 0
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
            is VerifyFilesystemAssets -> currentState is NoDownloadsRequired || currentState is LocalDirectoryCopySucceeded
            is ExtractFilesystem -> currentState is FilesystemAssetVerificationSucceeded
            is ResetSessionState -> true
        }
    }

    fun submitEvent(event: SessionStartupEvent, coroutineScope: CoroutineScope) = coroutineScope.launch {
        crashlyticsWrapper.setString("Last submitted session fsm event", "$event")
        crashlyticsWrapper.setString("State during session fsm event submission", "${state.value}")
        if (!transitionIsAcceptable(event)) {
            state.postValue(IncorrectSessionTransition(event, state.value!!))
            return@launch
        }
        when (event) {
            is SessionSelected -> { handleSessionSelected(event.session) }
            is RetrieveAssetLists -> { handleRetrieveAssetLists(event.filesystem) }
            is GenerateDownloads -> { handleGenerateDownloads(event.filesystem, event.assetLists) }
            is DownloadAssets -> { handleDownloadAssets(event.assetsToDownload) }
            is AssetDownloadComplete -> { handleAssetsDownloadComplete(event.downloadAssetId) }
            is CopyDownloadsToLocalStorage -> { handleCopyDownloadsToLocalDirectories(event.filesystem) }
            is VerifyFilesystemAssets -> { handleVerifyFilesystemAssets(event.filesystem) }
            is ExtractFilesystem -> { handleExtractFilesystem(event.filesystem) }
            is ResetSessionState -> { state.postValue(WaitingForSessionSelection) }
        }
    }

    private fun findFilesystemForSession(session: Session): Filesystem {
        return filesystems.find { filesystem -> filesystem.id == session.filesystemId }!!
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

        val filesystem = findFilesystemForSession(session)
        state.postValue(SessionIsReadyForPreparation(session, filesystem))
    }

    private suspend fun handleRetrieveAssetLists(filesystem: Filesystem) {
        state.postValue(RetrievingAssetLists)

        val assetLists = withContext(Dispatchers.IO) {
            assetRepository.getAllAssetLists(filesystem.distributionType, filesystem.archType)
        }

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

                if (asset.isLarge && needsUpdate && filesystemUtility
                                .hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")) {
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
        downloadingIds.clear()
        downloadedIds.clear()

        // If the state isn't updated first, AssetDownloadComplete events will be submitted before
        // the transition is acceptable.
        state.postValue(DownloadingRequirements(0, assetsToDownload.size))
        val newDownloads = downloadUtility.downloadRequirements(assetsToDownload)
        downloadingIds.addAll(newDownloads)
    }

    private fun handleAssetsDownloadComplete(downloadId: Long) {
        if(!downloadUtility.downloadIsForUserland(downloadId)) return

        if (!downloadUtility.downloadedSuccessfully(downloadId)) {
            val reason = downloadUtility.getReasonForDownloadFailure(downloadId)
            state.postValue(DownloadsHaveFailed(reason))
            return
        }

        downloadedIds.add(downloadId)
        downloadUtility.setTimestampForDownloadedFile(downloadId)
        if (downloadingIds.size != downloadedIds.size) {
            state.postValue(DownloadingRequirements(downloadedIds.size, downloadingIds.size))
            return
        }

        downloadedIds.sort()
        downloadingIds.sort()
        if (downloadedIds != downloadingIds) {
            state.postValue(DownloadsHaveFailed("Downloads completed with non-enqueued downloads"))
            return
        }

        state.postValue(DownloadsHaveSucceeded)
    }

    private suspend fun handleCopyDownloadsToLocalDirectories(filesystem: Filesystem) = withContext(Dispatchers.IO) {
        state.postValue(CopyingFilesToLocalDirectories)
        try {
            downloadUtility.moveAssetsToCorrectLocalDirectory()
            assetRepository.setLastDistributionUpdate(filesystem.distributionType)
        } catch (err: Exception) {
            state.postValue(LocalDirectoryCopyFailed)
            return@withContext
        }
        state.postValue(LocalDirectoryCopySucceeded)
    }

    private suspend fun handleVerifyFilesystemAssets(filesystem: Filesystem) = withContext(Dispatchers.IO) {
        state.postValue(VerifyingFilesystemAssets)

        val filesystemDirectoryName = "${filesystem.id}"
        val requiredAssets = assetRepository.getDistributionAssetsForExistingFilesystem(filesystem)
        val allAssetsArePresentOnFilesystem = filesystemUtility.areAllRequiredAssetsPresent(filesystemDirectoryName, requiredAssets)
        val filesystemAssetsNeedUpdating = filesystem.lastUpdated < assetRepository.getLastDistributionUpdate(filesystem.distributionType)

        if (!allAssetsArePresentOnFilesystem || filesystemAssetsNeedUpdating) {
            if (!assetRepository.assetsArePresentInSupportDirectories(requiredAssets)) {
                state.postValue(AssetsAreMissingFromSupportDirectories)
                return@withContext
            }

            try {
                filesystemUtility.copyAssetsToFilesystem("${filesystem.id}", filesystem.distributionType)
                filesystem.lastUpdated = timeUtility.getCurrentTimeMillis()
                filesystemDao.updateFilesystem(filesystem)
            } catch (err: Exception) {
                state.postValue(FilesystemAssetCopyFailed)
            }

            if (filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
                filesystemUtility.removeRootfsFilesFromFilesystem(filesystemDirectoryName)
            }
        }

        state.postValue(FilesystemAssetVerificationSucceeded)
    }

    private suspend fun handleExtractFilesystem(filesystem: Filesystem) = withContext(Dispatchers.IO) {
        val filesystemDirectoryName = "${filesystem.id}"

        if (filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            state.postValue(ExtractionHasCompletedSuccessfully)
            return@withContext
        }

        // TODO test
        filesystemUtility.extractFilesystem(filesystem, extractionLogger)

        if (filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            state.postValue(ExtractionHasCompletedSuccessfully)
            return@withContext
        }

        state.postValue(ExtractionFailed)
    }
}

sealed class SessionStartupState
data class IncorrectSessionTransition(val event: SessionStartupEvent, val state: SessionStartupState) : SessionStartupState()
object WaitingForSessionSelection : SessionStartupState()
object SingleSessionSupported : SessionStartupState()
data class SessionIsRestartable(val session: Session) : SessionStartupState()
data class SessionIsReadyForPreparation(val session: Session, val filesystem: Filesystem) : SessionStartupState()
object RetrievingAssetLists : SessionStartupState()
data class AssetListsRetrievalSucceeded(val assetLists: List<List<Asset>>) : SessionStartupState()
object AssetListsRetrievalFailed : SessionStartupState()
object GeneratingDownloadRequirements : SessionStartupState()
data class DownloadsRequired(val requiredDownloads: List<Asset>, val largeDownloadRequired: Boolean) : SessionStartupState()
object NoDownloadsRequired : SessionStartupState()
data class DownloadingRequirements(val numCompleted: Int, val numTotal: Int) : SessionStartupState()
object DownloadsHaveSucceeded : SessionStartupState()
data class DownloadsHaveFailed(val reason: String) : SessionStartupState()
object CopyingFilesToLocalDirectories : SessionStartupState()
object LocalDirectoryCopySucceeded : SessionStartupState()
object LocalDirectoryCopyFailed : SessionStartupState()
object VerifyingFilesystemAssets : SessionStartupState()
object FilesystemAssetVerificationSucceeded : SessionStartupState()
object AssetsAreMissingFromSupportDirectories : SessionStartupState()
object FilesystemAssetCopyFailed : SessionStartupState()
data class ExtractingFilesystem(val extractionTarget: String) : SessionStartupState()
object ExtractionHasCompletedSuccessfully : SessionStartupState()
object ExtractionFailed : SessionStartupState()

sealed class SessionStartupEvent
data class SessionSelected(val session: Session) : SessionStartupEvent()
data class RetrieveAssetLists(val filesystem: Filesystem) : SessionStartupEvent()
data class GenerateDownloads(val filesystem: Filesystem, val assetLists: List<List<Asset>>) : SessionStartupEvent()
data class DownloadAssets(val assetsToDownload: List<Asset>) : SessionStartupEvent()
data class AssetDownloadComplete(val downloadAssetId: Long) : SessionStartupEvent()
data class CopyDownloadsToLocalStorage(val filesystem: Filesystem) : SessionStartupEvent()
data class VerifyFilesystemAssets(val filesystem: Filesystem) : SessionStartupEvent()
data class ExtractFilesystem(val filesystem: Filesystem) : SessionStartupEvent()
object ResetSessionState : SessionStartupEvent()
