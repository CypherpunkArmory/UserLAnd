package tech.ula.utils

import java.io.File

// TODO refactor this class with a better name
class FileUtility(private val applicationFilesDirectoryPath: String) {

    fun getSupportDirPath(): String {
        return "$applicationFilesDirectoryPath/support"
    }

    fun getFilesDirPath(): String {
        return applicationFilesDirectoryPath
    }

    fun createAndGetDirectory(directory: String): File {
        val file = File("${getFilesDirPath()}/$directory")
        if (!file.exists()) file.mkdirs()
        return file
    }

    fun statusFileExists(filesystemName: String, filename: String): Boolean {
        val file = File("${getFilesDirPath()}/$filesystemName/support/$filename")
        return file.exists()
    }

    fun distributionAssetsExist(distributionType: String): Boolean {
        val rootfsParts = listOf("rootfs.tar.gz.part00", "rootfs.tar.gz.part01", "rootfs.tar.gz.part02", "rootfs.tar.gz.part03")
        rootfsParts.map {
            File("${getFilesDirPath()}/$distributionType/$it")
        }.forEach {
            if (!it.exists()) return false
        }
        return true
    }

    // TODO is this actually necessary?
//    fun copyDistributionAssetsToFilesystem(targetFilesystemName: String, distributionType: String) {
//        val sharedDirectory = File("${getFilesDirPath()}/$distributionType")
//        val targetDirectory = createAndGetDirectory("$targetFilesystemName/support")
//        sharedDirectory.copyRecursively(targetDirectory, overwrite = true)
//        targetDirectory.walkBottomUp().forEach {
//            if (it.name == "support") {
//                return
//            }
//            changePermission(it.name, "$targetFilesystemName/support")
//        }
//    }
}