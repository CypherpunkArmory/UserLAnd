package tech.ula.model.repositories

import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.remote.GithubApiClient
import tech.ula.utils.AssetPreferences
import tech.ula.utils.ConnectionUtility
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.Exception

data class DownloadMetadata(
    val filename: String,
    val assetType: String,
    val versionCode: String,
    val url: String,
    val downloadTitle: String = "$assetType-$filename-$versionCode"
)

class AssetRepository(
    private val applicationFilesDirPath: String,
    private val assetPreferences: AssetPreferences,
    private val githubApiClient: GithubApiClient = GithubApiClient(),
    private val connectionUtility: ConnectionUtility = ConnectionUtility()
) {

    @Throws(IllegalStateException::class)
    suspend fun generateDownloadRequirements(
        filesystem: Filesystem,
        assetLists: HashMap<String, List<Asset>>,
        filesystemNeedsExtraction: Boolean
    ): List<DownloadMetadata> {
        val downloadRequirements = mutableListOf<DownloadMetadata>()
        for (entry in assetLists) {
            val (repo, list) = entry
            // Empty lists should not have propagated this deeply.
            if (list.isEmpty()) throw IllegalStateException()
            if (assetsArePresentInSupportDirectories(list) && lastDownloadedVersionIsUpToDate(repo))
                continue
            val filename = "assets.tar.gz"
            val versionCode = githubApiClient.getLatestReleaseVersion(repo)
            val url = githubApiClient.getAssetEndpoint(filename, repo)
            val downloadMetadata = DownloadMetadata(filename, repo, versionCode, url)
            downloadRequirements.add(downloadMetadata)
        }
        if (filesystemNeedsExtraction && rootFsDownloadRequired(filesystem)) {
            val repo = filesystem.distributionType
            val filename = "rootfs.tar.gz"
            val versionCode = githubApiClient.getLatestReleaseVersion(repo)
            val url = githubApiClient.getAssetEndpoint(filename, repo)
            val downloadMetadata = DownloadMetadata(filename, repo, versionCode, url)
            downloadRequirements.add(downloadMetadata)
        }
        return downloadRequirements
    }

    fun getDistributionAssetsForExistingFilesystem(filesystem: Filesystem): List<Asset> {
        return assetPreferences.getCachedAssetList(filesystem.distributionType)
    }

    fun getLatestDistributionVersion(distributionType: String): String {
        return assetPreferences.getLatestDownloadVersion(distributionType)
    }

    fun assetsArePresentInSupportDirectories(assets: List<Asset>): Boolean {
        for (asset in assets) {
            val assetFile = File("$applicationFilesDirPath/${asset.pathName}")
            if (!assetFile.exists()) return false
        }
        return true
    }

    suspend fun getAllAssetLists(distributionType: String): HashMap<String, List<Asset>> {
        val lists = hashMapOf<String, List<Asset>>()
        listOf(distributionType, "support").forEach { repo ->
            try {
                val list = fetchAssetList(repo)
                assetPreferences.setAssetList(repo, list)
                lists[repo] = list
            } catch (err: Exception) {
                lists[repo] = assetPreferences.getCachedAssetList(repo)
            }
        }
        return lists
    }

    private suspend fun fetchAssetList(assetType: String): List<Asset> {
        val downloadUrl = githubApiClient.getAssetsListDownloadUrl(assetType)

        val inputStream = connectionUtility.getUrlInputStream(downloadUrl)
        val reader = BufferedReader(InputStreamReader(inputStream))

        val assetList = mutableListOf<Asset>()
        reader.forEachLine {
            val filename = it.substringBefore(' ')
            if (filename == "assets.txt") return@forEachLine
            assetList.add(Asset(filename, assetType))
        }

        reader.close()

        return assetList
    }

    private suspend fun lastDownloadedVersionIsUpToDate(repo: String): Boolean {
        val latestCached = assetPreferences.getLatestDownloadVersion(repo)
        val latestRemote = githubApiClient.getLatestReleaseVersion(repo)
        return latestCached >= latestRemote
    }

    private suspend fun lastDownloadedFilesystemVersionIsUpToDate(repo: String): Boolean {
        val latestCached = assetPreferences.getLatestDownloadFilesystemVersion(repo)
        val latestRemote = githubApiClient.getLatestReleaseVersion(repo)
        return latestCached >= latestRemote
    }

    private suspend fun rootFsDownloadRequired(filesystem: Filesystem): Boolean {
        val rootFsFile = File("$applicationFilesDirPath/${filesystem.distributionType}/rootfs.tar.gz")
        return !rootFsFile.exists() || !lastDownloadedFilesystemVersionIsUpToDate(filesystem.distributionType)
    }
}