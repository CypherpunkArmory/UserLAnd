package tech.ula.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

class ProotDebugLogger(defaultPreferences: DefaultPreferences, storageRootPath: String) {
    val isEnabled = defaultPreferences.getProotDebuggingEnabled()
    val verbosityLevel = defaultPreferences.getProotDebuggingLevel()

    private val logLocation = "$storageRootPath/Proot_Debug_Log.txt"

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
}