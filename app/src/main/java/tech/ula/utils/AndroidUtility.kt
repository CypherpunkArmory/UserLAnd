package tech.ula.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.support.v4.content.ContextCompat
import com.crashlytics.android.Crashlytics
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

fun makePermissionsUsable(containingDirectoryPath: String, filename: String) {
    val commandToRun = arrayListOf("chmod", "0777", filename)

    val containingDirectory = File(containingDirectoryPath)
    containingDirectory.mkdirs()

    val pb = ProcessBuilder(commandToRun)
    pb.directory(containingDirectory)

    val process = pb.start()
    process.waitFor()
}

fun arePermissionsGranted(context: Context): Boolean {
    return (ContextCompat.checkSelfPermission(context,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

            ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
}

fun displayGenericErrorDialog(activity: Activity, titleId: Int, messageId: Int) {
    AlertDialog.Builder(activity)
            .setTitle(titleId)
            .setMessage(messageId)
            .setPositiveButton(R.string.button_ok) {
                dialog, _ ->
                dialog.dismiss()
            }
            .create().show()
}

class DefaultPreferences(private val prefs: SharedPreferences) {

    fun getProotDebuggingEnabled(): Boolean {
        return prefs.getBoolean("pref_proot_debug_enabled", false)
    }

    fun getProotDebuggingLevel(): String {
        return prefs.getString("pref_proot_debug_level", "-1") ?: ""
    }

    fun getProotDebugLogLocation(): String {
        return prefs.getString("pref_proot_debug_log_location", "${Environment.getExternalStorageDirectory().path}/PRoot_Debug_Log") ?: ""
    }
}

class TimestampPreferences(private val prefs: SharedPreferences) {
    fun getSavedTimestampForFile(assetConcatenatedName: String): Long {
        return prefs.getLong(assetConcatenatedName, 0)
    }

    fun setSavedTimestampForFileToNow(assetConcatenatedName: String) {
        with(prefs.edit()) {
            putLong(assetConcatenatedName, currentTimeSeconds())
            apply()
        }
    }
}

class AssetPreferences(private val prefs: SharedPreferences) {
    private fun String.addTimestampPrefix(): String {
        return "timestamp-" + this
    }

    fun getLastUpdatedTimestampForAsset(asset: Asset): Long {
        return prefs.getLong(asset.concatenatedName.addTimestampPrefix(), -1)
    }

    fun setLastUpdatedTimestampForAsset(asset: Asset, currentTimeMillis: Long) {
        with(prefs.edit()) {
            putLong(asset.concatenatedName.addTimestampPrefix(), currentTimeMillis)
            apply()
        }
    }

    private val downloadsAreInProgressKey = "downloadsAreInProgress"
    fun getDownloadsAreInProgress(): Boolean {
        return prefs.getBoolean(downloadsAreInProgressKey, false)
    }

    fun setDownloadsAreInProgress(inProgress: Boolean) {
        with(prefs.edit()) {
            putBoolean(downloadsAreInProgressKey, true)
            apply()
        }
    }

    private val enqueuedDownloadsKey = "currentlyEnqueuedDownloads"
    fun getEnqueuedDownloads(): List<Long> {
        val enqueuedDownloadsAsStrings = prefs.getStringSet(enqueuedDownloadsKey, setOf()) ?: setOf<String>()
        return enqueuedDownloadsAsStrings.map { it.toLong() }.toList()
    }

    fun setEnqueuedDownloads(downloads: List<Long>) {
        val enqueuedDownloadsAsStrings = downloads.map { it.toString() }.toSet()
        with(prefs.edit()) {
            putStringSet(enqueuedDownloadsKey, enqueuedDownloadsAsStrings)
            apply()
        }
    }

    fun clearEnqueuedDownloadsCache() {
        with(prefs.edit()) {
            putStringSet(enqueuedDownloadsKey, setOf())
            apply()
        }
    }

    fun getAssetLists(allAssetListTypes: List<Pair<String, String>>): List<List<Asset>> {
        val assetLists = ArrayList<List<Asset>>()
        allAssetListTypes.forEach {
            (assetType, architectureType) ->
            val allEntries = prefs.getStringSet("$assetType-$architectureType", setOf()) ?: setOf()
            val assetList: List<Asset> = allEntries.map {
                val (filename, remoteTimestamp) = it.split(regex = "\\-(?=[^.]*\$)".toRegex(), limit = 2)
                Asset(filename, assetType, architectureType, remoteTimestamp.toLong())
            }
            assetLists.add(assetList)
        }
        return assetLists
    }

    fun setAssetList(assetType: String, architectureType: String, assetList: List<Asset>) {
        val entries = assetList.map {
            "${it.name}-${it.remoteTimestamp}"
        }.toSet()
        with(prefs.edit()) {
            putStringSet("$assetType-$architectureType", entries)
            apply()
        }
    }

    fun getLastDistributionUpdate(distributionType: String): Long {
        return prefs.getLong("$distributionType-lastUpdate", -1)
    }

    fun setLastDistributionUpdate(distributionType: String) {
        with(prefs.edit()) {
            putLong("$distributionType-lastUpdate", System.currentTimeMillis())
            apply()
        }
    }
}

sealed class AppServiceTypePreference
object PreferenceHasNotBeenSelected : AppServiceTypePreference() {
    override fun toString(): String {
        return "unselected"
    }
}
object SshTypePreference : AppServiceTypePreference() {
    override fun toString(): String {
        return "ssh"
    }
}
object VncTypePreference : AppServiceTypePreference() {
    override fun toString(): String {
        return "vnc"
    }
}

object XsdlTypePreference : AppServiceTypePreference() {
    override fun toString(): String {
        return "xsdl"
    }
}

class AppsPreferences(private val prefs: SharedPreferences) {

    fun setAppServiceTypePreference(appName: String, serviceType: AppServiceTypePreference) {
        val prefAsString = when (serviceType) {
            is SshTypePreference -> "ssh"
            is VncTypePreference -> "vnc"
            is XsdlTypePreference -> "xsdl"
            else -> "unselected"
        }
        with(prefs.edit()) {
            putString(appName, prefAsString)
            apply()
        }
    }

    fun getAppServiceTypePreference(app: App): AppServiceTypePreference {
        val pref = prefs.getString(app.name, "") ?: ""

        return when {
            pref.toLowerCase() == "ssh" || (app.supportsCli && !app.supportsGui) -> SshTypePreference
            pref.toLowerCase() == "xsdl" -> XsdlTypePreference
            pref.toLowerCase() == "vnc" || (!app.supportsCli && app.supportsGui) -> VncTypePreference
            else -> PreferenceHasNotBeenSelected
        }
    }

    fun setDistributionsList(distributionList: Set<String>) {
        with(prefs.edit()) {
            putStringSet("distributionsList", distributionList)
            apply()
        }
    }

    fun getDistributionsList(): Set<String> {
        return prefs.getStringSet("distributionsList", setOf()) ?: setOf()
    }
}

class BuildWrapper {
    private fun getSupportedAbis(): Array<String> {
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

class ConnectionUtility {
    fun httpsHostIsReachable(hostname: String): Boolean {
        return try {
            val sockaddr = InetSocketAddress(hostname, 443)
            val sock = Socket()
            val timeout = 2000

            sock.connect(sockaddr, timeout)
            true
        } catch (err: IOException) {
            false
        }
    }

    @Throws(Exception::class)
    fun getUrlInputStream(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        return conn.inputStream
    }
}

class DownloadManagerWrapper(private val downloadManager: DownloadManager) {
    fun generateDownloadRequest(url: String, destination: String): DownloadManager.Request {
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle(destination)
        request.setDescription("Downloading ${destination.substringAfterLast("-")}.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destination)
        return request
    }

    fun enqueue(request: DownloadManager.Request): Long {
        return downloadManager.enqueue(request)
    }

    private fun generateQuery(id: Long): DownloadManager.Query {
        val query = DownloadManager.Query()
        query.setFilterById(id)
        return query
    }

    private fun generateCursor(query: DownloadManager.Query): Cursor {
        return downloadManager.query(query)
    }

    fun getDownloadTitle(id: Long): String {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            return cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
        }
        return ""
    }

    fun downloadHasNotFailed(id: Long): Boolean {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            return status != DownloadManager.STATUS_FAILED
        }
        return false
    }

    fun getDownloadFailureReason(id: Long): String {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status: String = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
            if (status != "reason") {
                return status
            }
        }
        return "No reason for failure"
    }

    fun getDownloadsDirectory(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}

class LocalFileLocator(private val applicationFilesDir: String, private val resources: Resources) {
    fun findIconUri(type: String): Uri {
        val icon =
                File("$applicationFilesDir/apps/$type/$type.png")
        if (icon.exists()) return Uri.fromFile(icon)
        return getDefaultIconUri()
    }

    private fun getDefaultIconUri(): Uri {
        val resId = R.mipmap.ic_launcher_foreground
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + resources.getResourcePackageName(resId) + '/' +
                resources.getResourceTypeName(resId) + '/' +
                resources.getResourceEntryName(resId))
    }

    fun findAppDescription(appName: String): String {
        val appDescriptionFile =
                File("$applicationFilesDir/apps/$appName/$appName.txt")
        if (!appDescriptionFile.exists()) {
            return resources.getString(R.string.error_app_description_not_found)
        }
        return appDescriptionFile.readText()
    }
}

class TimeUtility {
    fun getCurrentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}

class CrashlyticsWrapper {
    fun setString(key: String, value: String) {
        Crashlytics.setString(key, value)
    }
}