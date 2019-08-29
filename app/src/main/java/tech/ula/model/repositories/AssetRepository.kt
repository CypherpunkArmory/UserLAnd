package tech.ula.model.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.remote.GithubApiClient
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import tech.ula.utils.preferences.AssetPreferences
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
    private val ulaFiles: UlaFiles,
    private val githubApiClient: GithubApiClient = GithubApiClient(ulaFiles),
    private val httpStream: HttpStream = HttpStream(),
    private val logger: Logger = SentryLogger()
) {

    @Throws(IllegalStateException::class, UnknownHostException::class)
    suspend fun generateDownloadRequirements(
        filesystem: Filesystem,
        assetList: List<Asset>,
        filesystemNeedsExtraction: Boolean
    ): List<DownloadMetadata> {
        val downloadRequirements = mutableListOf<DownloadMetadata>()
        // Empty lists should not have propagated this deeply.
        if (assetList.isEmpty()) {
            val err = IllegalStateException()
            logger.addExceptionBreadcrumb(err)
            throw err
        }

        val repo = filesystem.distributionType
        downloadRequirements.addAll(getRegularAssetDownloadRequirements(assetList, repo))
        if (filesystemNeedsExtraction) {
            downloadRequirements.addAll(getRootFsAssetDownloadRequirements(repo))
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
    suspend fun getAssetList(distributionType: String): List<Asset> {
        return try {
            val list = fetchAssetList(distributionType)
            assetPreferences.setAssetList(distributionType, list)
            list
        } catch (err: Exception) {
            assetPreferences.getCachedAssetList(distributionType)
        }
    }

    private suspend fun fetchAssetList(assetType: String): List<Asset> = withContext(Dispatchers.IO) {
        val downloadUrl = githubApiClient.getAssetsListDownloadUrl(assetType)

        val inputStream = httpStream.fromUrl(downloadUrl)
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

    private suspend fun getRegularAssetDownloadRequirements(
        assetList: List<Asset>,
        repo: String
    ): List<DownloadMetadata> {
        val downloadRequirements = mutableListOf<DownloadMetadata>()
        if (assetsArePresentInSupportDirectories(assetList)) {
            try {
                if (lastDownloadedVersionIsUpToDate(repo)) {
                    return downloadRequirements
                }
            } catch (err: UnknownHostException) {
                // If assets are present but the network is unreachable, don't bother trying
                // to find updates.
                return downloadRequirements
            }
        }

        val filename = "assets.tar.gz"
        val versionCode = githubApiClient.getLatestReleaseVersion(repo)
        val url = githubApiClient.getAssetEndpoint(filename, repo)
        val downloadMetadata = DownloadMetadata(filename, repo, versionCode, url)
        downloadRequirements.add(downloadMetadata)
        return downloadRequirements
    }

    private suspend fun getRootFsAssetDownloadRequirements(repo: String): List<DownloadMetadata> {
        val downloadRequirements = mutableListOf<DownloadMetadata>()
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
        return listOf(downloadMetadata)
    }
}