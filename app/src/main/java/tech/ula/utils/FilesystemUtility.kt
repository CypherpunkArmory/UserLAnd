package tech.ula.utils

import android.content.Context

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

}