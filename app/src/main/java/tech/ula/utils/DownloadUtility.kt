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
            "support:proot" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-Core/raw/$branch/assets/$archType/proot",
            "support:busybox" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-Core/raw/$branch/assets/$archType/busybox",
            "support:libtalloc.so.2" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-Core/raw/$branch/assets/$archType/libtalloc.so.2",
            "support:execInProot.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-Core/raw/$branch/assets/all/execInProot.sh",
            "support:killProcTree.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-Core/raw/$branch/assets/all/killProcTree.sh",
            "support:isServerInProcTree.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-Core/raw/$branch/assets/all/isServerInProcTree.sh",
            "$distType:startSSHServer.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/all/startSSHServer.sh",
            "$distType:startVNCServer.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/all/startVNCServer.sh",
            "$distType:startVNCServerStep2.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/all/startVNCServerStep2.sh",
            "$distType:extractFilesystem.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/test/assets/all/extractFilesystem.sh",
            "$distType:busybox" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/busybox",
            "$distType:libdisableselinux.so" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/libdisableselinux.so",
            "$distType:ld.so.preload" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/all/ld.so.preload"
    )

    private val rootfsEndpoint = arrayListOf(
            "$distType:rootfs.tar.gz.part00" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/rootfs.tar.gz.part00",
            "$distType:rootfs.tar.gz.part01" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/rootfs.tar.gz.part01",
            "$distType:rootfs.tar.gz.part02" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/rootfs.tar.gz.part02",
            "$distType:rootfs.tar.gz.part03" to "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/rootfs.tar.gz.part03"
    )

    fun largeAssetRequiredAndNoWifi(): Boolean {
        val filesystemIsPresent = session.isExtracted || filesystem.isDownloaded
        return !(filesystemIsPresent || wifiIsEnabled())
    }

    private fun download(type: String, url: String): Long {
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

        if (type.contains("rootfs.tar.gz")) filesystem.isDownloaded = true

        return downloadManager.enqueue(request)
    }

    private fun assetNeedsToUpdated(type: String, endpoint: String, updateIsBeingForced: Boolean): Boolean {
        val (subdirectory, filename) = type.split(":")
        val asset = File("${context.filesDir.path}/$subdirectory/$filename")
        val prefs = context.getSharedPreferences("file_date_stamps", Context.MODE_PRIVATE)

        // TODO: make it so we download a full group of files, if any has changed (not the rootfs though, that is special)
        // This will take care of a few possible corner cases

        if (!updateIsBeingForced) {
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
        val remoteDateStamp: Long = try {
            getLastModifiedDateForRemoteFile(endpoint)
        } catch (err: Exception) {
            0
        }
        if ((remoteDateStamp != failedConnection) && (localDateStamp != remoteDateStamp)) {
            if (asset.exists())
                asset.delete()
        }

        return !asset.exists()
    }

    fun downloadRequirements(updateIsBeingForced: Boolean = false): List<Long> {
        if (!session.isExtracted) assetEndpoints.addAll(rootfsEndpoint)
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

    private fun wifiIsEnabled(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        return if (connectivityManager is ConnectivityManager) {
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI
        } else false
    }

    fun networkIsEnabled(): Boolean {
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

    @Throws(Exception::class)
    private fun getLastModifiedDateForRemoteFile(address: String): Long {
        val repo = "UserLAnd-Assets-" +
                if (address.contains(distType)) distType
                else "Core"
        val apiEndpoint = "https://api.github.com/repos/CypherpunkArmory/$repo/commits" +
                "?path=${address.substringAfter(branch)}"
        val conn = URL(apiEndpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        return conn.lastModified
    }
}
