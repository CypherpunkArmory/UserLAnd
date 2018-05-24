package tech.userland.userland.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

val assetEndpoints = listOf(
    "proot" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/proot",
    "busybox" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/busybox",
    "libtalloc.so.2" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/libtalloc.so.2",
    "execInProot" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/main/execInProot",
    "startDBServer.sh" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/main/startDBServer.sh",
    "busybox" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/busybox",
    "libdisableselinux.so" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/libdisableselinux.so",
    "ld.so.preload" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/main/ld.so.preload",
    "rootfs.tar.gz" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/rootfs.tar.gz"
)

private fun download(downloadManager: DownloadManager, type: String, url: String): Long {
    val uri = Uri.parse(url)
    val request = DownloadManager.Request(uri)
    request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
    request.setAllowedOverMetered(false)
    request.setAllowedOverRoaming(false)
    request.setDescription("Downloading $type.")
    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "UserLAnd:$type")

    return downloadManager.enqueue(request)
}

private fun assetNeedsToUpdated(context: Context, type: String): Boolean {
    val appFilesDirectoryPath = context.filesDir.path
    val asset = File("$appFilesDirectoryPath/support/$type")
    // TODO more sophisticated version checking
    return !asset.exists()
}

fun DownloadManager.checkAndDownloadRequirements(context: Context): List<Long> {
        return assetEndpoints
            .filter {
                val (type, _) = it
                assetNeedsToUpdated(context, type)
            }
            .map {
                val (type, endpoint) = it
                download(this, type, endpoint)
            }
}

fun moveDownloadedAssetsToSupportDirectory(context: Context) {
    val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val appFilesDirectoryPath = context.filesDir.path
    downloadDirectory.walkBottomUp().
            filter { it.name.contains("UserLAnd:") }
            .forEach {
                val type = it.name.substringAfterLast(":")
                val targetDestination = File("$appFilesDirectoryPath/support/$type")
                it.copyTo(targetDestination)
                it.delete()
            }
}