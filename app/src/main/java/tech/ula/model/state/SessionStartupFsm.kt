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
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class SessionStartupFsm(
    ulaDatabase: UlaDatabase,
    private val assetRepository: AssetRepository,
    private val filesystemUtility: FilesystemUtility,
    private val downloadUtility: DownloadUtility,
    private val timeUtility: TimeUtility = TimeUtility(),
    private val acraWrapper: AcraWrapper = AcraWrapper()
) {

    private val state = MutableLiveData<SessionStartupState>().apply { postValue(WaitingForSessionSelection) }

    private val sessionDao = ulaDatabase.sessionDao()
    private val activeSessionsLiveData = sessionDao.findActiveSessions()
    private val activeSessions = mutableListOf<Session>()

    private val filesystemDao = ulaDatabase.filesystemDao()
    private val filesystemsLiveData = filesystemDao.getAllFilesystems()
    private val filesystems = mutableListOf<Filesystem>()

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
            is AssetDownloadComplete -> {
                // If we are currently downloading assets, we can handle completed downloads that
                // don't belong to us. Otherwise, we still don't want to post an illegal transition.
                currentState is DownloadingAssets || !downloadUtility.downloadIsForUserland(event.downloadAssetId)
            }
            is SyncDownloadState -> {
//                currentState is WaitingForSessionSelection || currentState is (DownloadingAssets)
                true
            }
            is CopyDownloadsToLocalStorage -> currentState is DownloadsHaveSucceeded
            is VerifyFilesystemAssets -> currentState is NoDownloadsRequired || currentState is LocalDirectoryCopySucceeded
            is ExtractFilesystem -> currentState is FilesystemAssetVerificationSucceeded
            is ResetSessionState -> true
        }
    }

    fun submitEvent(event: SessionStartupEvent, coroutineScope: CoroutineScope) = coroutineScope.launch {
        acraWrapper.putCustomString("Last submitted session fsm event", "$event")
        acraWrapper.putCustomString("State during session fsm event submission", "${state.value}")
        if (!transitionIsAcceptable(event)) {
            state.postValue(IncorrectSessionTransition(event, state.value!!))
            return@launch
        }
        when (event) {
            is SessionSelected -> { handleSessionSelected(event.session) }
            is RetrieveAssetLists -> { handleRetrieveAssetLists(event.filesystem) }
            is GenerateDownloads -> { handleGenerateDownloads(event.filesystem, event.assetLists) }
            is DownloadAssets -> { handleDownloadAssets(event.downloadRequirements) }
            is AssetDownloadComplete -> { handleAssetsDownloadComplete(event.downloadAssetId) }
            is SyncDownloadState -> { handleSyncDownloadState() }
            is CopyDownloadsToLocalStorage -> { handleCopyDownloadsToLocalDirectories() }
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
            assetRepository.getAllAssetLists(filesystem.distributionType)
        }

        if (assetLists.values.any { it.isEmpty() }) {
            state.postValue(AssetListsRetrievalFailed)
            return
        }

        state.postValue(AssetListsRetrievalSucceeded(assetLists))
    }

    private suspend fun handleGenerateDownloads(filesystem: Filesystem, assetLists: HashMap<String, List<Asset>>) {
        state.postValue(GeneratingDownloadRequirements)

        // versions are out of sync
        // assets are missing
        // caveat: rootfs shouldn't be downloaded if extracted

        // Asset lists should always only include distribution and "support"
        if (assetLists.size > 2) {
            state.postValue(UnexpectedDownloadGenerationSize(assetLists.size, assetLists.keys))
            return
        }

        if (!assetLists.containsKey(filesystem.distributionType)) {
            state.postValue(UnexpectedDownloadGenerationTypes(filesystem.distributionType, assetLists.keys))
            return
        }

        val downloadRequirements = assetRepository.generateDownloadRequirements(filesystem, assetLists)
        if (downloadRequirements.contains("rootfs") && filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")) {
            downloadRequirements.remove("rootfs")
        }

        if (downloadRequirements.isEmpty()) {
            state.postValue(NoDownloadsRequired)
            return
        }
        state.postValue(DownloadsRequired(downloadRequirements, downloadRequirements.contains("rootfs")))
    }

    private fun handleDownloadAssets(downloadRequirements: HashMap<String, String>) {
        // If the state isn't updated first, AssetDownloadComplete events will be submitted before
        // the transition is acceptable.
        state.postValue(DownloadingAssets(0, downloadRequirements.size))
        downloadUtility.downloadRequirements(downloadRequirements)
    }

    private fun handleAssetsDownloadComplete(downloadId: Long) {
        val result = downloadUtility.handleDownloadComplete(downloadId)
        handleAssetDownloadState(result)
    }

    private fun handleAssetDownloadState(assetDownloadState: AssetDownloadState) {
        return when (assetDownloadState) {
            // We don't care if some other app has downloaded something, though we may intercept the
            // broadcast from the Download Manager.
            is NonUserlandDownloadFound -> {}
            is CacheSyncAttemptedWhileCacheIsEmpty -> state.postValue(AttemptedCacheAccessWhileEmpty)
            is AllDownloadsCompletedSuccessfully -> state.postValue(DownloadsHaveSucceeded)
            is CompletedDownloadsUpdate -> {
                state.postValue(DownloadingAssets(assetDownloadState.numCompleted, assetDownloadState.numTotal))
            }
            is AssetDownloadFailure -> state.postValue(DownloadsHaveFailed(assetDownloadState.reason))
        }
    }

    private fun handleSyncDownloadState() {
        if (downloadUtility.downloadStateHasBeenCached()) {
            // Syncing download state should only be necessary on process death and when the app
            // is moved back into the foreground. This means the state should either be fresh,
            // or this object has remained in memory and its state will still be downloading assets.
            state.value?.let { currentState ->
                if (currentState !is WaitingForSessionSelection && currentState !is DownloadingAssets) {
                    state.postValue(AttemptedCacheAccessInIncorrectState)
                    return
                }
                state.postValue(DownloadingAssets(0, 0)) // Reset state so events can be submitted
                handleAssetDownloadState(downloadUtility.syncStateWithCache())
            }
        }
    }

    private suspend fun handleCopyDownloadsToLocalDirectories() = withContext(Dispatchers.IO) {
        state.postValue(CopyingFilesToLocalDirectories)
        try {
            val filesystemDistributionType = downloadUtility.findDownloadedDistributionType()
            downloadUtility.moveAssetsToCorrectLocalDirectory()
            assetRepository.setLastDistributionUpdate(filesystemDistributionType)
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
// One-off events
data class IncorrectSessionTransition(val event: SessionStartupEvent, val state: SessionStartupState) : SessionStartupState()
object WaitingForSessionSelection : SessionStartupState()
object SingleSessionSupported : SessionStartupState()
data class SessionIsRestartable(val session: Session) : SessionStartupState()
data class SessionIsReadyForPreparation(val session: Session, val filesystem: Filesystem) : SessionStartupState()

// Asset retrieval states
sealed class AssetRetrievalState : SessionStartupState()
object RetrievingAssetLists : AssetRetrievalState()
data class AssetListsRetrievalSucceeded(val assetLists: HashMap<String, List<Asset>>) : AssetRetrievalState()
object AssetListsRetrievalFailed : AssetRetrievalState()

// Download requirements generation state
sealed class DownloadRequirementsGenerationState : SessionStartupState()
object GeneratingDownloadRequirements : DownloadRequirementsGenerationState()
data class DownloadsRequired(val downloadsRequired: HashMap<String, String>, val largeDownloadRequired: Boolean) : DownloadRequirementsGenerationState()
object NoDownloadsRequired : DownloadRequirementsGenerationState()
data class UnexpectedDownloadGenerationSize(val size: Int, val listNames: Set<String>) : DownloadRequirementsGenerationState()
data class UnexpectedDownloadGenerationTypes(val expectedDistribution: String, val listNames: Set<String>) : DownloadRequirementsGenerationState()

// Downloading asset states
sealed class DownloadingAssetsState : SessionStartupState()
data class DownloadingAssets(val numCompleted: Int, val numTotal: Int) : DownloadingAssetsState()
object DownloadsHaveSucceeded : DownloadingAssetsState()
data class DownloadsHaveFailed(val reason: String) : DownloadingAssetsState()
object AttemptedCacheAccessWhileEmpty : DownloadingAssetsState()
object AttemptedCacheAccessInIncorrectState : DownloadingAssetsState()

sealed class CopyingFilesLocallyState : SessionStartupState()
object CopyingFilesToLocalDirectories : CopyingFilesLocallyState()
object LocalDirectoryCopySucceeded : CopyingFilesLocallyState()
object LocalDirectoryCopyFailed : CopyingFilesLocallyState()

sealed class AssetVerificationState : SessionStartupState()
object VerifyingFilesystemAssets : AssetVerificationState()
object FilesystemAssetVerificationSucceeded : AssetVerificationState()
object AssetsAreMissingFromSupportDirectories : AssetVerificationState()
object FilesystemAssetCopyFailed : AssetVerificationState()

sealed class ExtractionState : SessionStartupState()
data class ExtractingFilesystem(val extractionTarget: String) : ExtractionState()
object ExtractionHasCompletedSuccessfully : ExtractionState()
object ExtractionFailed : ExtractionState()

sealed class SessionStartupEvent
data class SessionSelected(val session: Session) : SessionStartupEvent()
data class RetrieveAssetLists(val filesystem: Filesystem) : SessionStartupEvent()
data class GenerateDownloads(val filesystem: Filesystem, val assetLists: HashMap<String, List<Asset>>) : SessionStartupEvent()
data class DownloadAssets(val downloadRequirements: HashMap<String, String>) : SessionStartupEvent()
data class AssetDownloadComplete(val downloadAssetId: Long) : SessionStartupEvent()
object SyncDownloadState : SessionStartupEvent()
object CopyDownloadsToLocalStorage : SessionStartupEvent()
data class VerifyFilesystemAssets(val filesystem: Filesystem) : SessionStartupEvent()
data class ExtractFilesystem(val filesystem: Filesystem) : SessionStartupEvent()
object ResetSessionState : SessionStartupEvent()
