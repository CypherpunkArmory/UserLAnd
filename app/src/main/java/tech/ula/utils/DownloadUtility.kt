package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType
import tech.ula.model.entities.Asset
import tech.ula.model.repositories.DownloadMetadata
import java.io.*
import java.util.zip.GZIPInputStream

sealed class AssetDownloadState
object CacheSyncAttemptedWhileCacheIsEmpty : AssetDownloadState()
object NonUserlandDownloadFound : AssetDownloadState()
object AllDownloadsCompletedSuccessfully : AssetDownloadState()
data class CompletedDownloadsUpdate(val numCompleted: Int, val numTotal: Int) : AssetDownloadState()
data class AssetDownloadFailure(val reason: String) : AssetDownloadState()

class DownloadUtility(
    private val assetPreferences: AssetPreferences,
    private val downloadManagerWrapper: DownloadManagerWrapper,
    private val applicationFilesDir: File,
    private val timeUtility: TimeUtility = TimeUtility()
) {
    private val downloadDirectory = downloadManagerWrapper.getDownloadsDirectory()

    private val userlandDownloadPrefix = "UserLAnd-"

    private val enqueuedDownloadIds = mutableSetOf<Long>()
    private val completedDownloadIds = mutableSetOf<Long>()

    private fun String.containsUserland(): Boolean {
        return this.toLowerCase().contains(userlandDownloadPrefix.toLowerCase())
    }

    fun downloadStateHasBeenCached(): Boolean {
        return assetPreferences.getDownloadsAreInProgress()
    }

    fun syncStateWithCache(): AssetDownloadState {
        if (!downloadStateHasBeenCached()) return CacheSyncAttemptedWhileCacheIsEmpty

        enqueuedDownloadIds.addAll(assetPreferences.getEnqueuedDownloads())

        for (id in enqueuedDownloadIds) {
            // Skip in-progress downloads
            if (!downloadManagerWrapper.downloadHasFailed(id) && !downloadManagerWrapper.downloadHasSucceeded(id)) {
                continue
            }
            val state = handleDownloadComplete(id)
            if (state !is CompletedDownloadsUpdate) return state
        }
        return CompletedDownloadsUpdate(completedDownloadIds.size, enqueuedDownloadIds.size)
    }

    fun downloadRequirements(downloadRequirements: List<DownloadMetadata>) {
        clearPreviousDownloadsFromDownloadsDirectory()
        assetPreferences.clearEnqueuedDownloadsCache()
        enqueuedDownloadIds.clear()
        completedDownloadIds.clear()

        enqueuedDownloadIds.addAll(downloadRequirements.map { metadata ->
            val destination = "$userlandDownloadPrefix${metadata.assetType}-${metadata.filename}-${metadata.versionCode}"
            val request = downloadManagerWrapper.generateDownloadRequest(metadata.url, destination)
            downloadManagerWrapper.enqueue(request)
        })
        assetPreferences.setDownloadsAreInProgress(inProgress = true)
        assetPreferences.setEnqueuedDownloads(enqueuedDownloadIds)
    }

    fun handleDownloadComplete(downloadId: Long): AssetDownloadState {
        if (!downloadIsForUserland(downloadId)) return NonUserlandDownloadFound

        if (downloadManagerWrapper.downloadHasFailed(downloadId)) {
            val reason = downloadManagerWrapper.getDownloadFailureReason(downloadId)
            return AssetDownloadFailure(reason)
        }

        completedDownloadIds.add(downloadId)
        if (completedDownloadIds.size != enqueuedDownloadIds.size) {
            return CompletedDownloadsUpdate(completedDownloadIds.size, enqueuedDownloadIds.size)
        }

        if (!enqueuedDownloadIds.containsAll(completedDownloadIds)) {
            return AssetDownloadFailure("Tried to finish download process with items we did not enqueue.")
        }

        enqueuedDownloadIds.clear()
        completedDownloadIds.clear()
        assetPreferences.setDownloadsAreInProgress(inProgress = false)
        assetPreferences.clearEnqueuedDownloadsCache()
        return AllDownloadsCompletedSuccessfully
    }

    fun downloadIsForUserland(id: Long): Boolean {
        return enqueuedDownloadIds.contains(id)
    }

    fun findDownloadedDistributionType(): String {
        downloadDirectory.listFiles()?.forEach { downloadedFile ->
            if (downloadedFile.name.containsUserland() && !downloadedFile.name.contains("support")) {
                val (_, distributionType, _) = downloadedFile.name.split("-", limit = 3)
                return distributionType
            }
        }
        return ""
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

    suspend fun prepareDownloadsForUse() = withContext(Dispatchers.IO) {
        val stagingDirectory = File("${applicationFilesDir.path}/staging")
        stagingDirectory.mkdirs()
        downloadDirectory.walkBottomUp()
                .filter { it.name.containsUserland() }
                .forEach {
                    // Use so assets from distribution and support don't overwrite each other
                    if (it.name.contains("rootfs.tar.gz")) {
                        moveRootfsAssetInternal(it)
                        return@forEach
                    }
                    extractAsset(it)
                }
    }

    private suspend fun moveRootfsAssetInternal(rootFsFile: File) = withContext(Dispatchers.IO) {
        val (_, repo, filename, version) = rootFsFile.name.split("-", limit = 4)
        val destinationDirectory = File("${applicationFilesDir.absolutePath}/$repo")
        val target = File("${destinationDirectory.absolutePath}/$filename")

        destinationDirectory.mkdirs()
        rootFsFile.copyTo(target, overwrite = true)
        rootFsFile.delete()
        assetPreferences.setLatestDownloadFilesystemVersion(repo, version)
    }

    private suspend fun extractAsset(tarFile: File) = withContext(Dispatchers.IO) {
        val (_, repo, filename, version) = tarFile.name.split("-", limit = 4)
        val stagingDirectory = File("${applicationFilesDir.absolutePath}/staging")
        val stagingTarget = File("${stagingDirectory.absolutePath}/$filename")
        val destination = File("${applicationFilesDir.path}/$repo")

        stagingDirectory.mkdirs()
        tarFile.copyTo(stagingTarget, overwrite = true)
        tarFile.delete()

        val archiver = ArchiverFactory.createArchiver(stagingTarget)
        archiver.extract(stagingTarget, destination)
        stagingDirectory.deleteRecursively()
        assetPreferences.setLatestDownloadVersion(repo, version)
    }
}
