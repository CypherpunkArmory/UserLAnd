package tech.ula.utils

class FlowUtility {
    fun largeAssetRequiredAndNoWifi(): Boolean {
        val filesystemIsPresent = session.isExtracted || filesystem.isDownloaded
        return !(filesystemIsPresent || wifiIsEnabled())
    }

    fun
}