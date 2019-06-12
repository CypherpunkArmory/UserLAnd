package tech.ula.utils

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Exception

class AssetFileClearer(
    private val filesDir: File,
    private val assetDirectoryNames: Set<String>,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: Logger = SentryLogger()
) {
    @Throws(Exception::class)
    suspend fun clearAllSupportAssets() {
        if (!filesDir.exists()) {
            val exception = FileNotFoundException()
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
        clearFilesystemSupportAssets()
        clearTopLevelAssets(assetDirectoryNames)
    }

    @Throws(IOException::class)
    private suspend fun clearTopLevelAssets(assetDirectoryNames: Set<String>) {
        for (file in filesDir.listFiles()) {
            if (!file.isDirectory) continue
            if (!assetDirectoryNames.contains(file.name)) continue
            if (file.name == "support") continue
            if (busyboxExecutor.recursivelyDelete(file.absolutePath) !is SuccessfulExecution) {
                val exception = IOException()
                logger.addExceptionBreadcrumb(exception)
                throw exception
            }
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
                // Use deleteRecursively to match functionality above
                if (busyboxExecutor.recursivelyDelete(supportFile.path) !is SuccessfulExecution) {
                    val exception = IOException()
                    logger.addExceptionBreadcrumb(exception)
                    throw exception
                }
            }
        }
    }
}