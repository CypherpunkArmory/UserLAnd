package tech.ula.utils

import android.app.DownloadManager
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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
        return prefs.getString("pref_proot_debug_level", "-1")
    }

    override fun getProotDebugLogLocation(): String {
        return prefs.getString("pref_proot_debug_log_location",
                "${Environment.getExternalStorageDirectory().path}/PRoot_Debug_Log")
    }
}

interface TimestampPreferenceAccessor {
    fun getSavedTimestampForFile(filename: String): Long

    fun setSavedTimestampForFile(filename: String, timestamp: Long)

    fun getLastUpdateCheck(): Long

    fun setLastUpdateCheck(timestamp: Long)
}

class TimestampPreferenceUtility(private val prefs: SharedPreferences) : TimestampPreferenceAccessor {
    override fun getSavedTimestampForFile(filename: String): Long {
        return prefs.getLong(filename, 0)
    }

    override fun setSavedTimestampForFile(filename: String, timestamp: Long) {
        with(prefs.edit()) {
            putLong(filename, timestamp)
            apply()
        }
    }

    override fun getLastUpdateCheck(): Long {
        return prefs.getLong("lastUpdateCheck", 0)
    }

    override fun setLastUpdateCheck(timestamp: Long) {
        with(prefs.edit()) {
            putLong("lastUpdateCheck", timestamp)
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
}

interface ConnectionAccessor {
    fun getAssetListConnection(url: String): InputStream
}

class ConnectionUtility : ConnectionAccessor {
    override fun getAssetListConnection(url: String): InputStream {
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