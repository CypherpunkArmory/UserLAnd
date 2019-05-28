package tech.ula.utils

import android.content.Context
import java.io.File

class UlaFiles(context: Context) {
    val filesDir: File = context.filesDir
    val scopedDir: File = context.storageRoot
    val libDir: File = File(context.applicationInfo.nativeLibraryDir)
    val supportDir: File = File(filesDir, "support")

    val busybox = File(libDir, "busybox")
    val proot = File(libDir, "proot")
}