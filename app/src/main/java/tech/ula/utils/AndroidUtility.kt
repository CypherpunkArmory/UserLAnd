package tech.ula.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.system.Os
import androidx.core.content.ContextCompat
import android.util.DisplayMetrics
import android.view.WindowManager
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.Asset
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
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

fun displayGenericErrorDialog(activity: Activity, titleId: Int, messageId: Int, callback: (() -> Unit) = {}) {
    AlertDialog.Builder(activity)
            .setTitle(titleId)
            .setMessage(messageId)
            .setPositiveButton(R.string.button_ok) {
                dialog, _ ->
                callback()
                dialog.dismiss()
            }
            .create().show()
}

// Add or change asset types as needed for testing and staggered releases.
fun getBranchToDownloadAssetsFrom(assetType: String): String {
    return when (assetType) {
        "support" -> "staging"
        "apps" -> "master"
        else -> "master"
    }
}

interface Localization {
    fun getString(context: Context): String
}

data class LocalizationData(val resId: Int, val formatStrings: List<String> = listOf()) : Localization {
    override fun getString(context: Context): String {
        return context.getString(resId, formatStrings)
    }
}

data class DownloadFailureLocalizationData(val resId: Int, val formatStrings: List<String> = listOf()) : Localization {
    override fun getString(context: Context): String {
        val errorDescriptionResId = R.string.illegal_state_downloads_did_not_complete_successfully
        val errorTypeString = context.getString(resId, formatStrings)
        return context.getString(errorDescriptionResId, errorTypeString)
    }
}

class Symlinker {
    fun createSymlink(targetPath: String, linkPath: String) {
        Os.symlink(targetPath, linkPath)
    }
}

class StorageUtility(private val statFs: StatFs) {

    fun getAvailableStorageInMB(): Long {
        val bytesInMB = 1048576
        val bytesAvailable = statFs.blockSizeLong * statFs.availableBlocksLong
        return bytesAvailable / bytesInMB
    }
}

class AssetPreferences(private val prefs: SharedPreferences) {

    private val versionString = "version"
    private val rootfsString = "rootfs"

    private val lowestVersion = "v0.0.0"
    fun getLatestDownloadVersion(repo: String): String {
        return prefs.getString("$repo-$versionString", lowestVersion) ?: lowestVersion
    }

    fun getLatestDownloadFilesystemVersion(repo: String): String {
        return prefs.getString("$repo-$rootfsString-$versionString", lowestVersion) ?: lowestVersion
    }

    fun setLatestDownloadVersion(repo: String, version: String) {
        with(prefs.edit()) {
            putString("$repo-$versionString", version)
            apply()
        }
    }

    fun setLatestDownloadFilesystemVersion(repo: String, version: String) {
        with(prefs.edit()) {
            putString("$repo-$rootfsString-$versionString", version)
            apply()
        }
    }

    private val downloadsAreInProgressKey = "downloadsAreInProgress"
    fun getDownloadsAreInProgress(): Boolean {
        return prefs.getBoolean(downloadsAreInProgressKey, false)
    }

    fun setDownloadsAreInProgress(inProgress: Boolean) {
        with(prefs.edit()) {
            putBoolean(downloadsAreInProgressKey, inProgress)
            apply()
        }
    }

    private val enqueuedDownloadsKey = "currentlyEnqueuedDownloads"
    fun getEnqueuedDownloads(): Set<Long> {
        val enqueuedDownloadsAsStrings = prefs.getStringSet(enqueuedDownloadsKey, setOf()) ?: setOf<String>()
        return enqueuedDownloadsAsStrings.map { it.toLong() }.toSet()
    }

    fun setEnqueuedDownloads(downloads: Set<Long>) {
        val enqueuedDownloadsAsStrings = downloads.map { it.toString() }.toSet()
        with(prefs.edit()) {
            putStringSet(enqueuedDownloadsKey, enqueuedDownloadsAsStrings)
            apply()
        }
    }

    fun clearEnqueuedDownloadsCache() {
        with(prefs.edit()) {
            remove(enqueuedDownloadsKey)
            apply()
        }
    }

    fun getCachedAssetList(assetType: String): List<Asset> {
        val entries = prefs.getStringSet(assetType, setOf()) ?: setOf()
        return entries.map { entry ->
            Asset(entry, assetType)
        }
    }

