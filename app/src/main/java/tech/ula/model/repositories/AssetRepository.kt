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

data class AssetMetadata(val repoName: String, val isRootFsFile: Boolean) {
    override fun toString(): String {
        val downloadName = if (isRootFsFile) "rootfs" else "assets"
        return "$repoName-$downloadName.tar.gz"
    }
}

data class DownloadRequirements(
    var distributionType: String = "",
    var distributionAssetsRequired: Boolean = false,
    var distributionAssetsEndpoint: String = "",
    var supportAssetsRequired: Boolean = false,
    var supportAssetsEndpoint: String = "",
    var rootFsRequired: Boolean = false,
    var rootFsEndpoint: String = ""
)

sealed class RepositoryTypes
object DistributionRepository : RepositoryTypes()
object SupportRepository : RepositoryTypes()

sealed class RepositoryStatus

class AssetRepository(
    private val applicationFilesDirPath: String,
    private val assetPreferences: AssetPreferences,
    private val githubApiClient: GithubApiClient = GithubApiClient(),
    private val connectionUtility: ConnectionUtility = ConnectionUtility()
) {

    private val repositoryStatus = MutableLiveData<RepositoryStatus>()

    suspend fun lastDownloadedVersionIsUpToDate(repo: String): Boolean {
        val latestCached = assetPreferences.getLatestDownloadVersion(repo)
        val latestRemote = githubApiClient.getLatestReleaseVersion(repo)
        return latestCached >= latestRemote
    }

    suspend fun lastDownloadedFilesystemVersionIsUpToDate(repo: String): Boolean {
        val latestCached = assetPreferences.getLatestDownloadFilesystemVersion(repo)
        val latestRemote = githubApiClient.getLatestReleaseVersion(repo)
        return latestRemote > latestCached
    }

    @Throws(IllegalStateException::class)
    suspend fun generateDownloadRequirements(filesystem: Filesystem, assetLists: HashMap<String, List<Asset>>): DownloadRequirements {
        if (assetLists.size > 2) {
            throw IllegalStateException(
                    "Download generation attempted for more lists than support and distribution."
            )
        }
        val downloadRequirements = DownloadRequirements()
        for (entry in assetLists) {
            val (repo, list) = entry
            if (assetsArePresentInSupportDirectories(list) && lastDownloadedVersionIsUpToDate(repo))
                continue
            when {
                repo == "support" -> {
                    downloadRequirements.supportAssetsRequired = true
                    downloadRequirements.supportAssetsEndpoint = githubApiClient.getAssetEndpoint("assets", repo)
                }
                list.any { it.isLarge } -> {
                    if (!lastDownloadedFilesystemVersionIsUpToDate(repo)) {
                        downloadRequirements.rootFsRequired = true
                        downloadRequirements.rootFsEndpoint = githubApiClient.getAssetEndpoint("rootfs", repo)
                    }
                }
                repo == filesystem.distributionType -> {
                    downloadRequirements.distributionAssetsRequired = true
                    downloadRequirements.distributionAssetsEndpoint = githubApiClient.getAssetEndpoint("assets", repo)
                }
            }
        }
        return downloadRequirements
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