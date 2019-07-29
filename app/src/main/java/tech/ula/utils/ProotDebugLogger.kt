package tech.ula.utils

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

class ProotDebugLogger(defaultSharedPreferences: SharedPreferences, private val ulaFiles: UlaFiles) {
    private val prefs = defaultSharedPreferences

    val isEnabled: Boolean
        get() = prefs.getBoolean("pref_proot_debug_enabled", false)

    val verbosityLevel
        get() = prefs.getString("pref_proot_debug_level", "-1") ?: "-1"

    private val logName = "Proot_Debug_Log.txt"
    private val logLocation = "${ulaFiles.emulatedUserDir}/$logName"

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

    fun deleteLogs() {
        val scopedFiles = ulaFiles.emulatedUserDir.listFiles() ?: return
        for (file in scopedFiles) {
            if (file.name.contains(logName)) file.delete()
        }
    }
}