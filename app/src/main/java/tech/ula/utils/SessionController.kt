package tech.ula.utils

import android.content.res.Resources
import kotlinx.coroutines.experimental.delay
import tech.ula.R
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository

class SessionController(
    private val filesystem: Filesystem,
    private val assetRepository: AssetRepository,
    private val filesystemUtility: FilesystemUtility
) {

    fun getAssetLists(): List<List<Asset>> {
        return try {
            assetRepository.retrieveAllRemoteAssetLists()
        } catch (err: Exception) {
            assetRepository.getCachedAssetLists()
        }
    }

    fun getDownloadRequirements(
        assetLists: List<List<Asset>>,
        forceDownloads: Boolean,
        networkUtility: NetworkUtility
    ): DownloadRequirementsResult {
        var wifiRequired = false
        val requiredDownloads: List<Asset> = assetLists.map { assetList ->
            assetList.filter { asset ->
                val needsUpdate = assetRepository.doesAssetNeedToUpdated(asset)
                if (asset.isLarge && needsUpdate) {

                    // Avoid overhead of updating filesystems unless they will need to be
                    // extracted.
                    if (filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}"))
                        return@filter false

                    if (!forceDownloads && !networkUtility.wifiIsEnabled()) {
                        wifiRequired = true
                        return@map listOf<Asset>()
                    }
                }
                needsUpdate
            }
        }.flatten()

        if (wifiRequired) return RequiresWifiResult(isRequired = true)
        return RequiredAssetsResult(requiredDownloads)
    }

    suspend fun downloadRequirements(
        requiredDownloads: List<Asset>,
        downloadBroadcastReceiver: DownloadBroadcastReceiver,
        downloadUtility: DownloadUtility,
        progressBarUpdater: (String, String) -> Unit,
        resources: Resources
    ) {
        val downloadedIds = ArrayList<Long>()
        downloadBroadcastReceiver.setDoOnReceived {
            downloadedIds.add(it)
            downloadUtility.setTimestampForDownloadedFile(it)
        }
        val downloadIds = downloadUtility.downloadRequirements(requiredDownloads)
        while (downloadIds.size != downloadedIds.size) {
            progressBarUpdater(resources.getString(R.string.progress_downloading),
                    resources.getString(R.string.progress_downloading_out_of,
                            downloadedIds.size, downloadIds.size))
            delay(500)
        }

        progressBarUpdater(resources.getString(R.string.progress_copying_downloads), "")
        downloadUtility.moveAssetsToCorrectLocalDirectory()
    }

    // Return value represents successful extraction. Also true if extraction is unnecessary.
    suspend fun extractFilesystemIfNeeded(filesystemExtractLogger: (line: String) -> Unit): Boolean {
        val filesystemDirectoryName = "${filesystem.id}"
        if (!filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            filesystemUtility.copyDistributionAssetsToFilesystem(filesystemDirectoryName, filesystem.distributionType)

            return asyncAwait {
                filesystemUtility.extractFilesystem(filesystemDirectoryName, filesystemExtractLogger)
                while (!filesystemUtility.isExtractionComplete(filesystemDirectoryName)) {
                    delay(500)
                }
                return@asyncAwait filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)
            }
        }
        return true
    }

    fun ensureFilesystemHasRequiredAssets() {
        val filesystemDirectoryName = "${filesystem.id}"
        val requiredDistributionAssets = assetRepository.getDistributionAssetsList(filesystem.distributionType)
        if (!filesystemUtility.areAllRequiredAssetsPresent(filesystemDirectoryName, requiredDistributionAssets)) {
            filesystemUtility.copyDistributionAssetsToFilesystem(filesystemDirectoryName, filesystem.distributionType)
            filesystemUtility.removeRootfsFilesFromFilesystem(filesystemDirectoryName)
        }
    }

    suspend fun activateSession(session: Session, serverUtility: ServerUtility): Session {
        session.pid = serverUtility.startServer(session)

        while (!serverUtility.isServerRunning(session)) {
            delay(500)
        }

        return session
    }
}

sealed class DownloadRequirementsResult
data class RequiresWifiResult(val isRequired: Boolean) : DownloadRequirementsResult()
data class RequiredAssetsResult(val assetList: List<Asset>) : DownloadRequirementsResult()