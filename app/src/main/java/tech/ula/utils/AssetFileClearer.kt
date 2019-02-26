package tech.ula.utils

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Exception

class AssetFileClearer(
    private val filesDir: File,
    private val assetDirectoryNames: Set<String>,
    private val busyboxExecutor: BusyboxExecutor
) {
    @Throws(Exception::class)
    suspend fun clearAllSupportAssets() {
        if (!filesDir.exists()) throw FileNotFoundException()
        clearTopLevelAssets(assetDirectoryNames)
        clearFilesystemSupportAssets()
    }

    @Throws(IOException::class)
    private suspend fun clearTopLevelAssets(assetDirectoryNames: Set<String>) {
        for (file in filesDir.listFiles()) {
            if (!file.isDirectory) continue
            if (!assetDirectoryNames.contains(file.name)) continue
            if (!busyboxExecutor.recursivelyDelete(file.absolutePath)) throw IOException()
        }
    }

    @Throws(IOException::class)
    private suspend fun clearFilesystemSupportAssets() {
        for (file in filesDir.listFiles()) {
            if (!file.isDirectory || file.name.toIntOrNull() == null) continue

            val supportDirectory = File("${file.absolutePath}/support")
            if (!supportDirectory.exists() || !supportDirectory.isDirectory) continue

            for (supportFile in supportDirectory.listFiles()) {
                // Exclude directories and hidden files.
                if (supportFile.isDirectory || supportFile.name.first() == '.') continue
                // Use deleteRecursively extension to match functionality above
                if (!busyboxExecutor.recursivelyDelete(supportFile.path)) throw IOException()
            }
        }
    }
}