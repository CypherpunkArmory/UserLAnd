package tech.ula.utils

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Environment
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class DownloadUtility(val context: Context, val session: Session, val filesystem: Filesystem) {

    private val branch = "master"
    private val failedConnection = 0L

    private val distType = filesystem.distributionType
    private val archType = filesystem.archType

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    // TODO make this list dynamic based on a list stored in the repo or otherwise
    // Prefix file name with OS type to move it into the correct folder
    private val assetEndpoints = arrayListOf(
            "support:proot" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/$archType/proot",
            "support:busybox" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/$archType/busybox",
            "support:libtalloc.so.2" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/$archType/libtalloc.so.2",
            "support:execInProot.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/all/execInProot.sh",
            "support:killProcTree.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/all/killProcTree.sh",
            "support:isServerInProcTree.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/all/isServerInProcTree.sh",
            "$distType:startSSHServer.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/all/startSSHServer.sh",
            "$distType:startVNCServer.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/all/startVNCServer.sh",
            "$distType:startVNCServerStep2.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/all/startVNCServerStep2.sh",
            "$distType:extractFilesystem.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/all/extractFilesystem.sh",
            "$distType:busybox" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/$archType/busybox",
            "$distType:libdisableselinux.so" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/$archType/libdisableselinux.so",
            "$distType:ld.so.preload" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/all/ld.so.preload"
    )

    private val rootfsEndpoint = "$distType:rootfs.tar.gz" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/$distType/$archType/rootfs.tar.gz"

    fun checkIfLargeRequirement(): Boolean {
        if(session.isExtracted) {
            return false
        }
        if (!isWifiEnabled()) {
            return assetNeedsToUpdated(rootfsEndpoint.first, rootfsEndpoint.second, false)
        }
        return false
    }

    private fun download(type: String, url: String): Long {
        // TODO Dynamically adjust allowed network types to ensure no mobile use
        // Currently just assuming the dialog choices succeed in some way
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setDescription("Downloading $type.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "UserLAnd:$type")
        deletePreviousDownload("UserLAnd:$type")

        val updateTime = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("file_date_stamps", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putLong(type, updateTime)
            apply()
        }

        if(type.contains("rootfs.tar.gz")) filesystem.isDownloaded = true

        return downloadManager.enqueue(request)
    }

    private fun assetNeedsToUpdated(type: String, endpoint: String, updateIsBeingForced: Boolean): Boolean {
        val (subdirectory, filename) = type.split(":")
        val asset = File("${context.filesDir.path}/$subdirectory/$filename")
        val prefs = context.getSharedPreferences("file_date_stamps", Context.MODE_PRIVATE)

        // TODO: make it so we download a full group of files, if any has changed (not the rootfs though, that is special)
        // This will take care of a few possible corner cases

        if(!updateIsBeingForced) {
            val now = System.currentTimeMillis()
            val lastUpdateCheck = prefs.getLong("lastUpdateCheck", 0)
            if (!asset.exists() || now > (lastUpdateCheck + TimeUnit.DAYS.toMillis(1))) {
                with(prefs.edit()) {
                    putLong("lastUpdateCheck", now)
                    apply()
                }
            } else {
                return false
            }
        }

        val localDateStamp = prefs.getLong(type, 0)
        val remoteDateStamp = urlDateModified(endpoint)
        if ((remoteDateStamp != failedConnection) && (localDateStamp != remoteDateStamp)) {
            if (asset.exists())
                asset.delete()
        }

        return !asset.exists()
    }

    fun downloadRequirements(updateIsBeingForced: Boolean = false): List<Long> {
        if (!session.isExtracted) assetEndpoints.add(rootfsEndpoint)
        return assetEndpoints
                .filter {
                    (type, endpoint) ->
                    assetNeedsToUpdated(type, endpoint, updateIsBeingForced)
                }
                .map {
                    (type, endpoint) ->
                    download(type, endpoint)
                }
    }

    private fun isWifiEnabled(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        return if (connectivityManager is ConnectivityManager) {
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI
        } else false
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        return if (connectivityManager is ConnectivityManager) {
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected ?: false
        } else false
    }

    private fun deletePreviousDownload(type: String) {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadFile = File(downloadDirectory, type)
        if (downloadFile.exists())
            downloadFile.delete()
    }

    private fun urlDateModified(address: String): Long {
        val url = URL(address)
        val httpCon = url.openConnection() as HttpURLConnection
        return httpCon.lastModified
    }
}
