package tech.ula.utils

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class AssetFileClearer(
    private val ulaFiles: UlaFiles,
    private val assetDirectoryNames: Set<String>,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: Logger = SentryLogger()
) {
    @Throws(FileNotFoundException::class, IllegalStateException::class)
    suspend fun clearAllSupportAssets() {
        if (!ulaFiles.filesDir.exists()) {
            val exception = FileNotFoundException()
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
        if (!ulaFiles.busybox.exists()) {
            val exception = IllegalStateException("Busybox missing")
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
        clearFilesystemSupportAssets()
        clearTopLevelAssets(assetDirectoryNames)
    }

    @Throws(IOException::class)
    private suspend fun clearTopLevelAssets(assetDirectoryNames: Set<String>) {
        for (file in ulaFiles.filesDir.listFiles()) {
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
        for (file in ulaFiles.filesDir.listFiles()) {
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