package tech.ula.model.state

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.DownloadUtility
import tech.ula.utils.FilesystemUtility
import tech.ula.utils.TimeUtility

class SessionStartupFsm(
    ulaDatabase: UlaDatabase,
    private val assetRepository: AssetRepository,
    private val filesystemUtility: FilesystemUtility,
    private val downloadUtility: DownloadUtility,
    private val timeUtility: TimeUtility = TimeUtility()
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
            is ExtractFilesystem -> currentState is NoDownloadsRequired || currentState is CopyingSucceeded
            is VerifyFilesystemAssets -> currentState is ExtractionSucceeded
            is ResetSessionState -> true
        }
    }

    suspend fun submitEvent(event: SessionStartupEvent) {
        if (!transitionIsAcceptable(event)) {
            state.postValue(IncorrectSessionTransition(event, state.value!!))
            return
        }
        when (event) {
            is SessionSelected -> { handleSessionSelected(event.session) }
            is RetrieveAssetLists -> { handleRetrieveAssetLists(event.filesystem) }
            is GenerateDownloads -> { handleGenerateDownloads(event.filesystem, event.assetLists) }
            is DownloadAssets -> { handleDownloadAssets(event.assetsToDownload) }
            is AssetDownloadComplete -> { handleAssetsDownloadComplete(event.downloadAssetId) }
            is CopyDownloadsToLocalStorage -> { handleCopyDownloads(event.filesystem) }
            is ExtractFilesystem -> { handleExtractFilesystem(event.filesystem) }
            is VerifyFilesystemAssets -> { handleVerifyFilesystemAssets(event.filesystem) }
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
        if (!downloadUtility.downloadedSuccessfully(downloadId)) {
            val reason = downloadUtility.getReasonForDownloadFailure(downloadId)
            state.postValue(DownloadsHaveFailed(reason))
            return
        }

        downloadedIds.add(downloadId)
        downloadUtility.setTimestampForDownloadedFile(downloadId)
        downloadedIds.sort()
        downloadingIds.sort()
        if (downloadingIds != downloadedIds) {
            state.postValue(DownloadingRequirements(downloadedIds.size, downloadingIds.size))
            return
        }

        state.postValue(DownloadsHaveSucceeded)
    }

    private suspend fun handleCopyDownloads(filesystem: Filesystem) = withContext(Dispatchers.IO) {
        state.postValue(CopyingFilesToRequiredDirectories)
        try {
            downloadUtility.moveAssetsToCorrectLocalDirectory()
            val copyingSucceeded = copyDistributionAssetsToFilesystem(filesystem)
            if (!copyingSucceeded) {
                state.postValue(DistributionCopyFailed)
                return@withContext
            }
            assetRepository.setLastDistributionUpdate(filesystem.distributionType)
        } catch (err: Exception) {
            state.postValue(CopyingFailed)
            return@withContext
        }
        state.postValue(CopyingSucceeded)
    }

    private suspend fun handleExtractFilesystem(filesystem: Filesystem) = withContext(Dispatchers.IO) {
        val filesystemDirectoryName = "${filesystem.id}"

        if (filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            state.postValue(ExtractionSucceeded)
            return@withContext
        }

        // TODO test
        filesystemUtility.extractFilesystem(filesystem, extractionLogger)

        if (filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            state.postValue(ExtractionSucceeded)
            return@withContext
        }

        state.postValue(ExtractionFailed)
    }

    private suspend fun copyDistributionAssetsToFilesystem(filesystem: Filesystem): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            filesystemUtility.copyAssetsToFilesystem("${filesystem.id}", filesystem.distributionType)
            filesystem.lastUpdated = timeUtility.getCurrentTimeMillis()
            filesystemDao.updateFilesystem(filesystem)
            true
        } catch (err: Exception) {
            false
        }
    }

    private suspend fun handleVerifyFilesystemAssets(filesystem: Filesystem) {
        state.postValue(VerifyingFilesystemAssets)

        val filesystemDirectoryName = "${filesystem.id}"
        val requiredAssets = assetRepository.getDistributionAssetsForExistingFilesystem(filesystem)
        val allAssetsArePresent = filesystemUtility.areAllRequiredAssetsPresent(filesystemDirectoryName, requiredAssets)
        val allAssetsAreUpToDate = filesystem.lastUpdated >= assetRepository.getLastDistributionUpdate(filesystem.distributionType)

        when {
            allAssetsArePresent && allAssetsAreUpToDate -> {
                state.postValue(FilesystemHasRequiredAssets)
            }

            allAssetsArePresent -> {
                val copyingSucceeded = copyDistributionAssetsToFilesystem(filesystem)
                if (copyingSucceeded) {
                    state.postValue(FilesystemHasRequiredAssets)
                    filesystemUtility.removeRootfsFilesFromFilesystem(filesystemDirectoryName)
                } else state.postValue(DistributionCopyFailed)
            }

            else -> {
                state.postValue(FilesystemIsMissingRequiredAssets)
            }
        }
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
object CopyingFilesToRequiredDirectories : SessionStartupState()
object CopyingSucceeded : SessionStartupState()
object CopyingFailed : SessionStartupState()
object DistributionCopyFailed : SessionStartupState()
data class ExtractingFilesystem(val extractionTarget: String) : SessionStartupState()
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
data class CopyDownloadsToLocalStorage(val filesystem: Filesystem) : SessionStartupEvent()
data class ExtractFilesystem(val filesystem: Filesystem) : SessionStartupEvent()
data class VerifyFilesystemAssets(val filesystem: Filesystem) : SessionStartupEvent()
object ResetSessionState : SessionStartupEvent()
