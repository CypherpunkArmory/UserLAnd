package tech.ula.utils

import android.content.Context
import android.os.Build

class FilesystemUtility(private val context: Context) {

    private val execUtility by lazy {
        ExecUtility(context)
    }

    private val fileManager by lazy {
        FileUtility(context)
    }

    fun extractFilesystem(targetDirectoryName: String) {
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"
        execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command)
    }

    fun deleteFilesystem(filesystemDirectoryName: String): Boolean {
        val directory = fileManager.createAndGetDirectory(filesystemDirectoryName)
        return directory.deleteRecursively()
    }

    fun getArchType(): String {
        if(Build.VERSION.SDK_INT >= 21) {
            val supportedABIS = Build.SUPPORTED_ABIS.map {
                translateABI(it)
            }
            supportedABIS.filter {
                isSupported(it)
            }
            if(supportedABIS.size == 1 && supportedABIS[0] == "") {
                throw Exception("No supported ABI!")
            }
            else {
                return supportedABIS[0]
            }
        }
        else {
            return if(isSupported(Build.CPU_ABI)) Build.CPU_ABI
            else if(isSupported(Build.CPU_ABI2)) Build.CPU_ABI2
            else {
                throw Exception("No supported ABI!")
            }
        }
    }

    private fun isSupported(abi: String): Boolean {
        val supportedABIs = listOf("arm64", "armhf", "x86_64", "x86")
        return supportedABIs.contains(abi)
    }

    private fun translateABI(abi: String): String {
        return when(abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> ""
        }
    }

}