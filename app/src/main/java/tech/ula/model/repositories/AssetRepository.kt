package tech.ula.model.repositories

import android.arch.lifecycle.MutableLiveData
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.remote.GithubApiClient
import tech.ula.utils.AssetPreferences
import tech.ula.utils.BuildWrapper
import tech.ula.utils.ConnectionUtility
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.IllegalStateException
import kotlin.Exception

data class DownloadMetadata(val filename: String, val assetType: String, val versionCode: String, val url: String)

class AssetRepository(
    private val applicationFilesDirPath: String,
    private val assetPreferences: AssetPreferences,
    private val githubApiClient: GithubApiClient = GithubApiClient(),
    private val connectionUtility: ConnectionUtility = ConnectionUtility()
) {

    suspend fun lastDownloadedVersionIsUpToDate(repo: String): Boolean {
        val latestCached = assetPreferences.getLatestDownloadVersion(repo)
        val latestRemote = githubApiClient.getLatestReleaseVersion(repo)
        return latestCached >= latestRemote
    }

    suspend fun lastDownloadedFilesystemVersionIsUpToDate(repo: String): Boolean {
        val latestCached = assetPreferences.getLatestDownloadFilesystemVersion(repo)
        val latestRemote = githubApiClient.getLatestReleaseVersion(repo)
        return latestCached > latestRemote
    }

    suspend fun generateDownloadRequirements(
            filesystem: Filesystem,
            assetLists: HashMap<String, List<Asset>>,
            filesystemHasAlreadyBeenExtracted: Boolean
    ): List<DownloadMetadata> {
        val downloadRequirements = mutableListOf<DownloadMetadata>()
        for (entry in assetLists) {
            val (repo, list) = entry
            if (assetsArePresentInSupportDirectories(list) && lastDownloadedVersionIsUpToDate(repo))
                continue
            val filename = "assets"
            val versionCode = githubApiClient.getLatestReleaseVersion(repo)
            val url = githubApiClient.getAssetEndpoint(filename, repo)
            val downloadMetadata = DownloadMetadata(filename, versionCode, url, repo)
            downloadRequirements.add(downloadMetadata)
        }
        if (!filesystemHasAlreadyBeenExtracted && rootFsDownloadRequired(filesystem)) {
            val repo = filesystem.distributionType
            val filename = "rootfs"
            val versionCode = githubApiClient.getLatestReleaseVersion(repo)
            val url = githubApiClient.getAssetEndpoint(filename, repo)
            val downloadMetadata = DownloadMetadata(filename, versionCode, url, repo)
            downloadRequirements.add(downloadMetadata)
        }
        return downloadRequirements
    }

    suspend fun rootFsDownloadRequired(filesystem: Filesystem): Boolean {
        // TODO Filesystem.iscreatedfrombackup
        val rootFsFile = File("$applicationFilesDirPath/${filesystem.distributionType}/rootfs.tar.gz")
        return !rootFsFile.exists() || !lastDownloadedFilesystemVersionIsUpToDate(filesystem.distributionType)
    }

    fun getDistributionAssetsForExistingFilesystem(filesystem: Filesystem): List<Asset> {
        val distributionType = filesystem.distributionType
        val deviceArchitecture = filesystem.archType
        val distributionAssetListTypes = listOf(
                distributionType to "all",
                distributionType to deviceArchitecture
        )
        val allAssets = assetPreferences.getAssetLists(distributionAssetListTypes)
        return allAssets.flatten().filter { !(it.name.contains("rootfs.tar.gz")) }
    }

    fun assetsArePresentInSupportDirectories(assets: List<Asset>): Boolean {
        for (asset in assets) {
            val assetFile = File("$applicationFilesDirPath/${asset.pathName}")
            if (!assetFile.exists()) return false
        }
        return true
    }

    fun getLastDistributionUpdate(distributionType: String): Long {
        return assetPreferences.getLastDistributionUpdate(distributionType)
    }

    fun setLastDistributionUpdate(distributionType: String) {
        assetPreferences.setLastDistributionUpdate(distributionType)
    }

    suspend fun getAllAssetLists(distributionType: String): HashMap<String, List<Asset>> {
        val lists = hashMapOf<String, List<Asset>>()
        listOf(distributionType, "support").forEach { repo ->
            try {
                val list = fetchAssetList(repo)
                assetPreferences.setAssetList(repo, "", list) // TODO arch type
                lists[repo] = list
            } catch (err: Exception) {
                lists[repo] = assetPreferences.getCachedAssetList(repo)
            }
        }
        return lists
    }

    @Throws()
    private suspend fun fetchAssetList(assetType: String): List<Asset> {
        val downloadUrl = githubApiClient.getAssetsListDownloadUrl(assetType)

        val inputStream = connectionUtility.getUrlInputStream(downloadUrl)
        val reader = BufferedReader(InputStreamReader(inputStream))

        val assetList = mutableListOf<Asset>()
        reader.forEachLine {
            val (filename, timestamp) = it.split(" ")
            if (filename == "assets.txt") return@forEachLine
            // TODO figure out what to do about architecture type. don't think we care about it
            assetList.add(Asset(filename, assetType, architectureType = "", remoteTimestamp = timestamp.toLong()))
        }

        reader.close()

        return assetList
    }
}