package tech.ula.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import java.io.File
import java.io.IOException

/**
 * FilesystemManager
 *
 * Responsible for:
 *  - copying shared assets into per-filesystem support dirs,
 *  - extracting/compressing rootfs via proot scripts,
 *  - checking extraction state,
 *  - deleting filesystem trees,
 *  - wiring app scripts into profile.d.
 *
 * Hardening (2025, Rafael Melo Reis – ∆RafaelVerboΩ):
 *  - Defensive checks around shared dirs and assets.
 *  - Safer delete paths + better error breadcrumbs.
 *  - Extra env for better script interoperability (USERLAND_*).
 */
class FilesystemManager(
    private val ulaFiles: UlaFiles,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: Logger = SentryLogger()
) {

    private val filesDirPath = ulaFiles.filesDir.path
    private val filesystemExtractionSuccess = ".success_filesystem_extraction"
    private val filesystemExtractionFailure = ".failure_filesystem_extraction"

    private val TAG = "FilesystemManager"

    private fun getSupportDirectoryPath(targetDirectoryName: String): String {
        return "$filesDirPath/$targetDirectoryName/support"
    }

    /**
     * Copy shared distribution assets (rootfs, scripts, etc.) into a specific filesystem support dir.
     * Skips rootfs tar when filesystem was created from backup.
     */
    @Throws(Exception::class)
    fun copyAssetsToFilesystem(filesystem: Filesystem) {
        val distributionType = filesystem.distributionType
        val targetFilesystemName = "${filesystem.id}"

        val sharedDirectory = File("$filesDirPath/$distributionType")
        if (!sharedDirectory.exists() || !sharedDirectory.isDirectory) {
            val msg =
                "Shared distribution directory missing or invalid: '${sharedDirectory.absolutePath}' for type '$distributionType'"
            Log.e(TAG, msg)
            throw IOException(msg)
        }

        val targetDirectory = File("$filesDirPath/$targetFilesystemName/support")
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            val msg = "Failed to create target support directory: '${targetDirectory.absolutePath}'"
            Log.e(TAG, msg)
            throw IOException(msg)
        }

        val files = sharedDirectory.listFiles()
        if (files == null || files.isEmpty()) {
            val msg = "No shared assets found in '${sharedDirectory.absolutePath}' for type '$distributionType'"
            Log.e(TAG, msg)
            throw IOException(msg)
        }

        for (file in files) {
            // If filesystem came from backup, skip rootfs tar to avoid overwriting backup content.
            if (file.name.contains("rootfs") && filesystem.isCreatedFromBackup) continue

            val targetFile = File(targetDirectory, file.name)
            try {
                file.copyTo(targetFile, overwrite = true)
                ulaFiles.makePermissionsUsable(targetDirectory.absolutePath, file.name)
            } catch (e: Exception) {
                Log.e(TAG, "copyAssetsToFilesystem() failed copying '${file.name}': ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Remove rootfs tarballs from a filesystem's support dir (e.g., post-extraction cleanup).
     */
    fun removeRootfsFilesFromFilesystem(targetFilesystemName: String) {
        val supportDirectory = File(getSupportDirectoryPath(targetFilesystemName))
        if (!supportDirectory.exists() || !supportDirectory.isDirectory) return

        supportDirectory.walkBottomUp().forEach { file ->
            if (file.name.contains("rootfs.tar.gz")) {
                val deleted = runCatching { file.delete() }.getOrDefault(false)
                if (!deleted) {
                    Log.w(
                        TAG,
                        "removeRootfsFilesFromFilesystem() failed to delete '${file.absolutePath}'"
                    )
                }
            }
        }
    }

    /**
     * Extract a filesystem using proot-controlled script.
     * Passes initial credentials to script via environment.
     */
    suspend fun extractFilesystem(
        filesystem: Filesystem,
        listener: (String) -> Any
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val filesystemDirName = "${filesystem.id}"
        val command = "/support/common/extractFilesystem.sh"
        val env = HashMap<String, String>().apply {
            this["INITIAL_USERNAME"] = filesystem.defaultUsername
            this["INITIAL_PASSWORD"] = filesystem.defaultPassword
            this["INITIAL_VNC_PASSWORD"] = filesystem.defaultVncPassword
            // Extra context for scripts / diagnostics:
            this["USERLAND_FS_ID"] = filesystem.id.toString()
            this["USERLAND_FS_DISTRO"] = filesystem.distributionType
            this["USERLAND_FS_IS_BACKUP"] = filesystem.isCreatedFromBackup.toString()
        }

        Log.d(
            TAG,
            "extractFilesystem(): id=${filesystem.id}, distro=${filesystem.distributionType}, fromBackup=${filesystem.isCreatedFromBackup}"
        )

        return@withContext busyboxExecutor.executeProotCommand(
            command = command,
            filesystemDirName = filesystemDirName,
            commandShouldTerminate = true,
            env = env,
            listener = listener
        )
    }

    /**
     * Compress a filesystem into a tarball located at [scopedExternalDestination].
     */
    suspend fun compressFilesystem(
        filesystem: Filesystem,
        scopedExternalDestination: File,
        listener: (String) -> Any
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val filesystemDirName = "${filesystem.id}"
        val command = "/support/common/compressFilesystem.sh"
        val env = HashMap<String, String>().apply {
            this["TAR_PATH"] = scopedExternalDestination.absolutePath
            this["USERLAND_FS_ID"] = filesystem.id.toString()
            this["USERLAND_FS_DISTRO"] = filesystem.distributionType
        }

        Log.d(
            TAG,
            "compressFilesystem(): id=${filesystem.id}, tarPath=${scopedExternalDestination.absolutePath}"
        )

        return@withContext busyboxExecutor.executeProotCommand(
            command = command,
            filesystemDirName = filesystemDirName,
            commandShouldTerminate = true,
            env = env,
            listener = listener
        )
    }

    /**
     * Extraction is considered complete if either a success or failure marker exists.
     */
    fun isExtractionComplete(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        val success = File("$supportPath/$filesystemExtractionSuccess")
        val failure = File("$supportPath/$filesystemExtractionFailure")
        return success.exists() || failure.exists()
    }

    /**
     * Returns true if extraction success marker is present.
     */
    fun hasFilesystemBeenSuccessfullyExtracted(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        return File("$supportPath/$filesystemExtractionSuccess").exists()
    }

    /**
     * Checks whether all required distribution assets are present
     * in the support directory for the given filesystem ID.
     */
    fun areAllRequiredAssetsPresent(
        targetDirectoryName: String,
        distributionAssetList: List<Asset>
    ): Boolean {
        if (distributionAssetList.isEmpty()) {
            // No declared required assets -> treat as false (caller must be explicit).
            Log.w(TAG, "areAllRequiredAssetsPresent(): empty distributionAssetList for '$targetDirectoryName'")
            return false
        }

        val supportDirectory = File(getSupportDirectoryPath(targetDirectoryName))
        if (!supportDirectory.exists() || !supportDirectory.isDirectory) return false

        val supportFiles = supportDirectory.listFiles() ?: return false
        if (supportFiles.isEmpty()) return false

        val supportDirectoryFileNames = supportFiles.map { it.name }
        val allPresent = distributionAssetList.all { asset ->
            supportDirectoryFileNames.contains(asset.name)
        }

        if (!allPresent) {
            Log.w(
                TAG,
                "Missing required assets in '$targetDirectoryName'. Found=$supportDirectoryFileNames, Required=${distributionAssetList.map { it.name }}"
            )
        }

        return allPresent
    }

    /**
     * Deletes a filesystem directory via external script.
     * On script failure, logs and throws IOException.
     */
    @Throws(IOException::class)
    suspend fun deleteFilesystem(filesystemId: Long) = withContext(Dispatchers.IO) {
        val filesystemDirectory = File("$filesDirPath/$filesystemId")
        if (!filesystemDirectory.exists() || !filesystemDirectory.isDirectory) {
            Log.d(TAG, "deleteFilesystem(): directory does not exist for id=$filesystemId")
            return@withContext
        }

        val command = "support/deleteFilesystem.sh ${filesystemDirectory.path}"
        val result = busyboxExecutor.executeScript(command)

        if (result is FailedExecution) {
            Log.e(TAG, "deleteFilesystem(): script failed for id=$filesystemId: ${result.reason}")

            // Opcional: tentar fallback com delete recursivo seguro do BusyboxExecutor.
            val fallback = busyboxExecutor.recursivelyDelete(filesystemDirectory.absolutePath)
            if (fallback is FailedExecution) {
                val err = IOException("deleteFilesystem() failed for id=$filesystemId: ${result.reason}; fallback=${fallback.reason}")
                logger.addExceptionBreadcrumb(err)
                throw err
            }
        }
    }

    /**
     * Copies an app-specific script into profile.d of the target filesystem,
     * forcing it to execute last (alphabetical order).
     */
    @Throws(IOException::class)
    fun moveAppScriptToRequiredLocation(appName: String, appFilesystem: Filesystem) {
        val fileNameToForceAppScriptToExecuteLast = "zzzzzzzzzzzzzzzz.sh"
        val appScriptSource = File("$filesDirPath/apps/$appName/$appName.sh")
        val appFilesystemProfileDDir = File("$filesDirPath/${appFilesystem.id}/etc/profile.d")
        val appScriptProfileDTarget = File(appFilesystemProfileDDir, fileNameToForceAppScriptToExecuteLast)

        if (!appScriptSource.exists() || !appScriptSource.isFile) {
            val msg = "App script not found for '$appName' at '${appScriptSource.absolutePath}'"
            Log.e(TAG, msg)
            val exception = IOException(msg)
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }

        try {
            if (!appFilesystemProfileDDir.exists() && !appFilesystemProfileDDir.mkdirs()) {
                val msg = "Failed to create profile.d directory at '${appFilesystemProfileDDir.absolutePath}'"
                Log.e(TAG, msg)
                val exception = IOException(msg)
                logger.addExceptionBreadcrumb(exception)
                throw exception
            }

            appScriptSource.copyTo(appScriptProfileDTarget, overwrite = true)
        } catch (err: Exception) {
            Log.e(
                TAG,
                "moveAppScriptToRequiredLocation() failed for app='$appName', fsId=${appFilesystem.id}: ${err.message}",
                err
            )
            val exception = IOException("Failed to move app script for '$appName' to profile.d")
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
    }
}
