package tech.userland.userland.utils

import android.content.Context
import android.os.Environment
import java.io.File

class FileManager(private val context: Context) {

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

    // TODO stop running this repeatedly
    // Filename takes form of UserLAnd:<directory to place in>:<filename>
    fun moveDownloadedAssetsToSupportDirectory() {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadDirectory.walkBottomUp().filter { it.name.contains("UserLAnd:") }
                .forEach {
                    val contents = it.name.split(":")
                    val targetDestination = File("${getFilesDirPath()}/${contents[1]}/${contents[2]}")
                    it.copyTo(targetDestination, overwrite = true)
                    it.delete()
                }
    }

    fun correctFilePermissions() {
        val filePermissions = listOf(
                Triple("proot", "support", "0777"),
                Triple("busybox", "support", "0777"),
                Triple("libtalloc.so.2", "support", "0777"),
                Triple("execInProot", "support", "0777"),
                Triple("startDBServer.sh", "debian", "0777"),
                Triple("busybox", "debian","0777"),
                Triple("libdisableselinux.so", "debian", "0777")
        )
        filePermissions.forEach { (file, subdirectory, permissions) -> changePermission(file, subdirectory, permissions) }
    }

    private fun changePermission(filename: String, subdirectory: String, permissions: String) {
        val executionDirectory = createAndGetDirectory(subdirectory)
        val commandToRun = arrayListOf("chmod", permissions, filename)
        Exec().execLocal(executionDirectory, commandToRun, listener = Exec.EXEC_INFO_LOGGER)
    }

    fun extractFilesystem(type: String, targetDirectoryName: String) {
        val executionDirectory = createAndGetDirectory(targetDirectoryName)

        // TODO type should eventually be debian, ubuntu, etc
        val commandToRun = arrayListOf("../support/busybox", "sh", "-c")
        commandToRun.add("../support/execInProot /support/busybox tar -xzvf /support/rootfs.tar.gz")

//        val env = hashMapOf("LD_LIBRARY_PATH" to (getSupportDirPath()),
//                "ROOT_PATH" to getFilesDirPath(),
//                "ROOTFS_PATH" to "${getFilesDirPath()}/$targetDirectoryName",
//                "PROOT_DEBUG_LEVEL" to "9")

        Exec().execLocal(executionDirectory, commandToRun, listener = Exec.EXEC_INFO_LOGGER)
    }
}