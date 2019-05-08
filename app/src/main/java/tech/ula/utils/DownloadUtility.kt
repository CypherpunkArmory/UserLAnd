package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rauschig.jarchivelib.Archiver
import org.rauschig.jarchivelib.ArchiverFactory
import tech.ula.R
import tech.ula.model.repositories.DownloadMetadata
import java.io.File
import java.io.IOException

sealed class AssetDownloadState
object CacheSyncAttemptedWhileCacheIsEmpty : AssetDownloadState()
object NonUserlandDownloadFound : AssetDownloadState()
object AllDownloadsCompletedSuccessfully : AssetDownloadState()
data class CompletedDownloadsUpdate(val numCompleted: Int, val numTotal: Int) : AssetDownloadState()
data class AssetDownloadFailure(val reason: LocalizationData) : AssetDownloadState()

class DownloadUtility(
    private val assetPreferences: AssetPreferences,
    private val downloadManagerWrapper: DownloadManagerWrapper,
    private val applicationFilesDir: File
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
            val destination = "$userlandDownloadPrefix${metadata.downloadTitle}"
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
            return AssetDownloadFailure(LocalizationData(R.string.download_failure_finished_wrong_items))
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

    @Throws(IOException::class)
    suspend fun prepareDownloadsForUse(archiverFactory: ArchiveFactoryWrapper = ArchiveFactoryWrapper()) = withContext(Dispatchers.IO) {
        val stagingDirectory = File("${applicationFilesDir.path}/staging")
        stagingDirectory.mkdirs()
        downloadDirectory.walkBottomUp()
                .filter { it.name.containsUserland() }
                .forEach {
                    if (it.name.contains("rootfs.tar.gz")) {
                        moveRootfsAssetInternal(it)
                        return@forEach
                    }
                    extractAssets(it, stagingDirectory, archiverFactory)
                }
    }

    private suspend fun moveRootfsAssetInternal(rootFsFile: File) = withContext(Dispatchers.IO) {
        val (_, repo, filename, version) = rootFsFile.name.split("-", limit = 4)
        val destinationDirectory = File("${applicationFilesDir.absolutePath}/$repo")
        val target = File("${destinationDirectory.absolutePath}/$filename")

        destinationDirectory.mkdirs()

        // Clear old rootfs parts if they exist
        val directoryFiles = destinationDirectory.listFiles()
        directoryFiles?.let {
            for (file in directoryFiles) {
                if (file.name.contains("rootfs.tar.gz.part")) file.delete()
            }
        }

        rootFsFile.copyTo(target, overwrite = true)
        rootFsFile.delete()
        assetPreferences.setLatestDownloadFilesystemVersion(repo, version)
    }

    private suspend fun extractAssets(tarFile: File, stagingDirectory: File, archiverFactory: ArchiveFactoryWrapper) = withContext(Dispatchers.IO) {
        val (_, repo, filename, version) = tarFile.name.split("-", limit = 4)
        val stagingTarget = File("${stagingDirectory.absolutePath}/$filename")
        val destination = File("${applicationFilesDir.path}/$repo")

        tarFile.copyTo(stagingTarget, overwrite = true)
        tarFile.delete()

        val archiver = archiverFactory.createArchiver(stagingTarget)
        archiver.extract(stagingTarget, destination)
        stagingDirectory.deleteRecursively()
        for (file in destination.listFiles()) {
            makePermissionsUsable(destination.absolutePath, file.name)
        }
        assetPreferences.setLatestDownloadVersion(repo, version)
    }
}

class ArchiveFactoryWrapper {
    fun createArchiver(archiverType: File): Archiver {
        return ArchiverFactory.createArchiver(archiverType)
    }
}
