package tech.ula.utils

import java.io.File

class FilesystemUtility(
    private val applicationFilesDirPath: String,
    private val execUtility: ExecUtility
) {

    private val filesystemExtractionSuccess = ".success_filesystem_extraction"
    private val filesystemExtractionFailure = ".failure_filesystem_extraction"

    private fun getSupportDirectoryPath(targetDirectoryName: String): String {
        return "$applicationFilesDirPath/$targetDirectoryName/support"
    }

    fun extractFilesystem(targetDirectoryName: String, listener: (String) -> Any) {
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"
        execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, listener)
    }

    fun isExtractionComplete(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        val success = File("$supportPath/$filesystemExtractionSuccess")
        val failure = File("$supportPath/$filesystemExtractionFailure")
        return success.exists() || failure.exists()
    }

    fun didExtractionFail(targetDirectoryName: String): Boolean {
        return isExtractionComplete(targetDirectoryName) &&
                File("${getSupportDirectoryPath(targetDirectoryName)}/$$filesystemExtractionFailure")
                .exists()
    }

//    fun assetsArePresent(targetDirectoryName: String): Boolean {
//        val supportDirectory = getSupportDirectory(targetDirectoryName)
//        return supportDirectory.exists() &&
//                supportDirectory.isDirectory &&
//                supportDirectory.listFiles().isNotEmpty()
//    }

    fun deleteFilesystem(filesystemId: Long) {
        val directory = File("$applicationFilesDirPath/$filesystemId")
        if (directory.exists() && directory.isDirectory)
            directory.deleteRecursively()
    }
}
