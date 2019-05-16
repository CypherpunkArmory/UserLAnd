package tech.ula.utils

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ProotDebugLogger(defaultSharedPreferences: SharedPreferences, storageRootPath: String) {
    private val prefs = defaultSharedPreferences

    val isEnabled: Boolean
        get() = prefs.getBoolean("pref_proot_debug_enabled", false)

    val verbosityLevel
        get() = prefs.getString("pref_proot_debug_level", "-1") ?: "-1"

    private val logName = "PRoot_Debug_Log.txt"
    private val logLocation = "$storageRootPath/$logName"

    fun logStream(
            inputStream: InputStream,
            coroutineScope: CoroutineScope
    ) = coroutineScope.launch {
        val prootLogFile = File(logLocation)
        if (prootLogFile.exists()) {
            prootLogFile.delete()
        }
        prootLogFile.createNewFile()
        val reader = inputStream.bufferedReader(Charsets.UTF_8)
        val writer = prootLogFile.writer(Charsets.UTF_8)
        reader.forEachLine { line -> writer.write("$line\n") }
        reader.close()
        writer.flush()
        writer.close()
    }

    fun generateCreateIntent(): Intent {
        // TODO remove
        File(logLocation).writeText("test")

        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, logName)
        }
    }

    fun copyLogToDestination(uri: Uri, contentResolver: ContentResolver): Boolean {
        val logFile = File(logLocation)
        if (!logFile.exists()) return false
        return try {
            contentResolver.openFileDescriptor(uri, "w")?.use { parcelFileDescriptor ->
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                    outputStream.write(logFile.readText().toByteArray())
                }
            }
            true
        } catch (err: Exception) {
            false
        }
    }

    fun deleteLog() {
        val logFile = File(logLocation)
        if (logFile.exists()) logFile.delete()
    }
}