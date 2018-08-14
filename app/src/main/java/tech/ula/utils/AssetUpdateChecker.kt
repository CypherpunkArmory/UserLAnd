package tech.ula.utils

import tech.ula.model.entities.Asset
import java.io.File

class AssetUpdateChecker(
    private val applicationFilesDirPath: String,
    private val timestampPreferenceUtility: TimestampPreferenceUtility
) {

    fun doesAssetNeedToUpdated(asset: Asset): Boolean {
        val assetFile = File("$applicationFilesDirPath/${asset.pathName}")

        if (!assetFile.exists()) return true

        val localTimestamp = timestampPreferenceUtility.getSavedTimestampForFile(asset.concatenatedName)
        return localTimestamp < asset.remoteTimestamp
    }
}