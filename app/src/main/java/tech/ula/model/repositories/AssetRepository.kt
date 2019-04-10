package tech.ula.model.repositories

import android.accounts.NetworkErrorException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.remote.GithubApiClient
import tech.ula.utils.AcraWrapper
import tech.ula.utils.AssetPreferences
import tech.ula.utils.ConnectionUtility
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.UnknownHostException
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
    private val connectionUtility: ConnectionUtility = ConnectionUtility(),
    private val acraWrapper: AcraWrapper = AcraWrapper()
) {

    @Throws(IllegalStateException::class, UnknownHostException::class)
    suspend fun generateDownloadRequirements(
        filesystem: Filesystem,
        assetLists: HashMap<String, List<Asset>>,
        filesystemNeedsExtraction: Boolean
    ): List<DownloadMetadata> {
        val downloadRequirements = mutableListOf<DownloadMetadata>()
        for (entry in assetLists) {
            val (repo, list) = entry
            // Empty lists should not have propagated this deeply.
            if (list.isEmpty()) {
                val err = IllegalStateException()
                acraWrapper.logException(err)
                throw err
            }
            if (assetsArePresentInSupportDirectories(list)) {
                try {
                    if (lastDownloadedVersionIsUpToDate(repo)) {
                        continue
                    }
                } catch (err: UnknownHostException) {
                    // If assets are present but the network is unreachable, don't bother trying
                    // to find updates.
                    break
                }
            }
            val filename = "assets.tar.gz"
            val versionCode = githubApiClient.getLatestReleaseVersion(repo)
            val url = githubApiClient.getAssetEndpoint(filename, repo)
            val downloadMetadata = DownloadMetadata(filename, repo, versionCode, url)
            downloadRequirements.add(downloadMetadata)
        }
        if (filesystemNeedsExtraction) {
            val repo = filesystem.distributionType
            val filename = "rootfs.tar.gz"

            val rootFsIsDownloaded = File("$applicationFilesDirPath/$repo/$filename").exists()
            val rootFsIsUpToDate = try {
                lastDownloadedFilesystemVersionIsUpToDate(repo)
            } catch (err: UnknownHostException) {
                // Allows usage of existing rootfs files in case of failing network connectivity.
                true
            }
            if (rootFsIsDownloaded && rootFsIsUpToDate) return downloadRequirements

            // If the rootfs is not downloaded, network failures will still propagate.
            val versionCode = githubApiClient.getLatestReleaseVersion(repo)
            val url = githubApiClient.getAssetEndpoint(filename, repo)
            val downloadMetadata = DownloadMetadata(filename, repo, versionCode, url)
            downloadRequirements.add(downloadMetadata)
        }
        return downloadRequirements
    }

    fun getDistributionAssetsForExistingFilesystem(filesystem: Filesystem): List<Asset> {
        val assets = assetPreferences.getCachedAssetList(filesystem.distributionType)
        return assets.filter { !it.name.contains("rootfs") }
    }

    fun getLatestDistributionVersion(distributionType: String): String {
        return assetPreferences.getLatestDownloadVersion(distributionType)
    }

    fun assetsArePresentInSupportDirectories(assets: List<Asset>): Boolean {
        for (asset in assets) {
            if (asset.name.contains("rootfs.tar.gz")) continue
            val assetFile = File("$applicationFilesDirPath/${asset.pathName}")
            if (!assetFile.exists()) return false
        }
        return true
    }

    @Throws(UnknownHostException::class)
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

    private suspend fun fetchAssetList(assetType: String): List<Asset> = withContext(Dispatchers.IO) {
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

        return@withContext assetList
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
}