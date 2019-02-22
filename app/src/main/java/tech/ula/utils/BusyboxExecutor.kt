package tech.ula.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.text.Charsets.UTF_8

class BusyboxExecutor(
    private val filesDir: File,
    private val externalStorageDir: File,
    private val defaultPreferences: DefaultPreferences,
    private val busyboxWrapper: BusyboxWrapper = BusyboxWrapper()
) {

    private val discardOutput: (String) -> Any = { }

    suspend fun recursivelyDelete(pathToDirectoryToDelete: String): Boolean = withContext(Dispatchers.IO) {
        val command = "rm -rf $pathToDirectoryToDelete"
        return@withContext executeCommand(command)
    }

    fun executeCommand(
        command: String,
        listener: (String) -> Any = discardOutput
    ): Boolean {
        val updatedCommand = busyboxWrapper.addBusybox(command)
        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(filesDir)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        collectOutput(process.inputStream, listener)
        return process.waitFor() == 0
    }

    fun executeProotCommand(
        command: String,
        filesystemDir: File,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput
    ): Process {
        val prootDebugEnabled = defaultPreferences.getProotDebuggingEnabled()
        val prootDebugLevel =
                if (prootDebugEnabled) defaultPreferences.getProotDebuggingLevel() else "-1"
        val prootDebugLocation = defaultPreferences.getProotDebugLogLocation()

        env.putAll(hashMapOf(
                "LD_LIBRARY_PATH" to "${filesDir.absolutePath}/support",
                "ROOT_PATH" to filesDir.absolutePath,
                "ROOTFS_PATH" to filesystemDir.absolutePath,
                "PROOT_DEBUG_LEVEL" to prootDebugLevel,
                "EXTRA_BINDINGS" to "-b ${externalStorageDir.absolutePath}:/sdcard",
                "OS_VERSION" to System.getProperty("os.version")))

        val updatedCommand = busyboxWrapper.addBusyboxAndProot(command)
        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(filesystemDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        when {
            prootDebugEnabled -> redirectOutputToDebugLog(process.inputStream, prootDebugLocation)
            commandShouldTerminate && !prootDebugEnabled -> collectOutput(process.inputStream, listener)
        }
        if (prootDebugEnabled) redirectOutputToDebugLog(process.inputStream, prootDebugLocation)
        if (commandShouldTerminate && !prootDebugEnabled) {
            collectOutput(process.inputStream, listener)
        }
        return process
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        val buf = inputStream.bufferedReader(Charsets.UTF_8)

        buf.forEachLine { listener(it) }

        buf.close()
    }

    private fun redirectOutputToDebugLog(inputStream: InputStream, prootDebugLocation: String) {
        val prootLogFile = File(prootDebugLocation)
        if (prootLogFile.exists()) {
            prootLogFile.delete()
        }
        prootLogFile.createNewFile()
        CoroutineScope(Dispatchers.IO).launch {
            val reader = inputStream.bufferedReader(UTF_8)
            val writer = prootLogFile.writer(UTF_8)
            reader.forEachLine { line -> writer.write("$line\n") }
            reader.close()
            writer.flush()
            writer.close()
        }
    }
}

// This class is intended to allow stubbing of elements that are unavailable during unit tests
class BusyboxWrapper {
    fun addBusybox(command: String): String {
        return "../support/busybox sh -c $command"
    }

    fun addBusyboxAndProot(command: String): String {
        val commandWithProot = "../support/execInProot.sh /bin/bash -c $command"
        return addBusybox(commandWithProot)
    }
}