    fun setAssetList(assetType: String, assetList: List<Asset>) {
        val entries = assetList.map {
            it.name
        }.toSet()
        with(prefs.edit()) {
            putStringSet(assetType, entries)
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

        val xsdlAvailable = Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1
        val onlyCliSupported = app.supportsCli && !app.supportsGui
        val onlyVncSupported = app.supportsGui && !app.supportsCli && !xsdlAvailable
        return when {
            pref.toLowerCase() == "ssh" || onlyCliSupported -> SshTypePreference
            pref.toLowerCase() == "xsdl" -> XsdlTypePreference
            pref.toLowerCase() == "vnc" || onlyVncSupported -> VncTypePreference
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
        return if (supportedABIS.size == 1 && supportedABIS[0] == "") {
            val exception = IllegalStateException("No supported ABI!")
            SentryLogger().addExceptionBreadcrumb(exception)
            throw exception
        } else {
            supportedABIS[0]
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
    @Throws(Exception::class)
    fun getUrlInputStream(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        return conn.inputStream
    }
}

class DownloadManagerWrapper(private val downloadManager: DownloadManager) {
    fun generateDownloadRequest(url: String, destination: File): DownloadManager.Request {
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        val destinationUri = Uri.fromFile(destination)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle(destination.name)
        request.setDescription("Downloading ${destination.name.substringAfterLast("-")}.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationUri(destinationUri)
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

    fun downloadHasSucceeded(id: Long): Boolean {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
        return false
    }

    fun downloadHasFailed(id: Long): Boolean {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_FAILED
        }
        return false
    }

    fun getDownloadFailureReason(id: Long): DownloadFailureLocalizationData {
        val query = generateQuery(id)
        val cursor = generateCursor(query)
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
            return DownloadFailureLocalizationData(resId = when (status) {
                in 100..500 -> R.string.download_failure_http_error
                1008 -> R.string.download_failure_cannot_resume
                1007 -> R.string.download_failure_no_external_devices
                1009 -> R.string.download_failure_destination_exists
                1001 -> R.string.download_failure_unknown_file_error
                1004 -> R.string.download_failure_http_processing
                1006 -> R.string.download_failure_insufficient_external_storage
                1005 -> R.string.download_failure_too_many_redirects
                1002 -> R.string.download_failure_unhandled_http_response
                1000 -> R.string.download_failure_unknown_error
                else -> R.string.download_failure_missing_error
            }, formatStrings = listOf("$status")) // Format strings only used for http_error
        }
        return DownloadFailureLocalizationData(R.string.download_failure_reason_not_found)
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

class DeviceDimensions {
    private var height = 720
    private var width = 1480

    companion object {
        const val portrait = "SCREEN_ORIENTATION_PORTRAIT"
        const val landscape = "SCREEN_ORIENTATION_LANDSCAPE"
    }

    fun getDeviceDimensions(windowManager: WindowManager, displayMetrics: DisplayMetrics, context: Context) {
        val navBarSize = getNavigationBarSize(windowManager)
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        height = displayMetrics.heightPixels
        width = displayMetrics.widthPixels
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        when (checkOrientation(context)) {
            portrait -> if (navBarSize.y > 0) height += navBarSize.y
            landscape -> if (navBarSize.x > 0) width += navBarSize.x
        }
    }

    private fun getNavigationBarSize(windowManager: WindowManager): Point {
        val appUsableSize = Point()
        val realScreenSize = Point()
        val display = windowManager.defaultDisplay
        display.getSize(appUsableSize)
        display.getRealSize(realScreenSize)

        return Point(realScreenSize.x - appUsableSize.x, realScreenSize.y - appUsableSize.y)
    }

    private fun checkOrientation(context: Context): String {
        return when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> portrait
            else -> landscape
        }
    }

    fun getGeometry(): String {
        return when (height > width) {
            true -> "${height}x$width"
            false -> "${width}x$height"
        }
    }
}

class UserFeedbackUtility(private val prefs: SharedPreferences) {
    private val numberOfTimesOpenedKey = "numberOfTimesOpened"
    private val userGaveFeedbackKey = "userGaveFeedback"
    private val dateTimeFirstOpenKey = "dateTimeFirstOpen"
    private val millisecondsInThreeDays = 259200000L
    private val minimumNumberOfOpensBeforeReviewRequest = 15

    fun askingForFeedbackIsAppropriate(): Boolean {
        return getIsSufficientTimeElapsedSinceFirstOpen() && numberOfTimesOpenedIsGreaterThanThreshold() && !getUserGaveFeedback()
    }

    fun incrementNumberOfTimesOpened() {
        with(prefs.edit()) {
            val numberTimesOpened = prefs.getInt(numberOfTimesOpenedKey, 1)
            if (numberTimesOpened == 1) putLong(dateTimeFirstOpenKey, System.currentTimeMillis())
            putInt(numberOfTimesOpenedKey, numberTimesOpened + 1)
            apply()
        }
    }

    fun userHasGivenFeedback() {
        with(prefs.edit()) {
            putBoolean(userGaveFeedbackKey, true)
            apply()
        }
    }

    private fun getUserGaveFeedback(): Boolean {
        return prefs.getBoolean(userGaveFeedbackKey, false)
    }

    private fun getIsSufficientTimeElapsedSinceFirstOpen(): Boolean {
        val dateTimeFirstOpened = prefs.getLong(dateTimeFirstOpenKey, 0L)
        val dateTimeWithSufficientTimeElapsed = dateTimeFirstOpened + millisecondsInThreeDays

        return (System.currentTimeMillis() > dateTimeWithSufficientTimeElapsed)
    }

    private fun numberOfTimesOpenedIsGreaterThanThreshold(): Boolean {
        val numberTimesOpened = prefs.getInt(numberOfTimesOpenedKey, 1)
        return numberTimesOpened > minimumNumberOfOpensBeforeReviewRequest
    }
}