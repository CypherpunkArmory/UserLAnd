package tech.ula.utils

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import java.io.File

// TODO refactor this class with a better name
class FileUtility(private val context: Context) {

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
        downloadDirectory.walkBottomUp().filter { it.name.contains("UserLAnd:") }
                .forEach {
                    val contents = it.name.split(":")
                    val targetDestination = File("${getFilesDirPath()}/${contents[1]}/${contents[2]}")
                    it.copyTo(targetDestination, overwrite = true)
                    it.delete()
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
                Triple("busybox", "support", "0777"),
                Triple("libtalloc.so.2", "support", "0777"),
                Triple("execInProot", "support", "0777"),
                Triple("" +
                        "" +
                        "" +
                        "" +
                        "" +
                        "" +
                        "" +
                        ".sh", "debian", "0777"),
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

    fun wrapWithBusyboxAndExecute(targetDirectoryName: String, commandToWrap: String): Process {
        val executionDirectory = createAndGetDirectory(targetDirectoryName)

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prootDebuggingEnabled = preferences.getBoolean("pref_proot_debug_enabled", false)
        val prootDebuggingLevel =
                if(prootDebuggingEnabled) preferences.getString("pref_proot_debug_level", "-1")
                else "-1"

        val command = arrayListOf("../support/busybox", "sh", "-c")

        val commandToAdd =
                if(prootDebuggingEnabled) "$commandToWrap &> /mnt/sdcard/PRoot_Debug_Log"
                else commandToWrap

        command.add(commandToAdd)

        val env = hashMapOf("LD_LIBRARY_PATH" to (getSupportDirPath()),
                "ROOT_PATH" to getFilesDirPath(),
                "ROOTFS_PATH" to "${getFilesDirPath()}/$targetDirectoryName",
                "PROOT_DEBUG_LEVEL" to prootDebuggingLevel)

        return Exec().execLocal(executionDirectory, command, env, Exec.EXEC_INFO_LOGGER)
    }

    fun extractFilesystem(targetDirectoryName: String) {
        val command = "../support/execInProot /support/extractFilesystem.sh"
        wrapWithBusyboxAndExecute(targetDirectoryName, command)
    }

    fun deleteFilesystem(filesystemDirectoryName: String): Boolean {
        val directory = createAndGetDirectory(filesystemDirectoryName)
        return directory.deleteRecursively()
    }
}