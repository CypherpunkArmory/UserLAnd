package tech.ula.utils

import kotlinx.coroutines.experimental.delay
import tech.ula.R
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository

class SessionController(
    private val filesystem: Filesystem,
    resourcesFetcher: ResourcesFetcher,
    private val assetRepository: AssetRepository,
    private val filesystemUtility: FilesystemUtility,
    private val progressBarUpdater: (String, String) -> Unit,
    private val dialogBroadcaster: (String) -> Unit
) {

    private val resources = resourcesFetcher.getAppResources()

    fun getAssetLists(
        networkUtility: NetworkUtility
    ): List<List<Asset>> {

        progressBarUpdater(resources.getString(R.string.progress_fetching_asset_lists), "")
        val assetLists = if (!networkUtility.networkIsActive()) {
            assetRepository.getCachedAssetLists()
        } else {
            assetRepository.retrieveAllRemoteAssetLists(networkUtility.httpsIsAccessible())
        }
        if (assetLists.any { it.isEmpty() }) {
            dialogBroadcaster("errorFetchingAssetLists")
            return listOf(listOf())
        }
        return assetLists
    }

    // Return value represents whether wifi is required for downloads.
    suspend fun downloadRequirements(
        assetLists: List<List<Asset>>,
        forceDownloads: Boolean,
        downloadBroadcastReceiver: DownloadBroadcastReceiver,
        downloadUtility: DownloadUtility,
        networkUtility: NetworkUtility
    ): Boolean {
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
                        dialogBroadcaster("wifiRequired")
                        return@map listOf<Asset>()
                    }
                }
                needsUpdate
            }
        }.flatten()

        if (wifiRequired) return true

        val downloadedIds = ArrayList<Long>()
        downloadBroadcastReceiver.setDoOnReceived { downloadedIds.add(it) }
        val downloadIds = downloadUtility.downloadRequirements(requiredDownloads)
        while (downloadIds.size != downloadedIds.size) {
            progressBarUpdater(resources.getString(R.string.progress_downloading),
                    resources.getString(R.string.progress_downloading_out_of,
                            downloadedIds.size, downloadIds.size))
            delay(500)
        }
        downloadUtility.moveAssetsToCorrectLocalDirectory()
        return false
    }

    // Return value represents successful extraction. Also true if extraction is unnecessary.
    suspend fun extractFilesystemIfNeeded(filesystemExtractLogger: (line: String) -> Unit): Boolean {
        val filesystemDirectoryName = "${filesystem.id}"
        if (!filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)) {
            filesystemUtility.copyDistributionAssetsToFilesystem(filesystemDirectoryName, filesystem.distributionType)

            val extractionSuccess = asyncAwait {
                filesystemUtility.extractFilesystem(filesystemDirectoryName, filesystemExtractLogger)
                while (!filesystemUtility.isExtractionComplete(filesystemDirectoryName)) {
                    delay(500)
                }
                return@asyncAwait filesystemUtility.hasFilesystemBeenSuccessfullyExtracted(filesystemDirectoryName)
            }

            if (!extractionSuccess) {
                dialogBroadcaster("extractionFailed")
            }
            return extractionSuccess
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