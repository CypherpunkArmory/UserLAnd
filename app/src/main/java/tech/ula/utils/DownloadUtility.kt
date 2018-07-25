package tech.ula.utils

import android.app.DownloadManager
import android.content.Context
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

class DownloadUtility(val context: Context, val session: Session, val filesystem: Filesystem) {

    private val branch = "master"

    private val distType = filesystem.distributionType
    private val archType = filesystem.archType

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
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
        val prefs = context.getSharedPreferences("file_timestamps", Context.MODE_PRIVATE)
        with(prefs.edit()) {
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
        val asset = File("${context.filesDir.path}/$repo/$filename")
        val prefs = context.getSharedPreferences("file_timestamps", Context.MODE_PRIVATE)

        if (filename.contains("rootfs.tar.gz") && session.isExtracted) return false

        // TODO make it so we download a full group of files, if any has changed (not the rootfs though, that is special)
        // TODO this will take care of a few possible corner cases

        val now = currentTimeSeconds()
        val lastUpdateCheck = prefs.getLong("lastUpdateCheck", 0)
        if (updateIsBeingForced ||
                !asset.exists() ||
                filename.contains("rootfs.tar.gz") ||
                now > (lastUpdateCheck + TimeUnit.DAYS.toMillis(1))) {
            with(prefs.edit()) {
                putLong("lastUpdateCheck", now)
                apply()
            }
        } else {
            return false
        }

        val timestampPrefName = "$repo:$filename"
        val localTimestamp = prefs.getLong(timestampPrefName, 0)
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
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in connectivityManager.allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
        }
        return false
    }

    fun internetIsAccessible(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
