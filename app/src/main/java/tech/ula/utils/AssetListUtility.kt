package tech.ula.utils

import tech.ula.model.entities.Asset
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.net.ssl.SSLHandshakeException

class AssetListUtility(
    private val deviceArchitecture: String,
    private val distributionType: String,
    private val assetListPreferenceUtility: AssetListPreferenceAccessor,
    private val connectionUtility: ConnectionUtility
) {

    private val allAssetListTypes = listOf(
            "support" to "all",
            "support" to deviceArchitecture,
            distributionType to "all",
            distributionType to deviceArchitecture
    )

    fun getCachedAssetLists(): List<List<Asset>> {
        return assetListPreferenceUtility.getAssetLists(distributionType)
    }

    fun retrieveAllRemoteAssetLists(httpsIsAccessible: Boolean): List<List<Asset>> {
        val allAssetLists = ArrayList<List<Asset>>()
        allAssetListTypes.forEach {
            (assetType, location) ->
            val assetList = retrieveAndParseAssetList(assetType, httpsIsAccessible)
            allAssetLists.add(assetList)
            assetListPreferenceUtility.setAssetList(assetType, assetList)
        }
        return allAssetLists.toList()
    }

    private fun retrieveAndParseAssetList(
        assetType: String,
        httpsIsAccessible: Boolean,
        retries: Int = 0
    ): List<Asset> {
        val assetList = ArrayList<Asset>()
        val protocol = if (httpsIsAccessible) "https" else "http"

        val url = "$protocol://github.com/CypherpunkArmory/UserLAnd-Assets-" +
                "$distributionType/raw/master/assets/$deviceArchitecture/assets.txt"
        try {
            val reader = BufferedReader(InputStreamReader(connectionUtility.getUrlInputStream(url)))

            reader.forEachLine {
                val (filename, timestampAsString) = it.split(" ")
                if (filename == "assets.txt") return@forEachLine
                val remoteTimestamp = timestampAsString.toLong()
                assetList.add(Asset(filename, distributionType, remoteTimestamp))
            }

            reader.close()
            return assetList.toList()
        } catch (err: SSLHandshakeException) {
            if (retries >= 5) throw object : Exception("Error getting asset list") {}
            return retrieveAndParseAssetList(assetType, httpsIsAccessible, retries + 1)
        } catch (err: Exception) {
            throw object : Exception("Error getting asset list") {}
        }
    }
}