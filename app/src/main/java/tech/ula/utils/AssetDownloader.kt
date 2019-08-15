package tech.ula.utils

import android.app.DownloadManager
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rauschig.jarchivelib.Archiver
import org.rauschig.jarchivelib.ArchiverFactory
import tech.ula.R
import tech.ula.model.repositories.DownloadMetadata
import tech.ula.utils.preferences.AssetPreferences
import java.io.File
import java.io.IOException

sealed class AssetDownloadState
object CacheSyncAttemptedWhileCacheIsEmpty : AssetDownloadState()
object NonUserlandDownloadFound : AssetDownloadState()
object AllDownloadsCompletedSuccessfully : AssetDownloadState()
data class CompletedDownloadsUpdate(val numCompleted: Int, val numTotal: Int) : AssetDownloadState()
data class AssetDownloadFailure(val reason: DownloadFailureLocalizationData) : AssetDownloadState()

class AssetDownloader(
    private val assetPreferences: AssetPreferences,
    private val downloadManagerWrapper: DownloadManagerWrapper,
    private val ulaFiles: UlaFiles
) {

    private val downloadDirectory = File(ulaFiles.emulatedScopedDir, "downloads")

    private val enqueuedDownloadIds = mutableSetOf<Long>()
    private val completedDownloadIds = mutableSetOf<Long>()

    init {
        if (!downloadDirectory.exists()) downloadDirectory.mkdirs()
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
            val destination = File(downloadDirectory, metadata.downloadTitle)
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
            downloadManagerWrapper.cancelAllDownloads(enqueuedDownloadIds)
            return AssetDownloadFailure(reason)
        }

        completedDownloadIds.add(downloadId)
        if (completedDownloadIds.size != enqueuedDownloadIds.size) {
            return CompletedDownloadsUpdate(completedDownloadIds.size, enqueuedDownloadIds.size)
        }

        if (!enqueuedDownloadIds.containsAll(completedDownloadIds)) {
            return AssetDownloadFailure(DownloadFailureLocalizationData(R.string.download_failure_finished_wrong_items))
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
                file.delete()
            }
        }
    }

    @Throws(IOException::class)
    suspend fun prepareDownloadsForUse(archiverFactory: ArchiveFactoryWrapper = ArchiveFactoryWrapper()) = withContext(Dispatchers.IO) {
        val stagingDirectory = File("${ulaFiles.filesDir.path}/staging")
        stagingDirectory.mkdirs()
        val downloadFiles = downloadDirectory.listFiles() ?: return@withContext
        downloadFiles.forEach {
            if (it.name.contains("rootfs.tar.gz")) {
                moveRootfsAssetInternal(it)
                return@forEach
            }
            extractAssets(it, stagingDirectory, archiverFactory)
        }
        stagingDirectory.deleteRecursively()
    }

    private suspend fun moveRootfsAssetInternal(rootFsFile: File) = withContext(Dispatchers.IO) {
        val (repo, filename, version) = rootFsFile.name.split("-", limit = 4)
        val destinationDirectory = File("${ulaFiles.filesDir.absolutePath}/$repo")
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
        val (repo, filename, version) = tarFile.name.split("-", limit = 3)
        val stagingTarget = File("${stagingDirectory.absolutePath}/$filename")
        val destination = File("${ulaFiles.filesDir.path}/$repo")

        tarFile.copyTo(stagingTarget, overwrite = true)
        tarFile.delete()

        val archiver = archiverFactory.createArchiver(stagingTarget)
        archiver.extract(stagingTarget, destination)
        val extractedFiles = destination.listFiles() ?: return@withContext
        for (file in extractedFiles) {
            ulaFiles.makePermissionsUsable(destination.absolutePath, file.name)
        }
        assetPreferences.setLatestDownloadVersion(repo, version)
    }
}

class DownloadManagerWrapper(private val downloadManager: DownloadManager) {
    fun generateDownloadRequest(url: String, destination: File): DownloadManager.Request {
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        val destinationUri = Uri.fromFile(destination)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle(destination.name)
        request.setDescription("Downloading ${destination.name.substringAfterLast("-")}.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationUri(destinationUri)
        return request
    }

    fun enqueue(request: DownloadManager.Request): Long {
        return downloadManager.enqueue(request)
    }

    private fun generateQuery(id: Long): DownloadManager.Query {
        val query = DownloadManager.Query()
        query.setFilterById(id)
        return query
    }

    private fun generateCursor(query: DownloadManager.Query): Cursor {
        return downloadManager.query(query)
    }

    fun downloadHasSucceeded(id: Long): Boolean {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
        return false
    }

    fun downloadHasFailed(id: Long): Boolean {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_FAILED
        }
        return false
    }

    fun getDownloadFailureReason(id: Long): DownloadFailureLocalizationData {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
            return DownloadFailureLocalizationData(resId = when (status) {
                in 100..500 -> R.string.download_failure_http_error
                1008 -> R.string.download_failure_cannot_resume
                1007 -> R.string.download_failure_no_external_devices
                1009 -> R.string.download_failure_destination_exists
                1001 -> R.string.download_failure_unknown_file_error
                1004 -> R.string.download_failure_http_processing
                1006 -> R.string.download_failure_insufficient_external_storage
                1005 -> R.string.download_failure_too_many_redirects
                1002 -> R.string.download_failure_unhandled_http_response
                1000 -> R.string.download_failure_unknown_error
                else -> R.string.download_failure_missing_error
            }, formatStrings = listOf("$status")) // Format strings only used for http_error
        }
        return DownloadFailureLocalizationData(R.string.download_failure_reason_not_found)
    }

    fun cancelAllDownloads(downloadIds: Set<Long>) {
        downloadManager.remove(*downloadIds.toLongArray())
    }
}

class ArchiveFactoryWrapper {
    fun createArchiver(archiverType: File): Archiver {
        return ArchiverFactory.createArchiver(archiverType)
    }
}
