package tech.ula.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import javax.net.ssl.SSLHandshakeException

class AssetListUtility(private val connectionUtility: ConnectionUtility) {

    fun retrieveAndParseAssetList(
        repo: String,
        scope: String,
        protocol: String = "https",
        retries: Int = 0
    ): ArrayList<Pair<String, Long>> {
        val assetList = ArrayList<Pair<String, Long>>()

        val url = "$protocol://github.com/CypherpunkArmory/UserLAnd-Assets-$repo/raw/master/assets/$scope/assets.txt"
        try {
            val reader = BufferedReader(InputStreamReader(connectionUtility.getUrlInputStream(url)))
            reader.forEachLine {
                val (filename, timestampAsString) = it.split(" ")
                if (filename == "assets.txt") return@forEachLine
                val timestamp = timestampAsString.toLong()
                assetList.add(filename to timestamp)
            }
            reader.close()
            return assetList
        } catch (err: SSLHandshakeException) {
            if (retries >= 5) throw object : Exception("Error getting asset list") {}
            return retrieveAndParseAssetList(repo, scope, "http", retries + 1)
        } catch (err: Exception) {
            throw object : Exception("Error getting asset list") {}
        }
    }
}