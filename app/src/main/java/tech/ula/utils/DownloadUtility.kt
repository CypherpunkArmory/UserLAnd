package tech.ula.utils

import tech.ula.model.entities.Asset
import tech.ula.model.state.DownloadingRequirements
import tech.ula.model.state.DownloadsHaveFailed
import java.io.File

sealed class AssetDownloadResult
object NonUserlandDownload : AssetDownloadResult()
object AllDownloadsCompletedSuccessfully : AssetDownloadResult()
data class DownloadCompletedSuccessfully(val numCompleted: Int, val numTotal: Int) : AssetDownloadResult()
data class AssetDownloadFailure(val reason: String) : AssetDownloadResult()

class DownloadUtility(
    private val assetPreferences: AssetPreferences,
    private val downloadManagerWrapper: DownloadManagerWrapper,
    private val applicationFilesDir: File,
    private val timeUtility: TimeUtility = TimeUtility()
) {

    private val downloadDirectory = downloadManagerWrapper.getDownloadsDirectory()
    private val userlandDownloadPrefix = "UserLAnd-"

    private val enqueuedDownloadIds = mutableListOf<Long>()
    private val completedDownloadIds = mutableListOf<Long>()

    private fun String.containsUserland(): Boolean {
        return this.toLowerCase().contains(userlandDownloadPrefix.toLowerCase())
    }

    fun checkCachedState() {
        if (assetPreferences.getDownloadsAreInProgress()) {
            enqueuedDownloadIds.addAll(assetPreferences.getEnqueuedDownloads())
            completedDownloadIds.addAll(findCompletedDownloadsInDownloadsDirectory())
        }
    }

    fun downloadRequirements(assetList: List<Asset>) {
        clearPreviousDownloadsFromDownloadsDirectory()
        assetPreferences.clearEnqueuedDownloadsCache()

        // TODO test
        enqueuedDownloadIds.addAll(assetList.map { download(it) })
        assetPreferences.setDownloadsAreInProgress(inProgress = true)
        assetPreferences.setEnqueuedDownloads(enqueuedDownloadIds)
    }

    fun handleDownloadComplete(downloadId: Long): AssetDownloadResult {
        if(!downloadIsForUserland(downloadId)) return NonUserlandDownload

        if (!downloadedSuccessfully(downloadId)) {
            val reason = getReasonForDownloadFailure(downloadId)
            return AssetDownloadFailure(reason)
        }

        completedDownloadIds.add(downloadId)
        setTimestampForDownloadedFile(downloadId)
        if (completedDownloadIds.size != enqueuedDownloadIds.size) {
            return DownloadCompletedSuccessfully(completedDownloadIds.size, enqueuedDownloadIds.size)
        }

        enqueuedDownloadIds.sort()
        completedDownloadIds.sort()
        if (enqueuedDownloadIds != completedDownloadIds) {
            return AssetDownloadFailure("Tried to finish download process with items we did not enqueue.")
        }

        assetPreferences.setDownloadsAreInProgress(inProgress = false)
        assetPreferences.clearEnqueuedDownloadsCache()
        return AllDownloadsCompletedSuccessfully
    }

    private fun downloadIsForUserland(id: Long): Boolean {
        val downloadTitle = downloadManagerWrapper.getDownloadTitle(id)
        return downloadTitle.containsUserland()
    }

    fun downloadedSuccessfully(id: Long): Boolean {
        return downloadManagerWrapper.downloadHasNotFailed(id)
    }

    fun getReasonForDownloadFailure(id: Long): String {
        return downloadManagerWrapper.getDownloadFailureReason(id)
    }

    private fun findCompletedDownloadsInDownloadsDirectory(): List<Long> {
        // TODO use enqueued downloads to find titles and determine if they've finished successfully, adding those to completed downloads
        // TODO think about what happens if downloads are in progress, or complete while this setup is happening
    }

    private fun download(asset: Asset): Long {
        var branch = "master"
        if (asset.distributionType.equals("support", true))
            branch = "staging"
        val url = "https://github.com/CypherpunkArmory/UserLAnd-Assets-" +
                "${asset.distributionType}/raw/$branch/assets/" +
                "${asset.architectureType}/${asset.name}"
        val destination = asset.concatenatedName
        val request = downloadManagerWrapper.generateDownloadRequest(url, destination)
        deletePreviousDownloadFromLocalDirectory(asset)
        return downloadManagerWrapper.enqueue(request)
    }

    private fun clearPreviousDownloadsFromDownloadsDirectory() {
        val downloadDirectoryFiles = downloadDirectory.listFiles()
        downloadDirectoryFiles?.let {
            for (file in downloadDirectoryFiles) {
                if (file.name.containsUserland()) {
                    file.delete()
                }
            }
        }
    }

    private fun deletePreviousDownloadFromLocalDirectory(asset: Asset) {
        val localFile = File(applicationFilesDir, asset.pathName)

        if (localFile.exists())
            localFile.delete()
    }

    fun setTimestampForDownloadedFile(id: Long) {
        val titleName = downloadManagerWrapper.getDownloadTitle(id)
        if (!titleName.containsUserland()) return
        // Title should be asset.concatenatedName
        val currentTimeSeconds = timeUtility.getCurrentTimeSeconds() // TODO test
        assetPreferences.setLastUpdatedTimestampForAssetUsingConcatenatedName(titleName, currentTimeSeconds)
    }

    @Throws(Exception::class)
    fun moveAssetsToCorrectLocalDirectory() {
        downloadDirectory.walkBottomUp()
                .filter { it.name.containsUserland() }
                .forEach {
                    val delimitedContents = it.name.split("-", limit = 3)
                    if (delimitedContents.size != 3) return@forEach
                    val (_, directory, filename) = delimitedContents
                    val containingDirectory = File("${applicationFilesDir.path}/$directory")
                    val targetDestination = File("${containingDirectory.path}/$filename")
                    it.copyTo(targetDestination, overwrite = true)
                    makePermissionsUsable(containingDirectory.path, filename)
                    it.delete()
                }
    }
}
