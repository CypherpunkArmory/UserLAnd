package tech.ula.utils

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import java.io.File

class FilesystemUtility(
    private val execUtility: ExecUtility,
    private val fileUtility: FileUtility,
    private val buildUtility: BuildUtility,
    private val logger: LogUtility = LogUtility()

) {

    private fun getSupportDirectory(targetDirectoryName: String): File {
        return File("${fileUtility.getFilesDirPath()}/$targetDirectoryName/support")
    }

    fun extractFilesystem(targetDirectoryName: String, listener: (String) -> Any) {
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"
        execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, listener)
    }

    fun assetsArePresent(targetDirectoryName: String): Boolean {
        val supportDirectory = getSupportDirectory(targetDirectoryName)
        return supportDirectory.exists() &&
                supportDirectory.isDirectory &&
                supportDirectory.listFiles().isNotEmpty()
    }

    fun removeRootfsFilesFromFilesystem(targetDirectoryName: String) {
        val supportDirectory = getSupportDirectory(targetDirectoryName)
        supportDirectory.walkTopDown().forEach {
            if (it.name.contains("rootfs.tar.gz")) it.delete()
        }
    }

    fun deleteFilesystem(filesystemId: Long) {
        val directory = fileUtility.createAndGetDirectory(filesystemId.toString())
        launch(CommonPool) {
            val isDirectoryDeleted = directory.deleteRecursively()
            if (isDirectoryDeleted) {
                logger.v("Success", "Successfully deleted Filesystem $directory")
            } else {
                val errorMessage = "Filesystem for directory: $directory has not been deleted successfully."
                logger.e("DeleteFilesystemFail = ", errorMessage)
            }
        }
    }

    fun getArchType(): String {
        val supportedABIS = buildUtility.getSupportedAbis()
                .map {
                    translateABI(it)
                }
                .filter {
                    isSupported(it)
                }
        if (supportedABIS.size == 1 && supportedABIS[0] == "") {
            throw Exception("No supported ABI!")
        } else {
            return supportedABIS[0]
        }
    }

    private fun isSupported(abi: String): Boolean {
        val supportedABIs = listOf("arm64", "arm", "x86_64", "x86")
        return supportedABIs.contains(abi)
    }

    private fun translateABI(abi: String): String {
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> ""
        }
    }
}
