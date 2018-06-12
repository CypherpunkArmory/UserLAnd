package tech.ula.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

// TODO refactor this class with a better name
class FileUtility(private val context: Context) {

    private val execUtility by lazy {
        ExecUtility(context)
    }

    fun getSupportDirPath(): String {
        return "${context.filesDir.path}/support"
    }

    fun getFilesDirPath(): String {
        return context.filesDir.path
    }

    fun createAndGetDirectory(directory: String): File {
        val file = File("${getFilesDirPath()}/$directory")
        if(!file.exists()) file.mkdirs()
        return file
    }

    fun statusFileExists(filesystemName: String, filename: String): Boolean {
        val file = File("${getFilesDirPath()}/$filesystemName/support/$filename")
        return file.exists()
    }

    // Filename takes form of UserLAnd:<directory to place in>:<filename>
    fun moveDownloadedAssetsToSharedSupportDirectory() {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadDirectory.walkBottomUp()
                .filter { it.name.contains("UserLAnd:") }
                .forEach {
                    val contents = it.name.split(":")
                    val targetDestination = File("${getFilesDirPath()}/${contents[1]}/${contents[2]}")
                    it.copyTo(targetDestination, overwrite = true)
                    if(!it.delete())
                        Log.e("FileUtility", "Could not delete downloaded file: ${it.name}")
                }
    }

    fun copyDistributionAssetsToFilesystem(targetFilesystemName: String, distributionType: String) {
        val sharedDirectory = File("${getFilesDirPath()}/$distributionType")
        val targetDirectory = createAndGetDirectory("$targetFilesystemName/support")
        sharedDirectory.copyRecursively(targetDirectory, overwrite = true)
        targetDirectory.walkBottomUp().forEach {
            if(it.name == "support") {
                return
            }
            changePermission(it.name, "$targetFilesystemName/support", "0777")
        }
    }

    fun correctFilePermissions() {
        val filePermissions = listOf(
                Triple("proot", "support", "0777"),
                Triple("killProcTree.sh", "support", "0777"),
                Triple("isServerInProcTree.sh", "support", "0777"),
                Triple("busybox", "support", "0777"),
                Triple("libtalloc.so.2", "support", "0777"),
                Triple("execInProot.sh", "support", "0777"),
                Triple("startSSHServer.sh", "debian", "0777"),
                Triple("busybox", "debian","0777"),
                Triple("libdisableselinux.so", "debian", "0777")
        )
        filePermissions.forEach { (file, subdirectory, permissions) -> changePermission(file, subdirectory, permissions) }
    }

    private fun changePermission(filename: String, subdirectory: String, permissions: String) {
        val executionDirectory = createAndGetDirectory(subdirectory)
        val commandToRun = arrayListOf("chmod", permissions, filename)
        execUtility.execLocal(executionDirectory, commandToRun, listener = ExecUtility.EXEC_DEBUG_LOGGER)
    }

}