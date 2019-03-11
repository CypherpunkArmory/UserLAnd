package tech.ula.model.repositories

import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.remote.AssetListFetcher
import tech.ula.model.remote.GithubReleaseAssetListFetcher
import tech.ula.utils.AssetPreferences
import java.io.File
import kotlin.Exception

class AssetRepository(
    private val applicationFilesDirPath: String,
    private val assetPreferences: AssetPreferences,
    private val assetListFetcher: AssetListFetcher = GithubReleaseAssetListFetcher()
) {

    fun doesAssetNeedToUpdated(asset: Asset): Boolean {
        val assetFile = File("$applicationFilesDirPath/${asset.pathName}")

        if (!assetFile.exists()) return true

        val localTimestamp = assetPreferences.getLastUpdatedTimestampForAsset(asset)
        return localTimestamp < asset.remoteTimestamp
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

    fun getLastDistributionUpdate(distributionType: String): Long {
        return assetPreferences.getLastDistributionUpdate(distributionType)
    }

    fun setLastDistributionUpdate(distributionType: String) {
        assetPreferences.setLastDistributionUpdate(distributionType)
    }

    fun assetsArePresentInSupportDirectories(assets: List<Asset>): Boolean {
        for (asset in assets) {
            val assetFile = File("$applicationFilesDirPath/${asset.pathName}")
            if (!assetFile.exists()) return false
        }
        return true
    }

    suspend fun getAllAssetLists(distributionType: String): List<List<Asset>> {
        return listOf(distributionType, "support").map { repo ->
            try {
                val list = assetListFetcher.fetchAssetList(repo)
                assetPreferences.setAssetList(repo, "", list) // TODO arch type
                list
            } catch (err: Exception) {
                assetPreferences.getCachedAssetList(repo)
            }
        }
    }
}