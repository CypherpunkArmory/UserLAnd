package tech.ula.utils

import android.content.Context
import android.os.Build
import java.io.File

class FilesystemUtility(private val context: Context) {

    private val execUtility by lazy {
        ExecUtility(context)
    }

    private val fileManager by lazy {
        FileUtility(context)
    }

    private fun getSupportDirectory(targetDirectoryName: String): File {
        return File("${fileManager.getFilesDirPath()}/$targetDirectoryName/support")
    }

    fun extractFilesystem(targetDirectoryName: String, listener: (String) -> Int) {
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

    fun deleteFilesystem(filesystemId: Long): Boolean {
        val directory = fileManager.createAndGetDirectory(filesystemId.toString())
        return directory.deleteRecursively()
    }

    fun getArchType(): String {
        if (Build.VERSION.SDK_INT >= 21) {
            val supportedABIS = Build.SUPPORTED_ABIS
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
        } else {
            return when {
                isSupported(Build.CPU_ABI) -> Build.CPU_ABI
                isSupported(Build.CPU_ABI2) -> Build.CPU_ABI2
                else -> throw Exception("No supported ABI!")
            }
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
