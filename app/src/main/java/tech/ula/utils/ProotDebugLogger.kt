package tech.ula.utils

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class ProotDebugLogger(defaultSharedPreferences: SharedPreferences, storageRootPath: String) {
    private val prefs = defaultSharedPreferences

    val isEnabled: Boolean
        get() = prefs.getBoolean("pref_proot_debug_enabled", false)

    val verbosityLevel
        get() = prefs.getString("pref_proot_debug_level", "-1") ?: "-1"

    val logName = "Proot_Debug_Log.txt"
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

    suspend fun copyLogToDestination(uri: Uri, contentResolver: ContentResolver): Boolean = withContext(Dispatchers.IO) {
        val logFile = File(logLocation)
        try {
            contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
                    outputStream.write(logFile.readText().toByteArray())
            }
            return@withContext true
        } catch (err: Exception) {
            return@withContext false
        }
    }

    fun deleteLog() {
        val logFile = File(logLocation)
        if (logFile.exists()) logFile.delete()
    }
}