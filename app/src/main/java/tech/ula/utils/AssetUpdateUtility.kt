package tech.ula.utils

import java.io.File
import java.util.concurrent.TimeUnit

class AssetUpdateUtility(private val timestampPreferenceUtility: TimestampPreferenceUtility) {

    private val distType = filesystem.distributionType
    private val archType = filesystem.archType

    private val allAssetListTypes = listOf(
            "support" to "all",
            "support" to archType,
            distType to "all",
            distType to archType
    )

    private val lastUpdateCheck: Long by lazy {
        // only grab the value from the database the first time such that we won't be looking at the value that is being
        // updated while we check each file
        timestampPreferenceUtility.getLastUpdateCheck()
    }

    fun doesAssetNeedsToUpdated(
            fileLocation: String
            remoteTimestamp: Long,
            updateIsBeingForced: Boolean
    ): Boolean {
        val asset = File("$applicationFilesDirPath/$repo/$filename")

        if (filename.contains("rootfs.tar.gz") && session.isExtracted) return false

        val now = currentTimeSeconds()
        if (updateIsBeingForced ||
                !asset.exists() ||
                !session.isExtracted ||
                now > (lastUpdateCheck + TimeUnit.DAYS.toSeconds(1))) {
            timestampPreferenceUtility.setLastUpdateCheck(now)
        } else {
            return false
        }

        val timestampPrefName = "$repo:$filename"
        val localTimestamp = timestampPreferenceUtility.getSavedTimestampForFile(timestampPrefName)
        if (localTimestamp < remoteTimestamp) {
            if (asset.exists())
                asset.delete()
        }

        return !asset.exists()
    }
}