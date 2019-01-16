package tech.ula.utils

import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception

class SupportFileClearer(private val filesDir: File, private val assetDirectoryNames: Set<String>) {
    @Throws(Exception::class)
    fun clearAllSupportAssets() {
        if (!filesDir.exists()) throw FileNotFoundException()
        clearTopLevelAssets(assetDirectoryNames)
        clearFilesystemAssets()
    }

    private fun clearTopLevelAssets(assetDirectoryNames: Set<String>) {
        for (file in filesDir.listFiles()) {
            if (!file.isDirectory) continue
            if (assetDirectoryNames.contains(file.name)) {
                file.deleteRecursively()
            }
        }
    }

    private fun clearFilesystemAssets() {
        for (file in filesDir.listFiles()) {
            if (file.name.toIntOrNull() != null) {
                val supportDirectory = File("${file.absolutePath}/support")
                for (supportFile in supportDirectory.listFiles()) {
                    // Exclude directories and hidden files.
                    if (!supportFile.isDirectory && supportFile.name.first() != '.') {
                        supportFile.delete()
                    }
                }
            }
        }
    }
}