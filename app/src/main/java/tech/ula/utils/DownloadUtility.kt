package tech.ula.utils

import android.app.DownloadManager
import tech.ula.model.entities.Asset
import java.io.File

class DownloadUtility(
    private val downloadManager: DownloadManager,
    private val timestampPreferenceUtility: TimestampPreferenceUtility,
    private val requestUtility: RequestUtility,
    private val downloadDirectory: File,
    private val applicationFilesDir: File
) {

    fun downloadRequirements(assetList: List<Asset>): List<Long> {
        return assetList.map { download(it) }
    }

    private fun download(asset: Asset): Long {
        val url = "https://github.com/CypherpunkArmory/UserLAnd-Assets-" +
                "${asset.distributionType}/raw/master/assets/" +
                "${asset.architectureType}/${asset.name}"
        val destination = "UserLAnd:${asset.concatenatedName}"
        val request = requestUtility.generateTypicalDownloadRequest(url, destination)
        deletePreviousDownload(asset)

        timestampPreferenceUtility.setSavedTimestampForFileToNow(asset.concatenatedName)

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

    fun moveAssetsToCorrectLocalDirectory() {
        downloadDirectory.walkBottomUp()
                .filter { it.name.contains("UserLAnd:") }
                .forEach {
                    val (_, directory, filename) = it.name.split(":")
                    val containingDirectoryPath = "${applicationFilesDir.path}/$directory"
                    val targetDestinationPath = "$containingDirectoryPath/$filename"
                    val targetDestination = File(targetDestinationPath)
                    it.copyTo(targetDestination, overwrite = true)
                    makePermissionsUsable(containingDirectoryPath, filename)
                }
    }
}
