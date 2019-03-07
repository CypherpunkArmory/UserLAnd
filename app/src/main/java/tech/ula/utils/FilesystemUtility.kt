package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import java.io.File

class FilesystemUtility(
    private val applicationFilesDirPath: String,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: LogUtility = LogUtility()
) {

    private val filesystemExtractionSuccess = ".success_filesystem_extraction"
    private val filesystemExtractionFailure = ".failure_filesystem_extraction"

    private fun getSupportDirectoryPath(targetDirectoryName: String): String {
        return "$applicationFilesDirPath/$targetDirectoryName/support"
    }

    @Throws(Exception::class)
    fun copyAssetsToFilesystem(targetFilesystemName: String, distributionType: String) {
        val sharedDirectory = File("$applicationFilesDirPath/$distributionType")
        val targetDirectory = File("$applicationFilesDirPath/$targetFilesystemName/support")
        if (!targetDirectory.exists()) targetDirectory.mkdirs()
        sharedDirectory.copyRecursively(targetDirectory, overwrite = true)
        targetDirectory.walkBottomUp().forEach {
            if (it.name == "support") {
                return
            }
            makePermissionsUsable(targetDirectory.path, it.name)
        }
    }

    fun removeRootfsFilesFromFilesystem(targetFilesystemName: String) {
        val supportDirectory = File(getSupportDirectoryPath(targetFilesystemName))
        supportDirectory.walkBottomUp().forEach {
            if (it.name.contains("rootfs.tar.gz")) it.delete()
        }
    }

    fun extractFilesystem(filesystem: Filesystem, listener: (String) -> Any) {
        val filesystemDirName = "${filesystem.id}"
        val command = "/support/common/extractFilesystem.sh"
        val env = HashMap<String, String>()
        env["INITIAL_USERNAME"] = filesystem.defaultUsername
        env["INITIAL_PASSWORD"] = filesystem.defaultPassword
        env["INITIAL_VNC_PASSWORD"] = filesystem.defaultVncPassword

        val result = busyboxExecutor.executeProotCommand(
            command,
            filesystemDirName,
            commandShouldTerminate = true,
            env = env,
            listener = listener
        )
        if (result is FailedExecution) {
            val err = result.reason
            logger.logRuntimeErrorForCommand(functionName = "extractFilesystem", command = command, err = err)
        }
    }

    suspend fun compressFilesystem(filesystem: Filesystem, externalStorageDirectory: File, listener: (String) -> Any) = withContext(Dispatchers.IO) {
        val filesystemDirName = "${filesystem.id}"
        val command = "/support/common/compressFilesystem.sh"

        val externalUserlandDirPath = "${externalStorageDirectory.absolutePath}/UserLAnd"
        if (!File(externalUserlandDirPath).exists())
            File(externalUserlandDirPath).mkdirs()

        val backupName = "${filesystem.name}-${filesystem.distributionType}-rootfs.tar.gz"
        val backupTarget = "$externalUserlandDirPath/$backupName"
        val env = hashMapOf("TAR_PATH" to backupTarget)

        val result = busyboxExecutor.executeProotCommand(
                command,
                filesystemDirName,
                commandShouldTerminate = true,
                env = env,
                listener = listener
        )
        if (result is FailedExecution) {
            val err = result.reason
            logger.logRuntimeErrorForCommand(functionName = "compressFilesystem", command = command, err = err)
        }
    }

    fun isExtractionComplete(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        val success = File("$supportPath/$filesystemExtractionSuccess")
        val failure = File("$supportPath/$filesystemExtractionFailure")
        return success.exists() || failure.exists()
    }

    fun hasFilesystemBeenSuccessfullyExtracted(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        return File("$supportPath/$filesystemExtractionSuccess").exists()
    }

    fun areAllRequiredAssetsPresent(
        targetDirectoryName: String,
        distributionAssetList: List<Asset>
    ): Boolean {
        val supportDirectory = File(getSupportDirectoryPath(targetDirectoryName))
        if (!supportDirectory.exists() || !supportDirectory.isDirectory) return false

        val supportDirectoryFileNames = supportDirectory.listFiles().map { it.name }
        return distributionAssetList.all {
            supportDirectoryFileNames.contains(it.name)
        }
    }

    suspend fun deleteFilesystem(filesystemId: Long) {
        val filesystemDirectory = File("$applicationFilesDirPath/$filesystemId")
        if (!filesystemDirectory.exists() || !filesystemDirectory.isDirectory) return
        val result = busyboxExecutor.recursivelyDelete(filesystemDirectory.absolutePath)
        if (result is FailedExecution) {
            logger.e("FilesystemUtility", "Failed to delete filesystem: $filesystemId")
        }
    }

    @Throws(Exception::class)
    fun moveAppScriptToRequiredLocation(appName: String, appFilesystem: Filesystem) {
        // Profile.d scripts execute in alphabetical order.
        val fileNameToForceAppScriptToExecuteLast = "zzzzzzzzzzzzzzzz.sh"
        val appScriptSource = File("$applicationFilesDirPath/apps/$appName/$appName.sh")
        val appFilesystemProfileDDir = File("$applicationFilesDirPath/${appFilesystem.id}/etc/profile.d")
        val appScriptProfileDTarget = File("$appFilesystemProfileDDir/$fileNameToForceAppScriptToExecuteLast")

        appFilesystemProfileDDir.mkdirs()
        appScriptSource.copyTo(appScriptProfileDTarget, overwrite = true)

        appScriptProfileDTarget.apply {
            if (!exists()) throw NoSuchFileException(this)
        }
    }
}
