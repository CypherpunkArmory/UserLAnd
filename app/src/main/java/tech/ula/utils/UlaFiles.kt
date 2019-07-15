package tech.ula.utils

import android.system.Os
import java.io.File
import java.lang.NullPointerException

class UlaFiles(
    val filesDir: File,
    val scopedDir: File,
    val libDir: File,
    private val symlinker: Symlinker = Symlinker()
) {

    val supportDir: File = File(filesDir, "support")
    val scopedUserDir: File = File(scopedDir, "storage")

    init {
        scopedUserDir.mkdirs()

        setupLinks()
    }

    val busybox = File(supportDir, "busybox")
    val proot = File(supportDir, "proot")

    fun makePermissionsUsable(containingDirectoryPath: String, filename: String) {
        val commandToRun = arrayListOf("chmod", "0777", filename)

        val containingDirectory = File(containingDirectoryPath)
        containingDirectory.mkdirs()

        val pb = ProcessBuilder(commandToRun)
        pb.directory(containingDirectory)

        val process = pb.start()
        process.waitFor()
    }

    // Lib files must start with 'lib' and end with '.so.'
    private fun String.toSupportName(): String {
        return this.substringAfter("lib_").substringBeforeLast(".so")
    }

    @Throws(NullPointerException::class, NoSuchFileException::class, Exception::class)
    private fun setupLinks() {
        supportDir.mkdirs()

        libDir.listFiles()!!.forEach { libFile ->
            val name = libFile.name.toSupportName()
            val linkFile = File(supportDir, name)
            linkFile.delete()
            symlinker.createSymlink(libFile.path, linkFile.path)
        }
    }
}

class Symlinker {
    fun createSymlink(targetPath: String, linkPath: String) {
        Os.symlink(targetPath, linkPath)
    }
}