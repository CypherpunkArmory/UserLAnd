package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UlaFiles(
    val filesDir: File,
    val scopedDir: File,
    val libDir: File,
    private val symlinker: Symlinker = Symlinker()
) {

    val supportDir: File = File(filesDir, "support")
    val scopedUserDir: File = File(scopedDir, "home")

    init {
        scopedUserDir.mkdirs()
    }

    val busybox = File(supportDir, "busybox")
    val proot = File(supportDir, "proot")

    // Lib files must start with 'lib' and end with '.so.'
    private fun String.toSupportName(): String {
        return this.substringAfter("lib_").substringBeforeLast(".so")
    }

    suspend fun setupLinks() = withContext(Dispatchers.IO) {
        supportDir.mkdirs()

        libDir.listFiles().forEach { libFile ->
            val name = libFile.name.toSupportName()
            val linkFile = File(supportDir, name)
            linkFile.delete()
            symlinker.createSymlink(libFile.path, linkFile.path)
        }
    }
}