package tech.ula.utils

import android.app.DownloadManager
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class DownloadUtility(
    val session: Session,
    val filesystem: Filesystem,
    val downloadManager: DownloadManager,
    val sharedPreferences: SharedPreferences,
    val applicationFilesDirPath: String,
    val connectivityManager: ConnectivityManager
) {x

    private val branch = "master"

    private val distType = filesystem.distributionType
    private val archType = filesystem.archType

    private val lastUpdateCheck: Long by lazy {
        // only grab the value from the database the first time such that we won't be looking at the value that is being
        // updated while we check each file
        sharedPreferences.getLong("lastUpdateCheck", 0)
    }

    fun largeAssetRequiredAndNoWifi(): Boolean {
        val filesystemIsPresent = session.isExtracted || filesystem.isDownloaded
        return !(filesystemIsPresent || wifiIsEnabled())
    }

    private fun download(filename: String, repo: String, scope: String): Long {
        val url = "https://github.com/CypherpunkArmory/UserLAnd-Assets-$repo/raw/$branch/assets/$scope/$filename"
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setDescription("Downloading $filename.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "UserLAnd:$repo:$filename")
        deletePreviousDownload("UserLAnd:$filename")

        val updateTime = currentTimeSeconds()
        with(sharedPreferences.edit()) {
            val timestampPrefName = "$repo:$filename"
            putLong(timestampPrefName, updateTime)
            apply()
        }

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
            with(sharedPreferences.edit()) {
                putLong("lastUpdateCheck", now)
                apply()
            }
        } else {
            return false
        }

        val timestampPrefName = "$repo:$filename"
        val localTimestamp = sharedPreferences.getLong(timestampPrefName, 0)
        if (localTimestamp < remoteTimestamp) {
            if (asset.exists())
                asset.delete()
        }

        return !asset.exists()
    }

    fun downloadRequirements(updateIsBeingForced: Boolean = false): ArrayList<Long> {
        val downloads = ArrayList<Long>()
        val assetListTypes = listOf(
                "support" to "all",
                "support" to archType,
                distType to "all",
                distType to archType
        )

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
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadFile = File(downloadDirectory, type)
        if (downloadFile.exists())
            downloadFile.delete()
    }

    @Throws(Exception::class)
    private fun retrieveAndParseAssetList(repo: String, scope: String): ArrayList<Pair<String, Long>> {
        val assetList = ArrayList<Pair<String, Long>>()

        if (!internetIsAccessible()) {
            return assetList
        }

        val url = "https://github.com/CypherpunkArmory/UserLAnd-Assets-$repo/raw/$branch/assets/$scope/assets.txt"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        reader.forEachLine {
            val (filename, timestampAsString) = it.split(" ")
            if (filename == "assets.txt") return@forEachLine
            val timestamp = timestampAsString.toLong()
            assetList.add(filename to timestamp)
        }
        reader.close()
        return assetList
    }
}
