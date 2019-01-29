package tech.ula.model.repositories

import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.utils.AssetPreferences
import tech.ula.utils.ConnectionUtility
import tech.ula.utils.TimeUtility
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.Exception

class AssetRepository(
    private val applicationFilesDirPath: String,
    private val assetPreferences: AssetPreferences,
    private val connectionUtility: ConnectionUtility = ConnectionUtility()
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

    fun getAllAssetLists(distributionType: String, deviceArchitecture: String): List<List<Asset>> {
        val allAssetListTypes = listOf(
                "support" to "all",
                "support" to deviceArchitecture,
                distributionType to "all",
                distributionType to deviceArchitecture
        )

        if (!connectionUtility.httpsHostIsReachable("github.com")) return getCachedAssetLists(allAssetListTypes)
        return retrieveAllRemoteAssetLists(allAssetListTypes)
    }

    private fun getCachedAssetLists(allAssetListTypes: List<Pair<String, String>>): List<List<Asset>> {
        return assetPreferences.getAssetLists(allAssetListTypes)
    }

    @Throws
    private fun retrieveAllRemoteAssetLists(allAssetListTypes: List<Pair<String, String>>): List<List<Asset>> {
        val allAssetLists = ArrayList<List<Asset>>()
        allAssetListTypes.forEach {
            (assetType, architectureType) ->
            val assetList = try {
                retrieveAndParseAssetList(assetType, architectureType)
            } catch (err: Exception) {
                emptyList<Asset>()
            }
            allAssetLists.add(assetList)
            assetPreferences.setAssetList(assetType, architectureType, assetList)
        }
        return allAssetLists.toList()
    }

    @Throws
    private fun retrieveAndParseAssetList(
        assetType: String,
        architectureType: String
    ): List<Asset> {
        val assetList = ArrayList<Asset>()

        var branch = "master"
        if (assetType.equals("support", true))
            branch = "staging"
        val url = "https://github.com/CypherpunkArmory/UserLAnd-Assets-" +
                "$assetType/raw/$branch/assets/$architectureType/assets.txt"

        val reader = BufferedReader(InputStreamReader(connectionUtility.getUrlInputStream(url)))

        reader.forEachLine {
            val (filename, timestampAsString) = it.split(" ")
            if (filename == "assets.txt") return@forEachLine
            val remoteTimestamp = timestampAsString.toLong()
            assetList.add(Asset(filename, assetType, architectureType, remoteTimestamp))
        }

        reader.close()
        return assetList.toList()
    }
}