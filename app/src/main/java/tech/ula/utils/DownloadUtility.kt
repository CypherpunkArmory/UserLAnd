package tech.ula.utils

import android.app.DownloadManager
import tech.ula.model.entities.Asset
import java.io.File

class DownloadUtility(
    private val downloadManager: DownloadManager,
    private val timestampPreferences: TimestampPreferences,
    private val downloadManagerWrapper: DownloadManagerWrapper,
    private val applicationFilesDir: File
) {

    private val downloadDirectory = downloadManagerWrapper.getDownloadsDirectory()

    fun downloadRequirements(assetList: List<Asset>): List<Pair<Asset, Long>> {
        return assetList.map { it to download(it) }
    }

    private fun download(asset: Asset): Long {
        val url = "https://github.com/CypherpunkArmory/UserLAnd-Assets-" +
                "${asset.distributionType}/raw/master/assets/" +
                "${asset.architectureType}/${asset.name}"
        val destination = "UserLAnd:${asset.concatenatedName}"
        val request = downloadManagerWrapper.generateDownloadRequest(url, destination)
        deletePreviousDownload(asset)

        return downloadManager.enqueue(request)
    }

    private fun deletePreviousDownload(asset: Asset) {
        val downloadsDirectoryFile = File(downloadDirectory, "UserLAnd:${asset.concatenatedName}")
        val localFile = File(applicationFilesDir, asset.pathName)

        if (downloadsDirectoryFile.exists())
            downloadsDirectoryFile.delete()
        if (localFile.exists())
            localFile.delete()
    }

    fun setTimestampForDownloadedFile(id: Long) {
        val query = downloadManagerWrapper.generateQuery(id)
        val cursor = downloadManagerWrapper.generateCursor(downloadManager, query)
        val titleName = downloadManagerWrapper.getDownloadTitle(cursor)
        if (titleName == "" || !titleName.contains("UserLAnd")) return
        val assetConcatenatedName = titleName.substringAfter(":")
        timestampPreferences.setSavedTimestampForFileToNow(assetConcatenatedName)
    }

    fun moveAssetsToCorrectLocalDirectory() {
        downloadDirectory.walkBottomUp()
                .filter { it.name.contains("UserLAnd:") }
                .forEach {
                    val (_, directory, filename) = it.name.split(":")
                    val containingDirectory = File("${applicationFilesDir.path}/$directory")
                    val targetDestination = File("${containingDirectory.path}/$filename")
                    val fileAsByteArray = it.readBytes()
                    containingDirectory.mkdirs()
                    targetDestination.createNewFile()
                    targetDestination.writeBytes(fileAsByteArray)
                    makePermissionsUsable(containingDirectory.path, filename)
                    it.delete()
                }
    }
}
