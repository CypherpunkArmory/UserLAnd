package tech.ula.model.remote

import tech.ula.model.entities.Asset
import tech.ula.utils.ConnectionUtility
import java.io.BufferedReader
import java.io.InputStreamReader

interface AssetListFetcher {
    suspend fun fetchAssetList(assetType: String): List<Asset>
}

class GithubReleaseAssetListFetcher(
        private val client: GithubApiClient = GithubApiClient(),
        private val connectionUtility: ConnectionUtility = ConnectionUtility()
) : AssetListFetcher {
    @Throws()
    override suspend fun fetchAssetList(assetType: String): List<Asset> {
        val downloadUrl = client.getAssetsListDownloadUrl(assetType)

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