package tech.ula.utils

import java.io.File
import java.lang.NullPointerException

class UlaFiles(
        val filesDir: File,
        private val scopedDir: File,
        private val libDir: File,
        private val symlinker: Symlinker = Symlinker()
) {

    val supportDir: File = File(filesDir, "support")
    val scopedUserDir: File = File(scopedDir, "home")

    init {
        scopedUserDir.mkdirs()

        setupLinks()
    }

    val busybox = File(supportDir, "busybox")
    val proot = File(supportDir, "proot")

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