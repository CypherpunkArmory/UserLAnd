package tech.ula.utils

import java.util.concurrent.TimeUnit

class AssetUpdateChecker(
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

        val now = currentTimeSeconds()
        if (now > (lastUpdateCheck + TimeUnit.DAYS.toSeconds(1))) {
            timestampPreferenceUtility.setLastUpdateCheckToNow()
        } else {
            return false
        }

        val localTimestamp = timestampPreferenceUtility.getSavedTimestampForFile(asset.qualifedName)
        return localTimestamp < asset.remoteTimestamp
    }
}