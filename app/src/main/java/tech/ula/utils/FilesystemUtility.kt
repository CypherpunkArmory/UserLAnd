package tech.ula.utils

import java.io.File

class FilesystemUtility(
    private val execUtility: ExecUtility,
    private val fileUtility: FileUtility,
    private val buildUtility: BuildUtility
) {

    private fun getSupportDirectory(targetDirectoryName: String): File {
        return File("${fileUtility.getFilesDirPath()}/$targetDirectoryName/support")
    }

    fun extractFilesystem(targetDirectoryName: String, listener: (String) -> Any): Boolean {
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"
        try {
            execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, listener)
        } catch (err: Exception) {
            return false
        }
        return true
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

    fun deleteFilesystem(filesystemId: Long): Boolean {
        val directory = fileUtility.createAndGetDirectory(filesystemId.toString())
        return directory.deleteRecursively()
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
