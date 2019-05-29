package tech.ula.utils

import android.content.Context
import android.system.Os
import java.io.File

class UlaFiles(context: Context) {
    val filesDir: File = context.filesDir
    val scopedDir: File = context.storageRoot
    val libDir: File = File(context.applicationInfo.nativeLibraryDir)
    val libLinkDir: File = File(filesDir, "lib")
    val supportDir: File = File(filesDir, "support")

    val busybox = File(libDir, "busybox")
    val proot = File(libDir, "proot")

    // TODO error checking for these functions
    fun setupSupportDir() {
        supportDir.mkdirs()

        listOf(
                "addNonRootUser.sh",
                "busybox_static",
                "compressFilesystem.sh",
                "execInProot.sh",
                "isServerInProcTree.sh",
                "killProcTree.sh"
        ).forEach { filename ->
            val assetFile = File(libDir, filename)
            val target = File(supportDir, filename)
            assetFile.copyTo(target, overwrite = true)
        }
    }

    fun setupLinks() {
        listOf(
                "libc++_shared.so" to "libcppshared",
                "libcrypto.so.1.1" to "libcrypto.1.1",
                "libleveldb.so.1" to "libleveldb.1",
                "libtalloc.so.2" to "libtalloc.2",
                "libtermux-auth.so" to "libtermuxauth",
                "libutil.so" to "libutil"
        ).forEach { (requiredLinkName, actualLibName) ->
            if (!libLinkDir.exists()) libLinkDir.mkdirs()
            val libFile = File(libDir, actualLibName)
            val linkFile = File(libLinkDir, requiredLinkName)
            if (!libFile.exists()) throw NoSuchFileException(libFile)
            linkFile.delete()
            Os.symlink(libFile.path, linkFile.path)
        }
    }
}