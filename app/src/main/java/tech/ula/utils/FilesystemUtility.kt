package tech.ula.utils

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import tech.ula.model.entities.Asset
import android.app.Activity
import android.net.Uri
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment.getExternalStorageDirectory
import android.content.ContentResolver
import android.content.Context
import android.os.Environment
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class FilesystemUtility(
    private val applicationFilesDirPath: String,
    private val execUtility: ExecUtility,
    private val logger: LogUtility = LogUtility()
) {

    private val filesystemExtractionSuccess = ".success_filesystem_extraction"
    private val filesystemExtractionFailure = ".failure_filesystem_extraction"

    private fun getSupportDirectoryPath(targetDirectoryName: String): String {
        return "$applicationFilesDirPath/$targetDirectoryName/support"
    }

    fun copyDistributionAssetsToFilesystem(targetFilesystemName: String, distributionType: String) {
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

    fun extractFilesystem(targetDirectoryName: String, listener: (String) -> Any) {
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"
        try {
            execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, listener)
        } catch (err: Exception) {
            logger.logRuntimeErrorForCommand(functionName = "extractFilesystem", command = command, err = err)
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

    fun deleteFilesystem(filesystemId: Long) {
        val directory = File("$applicationFilesDirPath/$filesystemId")
        launch(CommonPool) {
            if (directory.exists() && directory.isDirectory)
                directory.deleteRecursively()
            val isDirectoryDeleted = directory.deleteRecursively()
            if (isDirectoryDeleted) {
                val successMessage = "Successfully deleted filesystem located at: $directory"
                logger.v("Filesystem Delete", successMessage)
            } else {
                val errorMessage = "Error in attempting to delete filesystem located at: $directory"
                logger.e("Filesystem Delete", errorMessage)
            }
        }
    }

    fun backupFilesystemByLocation(execDirectory: String, directoryName: String, backupName: String, destinationDir: File) {
        val backupLocation = "../${backupName}"
        val backupLocationTmp = "${backupLocation}.tmp"
        val exclude = "--exclude=sys --exclude=dev --exclude=proc --exclude=data --exclude=mnt --exclude=host-rootfs --exclude=sdcard --exclude=etc/mtab --exclude=etc/ld.so.preload"
        val commandCompress = "rm -rf ${backupLocationTmp} && tar ${exclude} -cvpzf ${backupLocationTmp} ../${directoryName}"
        val commandMove = "rm -rf ${applicationFilesDirPath}/${backupName} && mv ${backupLocationTmp} ${backupLocation}"
        // TODO add in progress notification
        execUtility.wrapWithBusyboxAndExecute("${execDirectory}", commandCompress, doWait = true)
        execUtility.wrapWithBusyboxAndExecute("${execDirectory}", commandMove, doWait = true)
        val backupLocationF = File("${applicationFilesDirPath}/${backupName}")
        // TODO refactor to not using str.replace
        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val now = Date()
        val fileName = "." + formatter.format(now) + ".tar.gz"
        backupLocationF.copyTo(File(destinationDir, backupName.replace(".tar.gz",fileName)), overwrite = true)
    }

    // https://developertip.wordpress.com/2012/12/11/android-copy-file-programmatically-from-its-uri/
    fun copyFileFromUri(context: Context, saveFilePath: String, fileUri: Uri): Boolean {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            val content = context.getContentResolver()
            inputStream = content.openInputStream(fileUri)

            val root = Environment.getExternalStorageDirectory()
            if (root == null) {
                throw Exception("Failed to get root")
            }

            // create a directory
            //val saveDirectory = File(Environment.getExternalStorageDirectory().path + File.separator + "directory_name" + File.separator)
            // create direcotory if it doesn't exists
            //saveDirectory.mkdirs()

            outputStream = FileOutputStream(saveFilePath)//saveDirectory.path + "f.tar.gz") // filename.png, .mp3, .mp4 ...
            if (outputStream != null) {
                //Log.e(TAG, "Output Stream Opened successfully")
            }

            val buffer = ByteArray(1000)
            // TODO progress loading could go here
            while ((inputStream.read(buffer, 0, buffer.size)) >= 0) {
                outputStream!!.write(buffer, 0, buffer.size)
            }
        } catch (e: Exception) {
            throw e
        }
        return true
    }

    // TODO move to FilesystemListFragment menu
    fun restoreFilesystemByLocation(execDirectory: String, activity: Activity, backupUri: Uri, restoreDirName: String) {
        // TODO add in progress notification
        val backupFileName = backupUri.path.substring(backupUri.path.lastIndexOf('/')+1, backupUri.path.length).replace(".tar.gz","")
        val restoreFileNameTmp = "${backupFileName}.tar.gz.restore.tmp"
        val restoreDirNameTmp = "${restoreDirName}.restore.tmp"
        val restoreFileTmp = "${applicationFilesDirPath}/${restoreFileNameTmp}"
        val restoreDirTmp = "${applicationFilesDirPath}/${restoreDirNameTmp}"
        val filesystemDir = "${applicationFilesDirPath}/${restoreDirName}"
        val commandExtract = "rm -rf ${restoreDirTmp} && tar xpvzf ${restoreFileTmp} -C ${restoreDirTmp} && rm -rf ${filesystemDir} && mv ${restoreDirTmp} ${filesystemDir}"
        copyFileFromUri(context = activity, saveFilePath = restoreFileTmp, fileUri = backupUri)
        //backFile.copyTo(File(restoreFileTmp), overwrite = true)
        execUtility.wrapWithBusyboxAndExecute(execDirectory, commandExtract, doWait = true)
    }
}
