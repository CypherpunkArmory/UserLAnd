package tech.ula.utils

import android.app.DownloadManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class DownloadUtility(
    private val session: Session,
    private val filesystem: Filesystem,
    private val downloadManager: DownloadManager,
    private val timestampPreferenceUtility: TimestampPreferenceUtility,
    private val applicationFilesDirPath: String,
    private val connectivityManager: ConnectivityManager,
    private val connectionUtility: ConnectionUtility,
    private val requestUtility: RequestUtility,
    private val environmentUtility: EnvironmentUtility
) {

    private val branch = "master"

    private val distType = filesystem.distributionType
    private val archType = filesystem.archType

    private val allAssetListTypes = listOf(
            "support" to "all",
            "support" to archType,
            distType to "all",
            distType to archType
    )

    private val lastUpdateCheck: Long by lazy {
        // only grab the value from the database the first time such that we won't be looking at the value that is being
        // updated while we check each file
        timestampPreferenceUtility.getLastUpdateCheck()
    }

    fun largeAssetRequiredAndNoWifi(): Boolean {
        val filesystemIsPresent = session.isExtracted || filesystem.isDownloaded
        return !(filesystemIsPresent || wifiIsEnabled())
    }

    private fun download(filename: String, repo: String, scope: String): Long {
        val url = "https://github.com/CypherpunkArmory/UserLAnd-Assets-$repo/raw/$branch/assets/$scope/$filename"
        val destination = "UserLAnd:$repo:$filename"
        val request = requestUtility.generateTypicalDownloadRequest(url, destination)
        deletePreviousDownload("UserLAnd:$repo:$filename")

        val updateTime = currentTimeSeconds()
        val timestampPrefName = "$repo:$filename"
        timestampPreferenceUtility.setSavedTimestampForFile(timestampPrefName, updateTime)

        if (filename.contains("rootfs.tar.gz")) filesystem.isDownloaded = true

        return downloadManager.enqueue(request)
    }

    private fun assetNeedsToUpdated(
        filename: String,
        remoteTimestamp: Long,
        repo: String,
        updateIsBeingForced: Boolean
    ): Boolean {
        val asset = File("$applicationFilesDirPath/$repo/$filename")

        if (filename.contains("rootfs.tar.gz") && session.isExtracted) return false

        val now = currentTimeSeconds()
        if (updateIsBeingForced ||
                !asset.exists() ||
                !session.isExtracted ||
                now > (lastUpdateCheck + TimeUnit.DAYS.toSeconds(1))) {
            timestampPreferenceUtility.setLastUpdateCheck(now)
        } else {
            return false
        }

        val timestampPrefName = "$repo:$filename"
        val localTimestamp = timestampPreferenceUtility.getSavedTimestampForFile(timestampPrefName)
        if (localTimestamp < remoteTimestamp) {
            if (asset.exists())
                asset.delete()
        }

        return !asset.exists()
    }

    fun downloadRequirements(updateIsBeingForced: Boolean = false, assetListTypes: List<Pair<String, String>> = allAssetListTypes): ArrayList<Long> {
        val downloads = ArrayList<Long>()

        assetListTypes.forEach {
            (repo, scope) ->
            val assetList = retrieveAndParseAssetList(repo, scope)
            assetList.forEach {
                (type, timestamp) ->
                if (assetNeedsToUpdated(type, timestamp, repo, updateIsBeingForced)) {
                    downloads.add(download(type, repo, scope))
                }
            }
        }
        return downloads
    }

    private fun wifiIsEnabled(): Boolean {
        for (network in connectivityManager.allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
        }
        return false
    }

    fun internetIsAccessible(): Boolean {
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        activeNetworkInfo?.let {
            return true
        }
        return false
    }

    private fun deletePreviousDownload(type: String) {
        val downloadDirectory = environmentUtility.getDownloadsDirectory()
        val downloadFile = File(downloadDirectory, type)
        if (downloadFile.exists())
            downloadFile.delete()
    }

    private fun retrieveAndParseAssetList(
        repo: String,
        scope: String,
        protocol: String = "https",
        retries: Int = 0
    ): ArrayList<Pair<String, Long>> {
        val assetList = ArrayList<Pair<String, Long>>()

        if (!internetIsAccessible()) {
            return assetList
        }

        val url = "$protocol://github.com/CypherpunkArmory/UserLAnd-Assets-$repo/raw/$branch/assets/$scope/assets.txt"
        try {
            val reader = BufferedReader(InputStreamReader(connectionUtility.getAssetListConnection(url)))
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
