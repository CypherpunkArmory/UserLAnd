package tech.userland.userland.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

// Prefix file name with OS type to move it into the correct folder
val assetEndpoints = listOf(
    "support:proot" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/proot",
    "support:busybox" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/busybox",
    "support:libtalloc.so.2" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/libtalloc.so.2",
    "support:execInProot" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/main/execInProot",
    "debian:startDBServer.sh" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf_next/startDBServer.sh",
    "debian:busybox" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/busybox",
    "debian:libdisableselinux.so" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/libdisableselinux.so",
    "debian:ld.so.preload" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/main/ld.so.preload",
    "debian:rootfs.tar.gz" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/rootfs.tar.gz"
)

private fun download(downloadManager: DownloadManager, type: String, url: String): Long {
    //TODO notify user if wifi not available
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
    val (subdirectory, filename) = type.split(":")
    val asset = File("${context.filesDir.path}/$subdirectory/$filename")
    // TODO more sophisticated version checking
    return !asset.exists()
}

fun DownloadManager.checkAndDownloadRequirements(context: Context): List<Long> {
        return assetEndpoints
            .filter {
                (type, _) ->
                assetNeedsToUpdated(context, type)
            }
            .map {
                (type, endpoint) ->
                download(this, type, endpoint)
            }
}

