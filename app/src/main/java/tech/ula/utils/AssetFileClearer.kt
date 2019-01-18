package tech.ula.utils

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Exception

class AssetFileClearer(private val filesDir: File, private val assetDirectoryNames: Set<String>) {
    @Throws(Exception::class)
    fun clearAllSupportAssets() {
        if (!filesDir.exists()) throw FileNotFoundException()
        clearTopLevelAssets(assetDirectoryNames)
        clearFilesystemSupportAssets()
    }

    @Throws(IOException::class)
    private fun clearTopLevelAssets(assetDirectoryNames: Set<String>) {
        for (file in filesDir.listFiles()) {
            if (!file.isDirectory) continue
            if (!assetDirectoryNames.contains(file.name)) continue
            if (!file.deleteRecursively()) throw IOException()
        }
    }

    @Throws(IOException::class)
    private fun clearFilesystemSupportAssets() {
        for (file in filesDir.listFiles()) {
            if (!file.isDirectory || file.name.toIntOrNull() == null) continue

            val supportDirectory = File("${file.absolutePath}/support")
            if (!supportDirectory.exists() || !supportDirectory.isDirectory) continue

            for (supportFile in supportDirectory.listFiles()) {
                // Exclude directories and hidden files.
                if (supportFile.isDirectory || supportFile.name.first() == '.') continue
                // Use deleteRecursively extension to match functionality above
                if (!supportFile.deleteRecursively()) throw IOException()
            }
        }
    }
}