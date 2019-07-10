package tech.ula.utils.preferences

import android.content.Context
import tech.ula.model.entities.Asset

class AssetPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("assetLists", Context.MODE_PRIVATE)

    private val versionKey = "version"
    private val rootFsKey = "rootfs"
    private val downloadsAreInProgressKey = "downloadsAreInProgress"
    private val enqueuedDownloadsKey = "currentlyEnqueuedDownloads"

    private val lowestPossibleVersion = "v0.0.0"

    fun getLatestDownloadVersion(repo: String): String {
        return prefs.getString("$repo-$versionKey", lowestPossibleVersion) ?: lowestPossibleVersion
    }

    fun getLatestDownloadFilesystemVersion(repo: String): String {
        return prefs.getString("$repo-$rootFsKey-$versionKey", lowestPossibleVersion) ?: lowestPossibleVersion
    }

    fun setLatestDownloadVersion(repo: String, version: String) {
        with(prefs.edit()) {
            putString("$repo-$versionKey", version)
            apply()
        }
    }

    fun setLatestDownloadFilesystemVersion(repo: String, version: String) {
        with(prefs.edit()) {
            putString("$repo-$rootFsKey-$versionKey", version)
            apply()
        }
    }

    fun getDownloadsAreInProgress(): Boolean {
        return prefs.getBoolean(downloadsAreInProgressKey, false)
    }

    fun setDownloadsAreInProgress(inProgress: Boolean) {
        with(prefs.edit()) {
            putBoolean(downloadsAreInProgressKey, inProgress)
            apply()
        }
    }

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