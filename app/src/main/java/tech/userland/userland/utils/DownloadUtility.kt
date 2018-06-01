package tech.userland.userland.utils

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import tech.userland.userland.R
import java.io.File
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class DownloadUtility(val uiContext: Context) {
    private val downloadManager: DownloadManager by lazy {
        uiContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val wifiManager: WifiManager by lazy {
        uiContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    companion object {
        val CONTINUE = 0
        val TURN_ON_WIFI = 1
        val CANCEL = 2
    }

    // Prefix file name with OS type to move it into the correct folder
    val assets = arrayListOf(
            "support:proot" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/proot",
            "support:busybox" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/busybox",
            "support:libtalloc.so.2" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf/libtalloc.so.2",
            "support:execInProot" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/main/execInProot"
    )

    val debianAssets = listOf(
            "debian:startDBServer.sh" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf_next/startSSHServer.sh",
            "debian:extractFilesystem.sh" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/main/extractFilesystem.sh",
            "debian:busybox" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/busybox",
            "debian:libdisableselinux.so" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/armhf/libdisableselinux.so",
            "debian:ld.so.preload" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/debianSupport/main/ld.so.preload",
            "debian:rootfs.tar.gz" to "https://s3-us-west-2.amazonaws.com/tech.userland.us.west.oregon/mainSupport/armhf_next/rootfs.tar.gz"
    )

    fun addRequirements(filesystemType: String) {
        when(filesystemType) {
            "debian" -> assets.addAll(debianAssets)
            else -> return
        }
    }

    fun checkIfLargeRequirement(): Boolean {
        if(!isWifiEnabled()) {
            assets.forEach {
                (type, _) ->
                if(type.contains("rootfs") && assetNeedsToUpdated(type)) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun displayWifiChoices(): Int {
        lateinit var result: Continuation<Int>
        val builder = AlertDialog.Builder(uiContext)
        builder.setMessage(R.string.alert_wifi_disabled_message)
                .setTitle(R.string.alert_wifi_disabled_title)
                .setPositiveButton(R.string.alert_wifi_disabled_force_button, {
                    dialog, _ ->
                    dialog.dismiss()
                    result.resume(DownloadUtility.CONTINUE)
                })
                .setNegativeButton(R.string.alert_wifi_disabled_turn_on_wifi_button, {
                    dialog, _ ->
                    dialog.dismiss()
                    result.resume(DownloadUtility.TURN_ON_WIFI)
                })
                .setNeutralButton(R.string.alert_wifi_disabled_cancel_button, {
                    dialog, _ ->
                    dialog.dismiss()
                    result.resume(DownloadUtility.CANCEL)
                })
                .create()
                .show()

        return suspendCoroutine { continuation -> result = continuation }
    }

    private fun download(type: String, url: String): Long {
        // TODO Dynamically adjust allowed network types to ensure no mobile use
        // Currently just assuming the dialog choices succeed in some way
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setAllowedOverMetered(false)
        request.setAllowedOverRoaming(false)
        request.setDescription("Downloading $type.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "UserLAnd:$type")

        return downloadManager.enqueue(request)
    }

    private fun assetNeedsToUpdated(type: String): Boolean {
        val (subdirectory, filename) = type.split(":")
        val asset = File("${uiContext.filesDir.path}/$subdirectory/$filename")
        // TODO more sophisticated version checking
        return !asset.exists()
    }


    fun downloadRequirements(): List<Long> {
        return assets
                .filter {
                    (type, _) ->
                    assetNeedsToUpdated(type)
                }
                .map {
                    (type, endpoint) ->
                    download(type, endpoint)
                }
    }

    private fun isWifiEnabled(): Boolean {
        val connectivityManager = uiContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI
    }
}

