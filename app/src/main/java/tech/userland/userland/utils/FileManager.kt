package tech.userland.userland.utils

import android.content.Context
import android.os.Environment
import java.io.File

class FileManager(private val context: Context) {

    fun getSupportDir(): String {
        return "${context.filesDir.path}/support"
    }

    fun prependSupportDir(file: String): String {
        return "${getSupportDir()}/$file"
    }

    fun moveDownloadedAssetsToSupportDirectory() {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appFilesDirectoryPath = context.filesDir.path
        downloadDirectory.walkBottomUp().filter { it.name.contains("UserLAnd:") }
                .forEach {
                    val type = it.name.substringAfterLast(":")
                    val targetDestination = File("$appFilesDirectoryPath/support/$type")
                    it.copyTo(targetDestination)
                    it.delete()
                }
    }

    fun correctFilePermissions() {
        val filePermissions = listOf(
                Triple("proot", "", "0777"),
                Triple("busybox", "", "0777"),
                Triple("libtalloc.so.2", "", "0777"),
                Triple("execInProot", "", "0777"),
                Triple("startDBServer.sh", "", "0777"),
                Triple("busybox", "","0777"),
                Triple("libdisableselinux.so", "", "0777")
        )
        filePermissions.forEach { (file, subdirectory, permissions) -> changePermission(file, subdirectory, permissions) }
    }

    private fun changePermission(filename: String, subdirectory: String, permissions: String) {
        val directoryPath = "${getSupportDir()}/$subdirectory"
        val commandToRun = "chmod $permissions $filename"
        Exec().execLocal(directoryPath, commandToRun, listener = Exec.EXEC_INFO_LOGGER)
    }
}