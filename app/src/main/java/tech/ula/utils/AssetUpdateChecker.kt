package tech.ula.utils

import java.io.File
import java.util.concurrent.TimeUnit

class AssetUpdateChecker(
        private val applicationFilesDirPath: String,
    private val timestampPreferenceUtility: TimestampPreferenceUtility
) {

    private val lastUpdateCheck: Long by lazy {
        // only grab the value from the database the first time such that we won't be looking at the value that is being
        // updated while we check each file
        timestampPreferenceUtility.getLastUpdateCheck()
    }

    fun doesAssetNeedToUpdated(
        asset: Asset
    ): Boolean {

        val assetFile = File("$applicationFilesDirPath/${asset.pathName}")

        if (!assetFile.exists()) return true

        val now = currentTimeSeconds()
        if (now > (lastUpdateCheck + TimeUnit.DAYS.toSeconds(1))) {
            timestampPreferenceUtility.setLastUpdateCheckToNow()
        } else {
            return false
        }

        val localTimestamp = timestampPreferenceUtility.getSavedTimestampForFile(asset.concatenatedName)
        return localTimestamp < asset.remoteTimestamp
    }
}