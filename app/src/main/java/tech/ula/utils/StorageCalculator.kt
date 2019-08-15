package tech.ula.utils

import android.os.StatFs

class StorageCalculator(private val statFs: StatFs) {
    fun getAvailableStorageInMB(): Long {
        val bytesInMB = 1048576
        val bytesAvailable = statFs.blockSizeLong * statFs.availableBlocksLong
        return bytesAvailable / bytesInMB
    }
}