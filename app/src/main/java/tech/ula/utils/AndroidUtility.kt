package tech.ula.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import tech.ula.model.entities.Asset
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

fun makePermissionsUsable(containingDirectoryPath: String, filename: String) {
    val commandToRun = arrayListOf("chmod", "0777", filename)

    val pb = ProcessBuilder(commandToRun)
    pb.directory(File(containingDirectoryPath))

    val process = pb.start()
    process.waitFor()
}

interface DefaultPreferencesAccessor {
    fun getProotDebuggingEnabled(): Boolean

    fun getProotDebuggingLevel(): String

    fun getProotDebugLogLocation(): String
}

class DefaultPreferenceUtility(private val prefs: SharedPreferences) : DefaultPreferencesAccessor {

    override fun getProotDebuggingEnabled(): Boolean {
        return prefs.getBoolean("pref_proot_debug_enabled", false)
    }

    override fun getProotDebuggingLevel(): String {
        return prefs.getString("pref_proot_debug_level", "-1") ?: ""
    }

    override fun getProotDebugLogLocation(): String {
        return prefs.getString("pref_proot_debug_log_location", "${Environment.getExternalStorageDirectory().path}/PRoot_Debug_Log") ?: ""
    }
}

interface TimestampPreferenceAccessor {
    fun getSavedTimestampForFile(filename: String): Long

    fun setSavedTimestampForFileToNow(filename: String)

    fun getLastUpdateCheck(): Long

    fun setLastUpdateCheckToNow()
}

class TimestampPreferenceUtility(private val prefs: SharedPreferences) : TimestampPreferenceAccessor {
    override fun getSavedTimestampForFile(filename: String): Long {
        return prefs.getLong(filename, 0)
    }

    override fun setSavedTimestampForFileToNow(filename: String) {
        with(prefs.edit()) {
            putLong(filename, currentTimeSeconds())
            apply()
        }
    }

    override fun getLastUpdateCheck(): Long {
        return prefs.getLong("lastUpdateCheck", 0)
    }

    override fun setLastUpdateCheckToNow() {
        with(prefs.edit()) {
            putLong("lastUpdateCheck", currentTimeSeconds())
            apply()
        }
    }
}

interface AssetListPreferenceAccessor {
    fun getAssetLists(allAssetListTypes: List<Pair<String, String>>): List<List<Asset>>

    fun setAssetList(assetType: String, architectureType: String, assetList: List<Asset>)
}

class AssetListPreferenceUtility(private val prefs: SharedPreferences) : AssetListPreferenceAccessor {
    override fun getAssetLists(allAssetListTypes: List<Pair<String, String>>): List<List<Asset>> {
        val assetLists = ArrayList<List<Asset>>()
        allAssetListTypes.forEach {
            (assetType, architectureType) ->
            val allEntries = prefs.getStringSet("$assetType:$architectureType", setOf()) ?: setOf()
            val assetList: List<Asset> = allEntries.map {
                val (filename, remoteTimestamp) = it.split(":")
                Asset(filename, assetType, architectureType, remoteTimestamp.toLong())
            }
            assetLists.add(assetList)
        }
        return assetLists
    }

    override fun setAssetList(assetType: String, architectureType: String, assetList: List<Asset>) {
        val entries = assetList.map {
            "${it.name}:${it.remoteTimestamp}"
        }.toSet()
        with(prefs.edit()) {
            putStringSet("$assetType:$architectureType", entries)
            apply()
        }
    }
}

interface BuildAccessor {
    fun getSupportedAbis(): Array<String>
}

class BuildUtility : BuildAccessor {
    override fun getSupportedAbis(): Array<String> {
        return Build.SUPPORTED_ABIS
    }

    fun getArchType(): String {
        val supportedABIS = this.getSupportedAbis()
                .map {
                    translateABI(it)
                }
                .filter {
                    isSupported(it)
                }
        if (supportedABIS.size == 1 && supportedABIS[0] == "") {
            throw Exception("No supported ABI!")
        } else {
            return supportedABIS[0]
        }
    }

    private fun isSupported(abi: String): Boolean {
        val supportedABIs = listOf("arm64", "arm", "x86_64", "x86")
        return supportedABIs.contains(abi)
    }

    private fun translateABI(abi: String): String {
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> ""
        }
    }
}

interface ConnectionAccessor {
    fun getUrlConnection(url: String): HttpURLConnection

    fun getUrlInputStream(url: String): InputStream
}

class ConnectionUtility : ConnectionAccessor {

    @Throws(Exception::class)
    override fun getUrlConnection(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }

    @Throws(Exception::class)
    override fun getUrlInputStream(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        return conn.inputStream
    }
}

interface RequestGenerator {
    fun generateTypicalDownloadRequest(url: String, destination: String): DownloadManager.Request
}

class RequestUtility : RequestGenerator {
    override fun generateTypicalDownloadRequest(url: String, destination: String): DownloadManager.Request {
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setDescription("Downloading ${destination.substringAfterLast(":")}.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destination)
        return request
    }
}

interface EnvironmentAccessor {
    fun getDownloadsDirectory(): File
}

class EnvironmentUtility : EnvironmentAccessor {
    override fun getDownloadsDirectory(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}

interface ResourcesAccessor {
    fun getAppResources(): Resources
}

class ResourcesUtility(private val context: Context) : ResourcesAccessor {
    override fun getAppResources(): Resources {
        return context.resources
    }
}

class DownloadBroadcastReceiver : BroadcastReceiver() {
    private lateinit var doOnReceived: (Long) -> Unit

    fun setDoOnReceived(action: (Long) -> Unit) {
        doOnReceived = action
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val downloadedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        downloadedId?.let {
            if (it == -1L) return@let
            doOnReceived(it)
        }
    }
}