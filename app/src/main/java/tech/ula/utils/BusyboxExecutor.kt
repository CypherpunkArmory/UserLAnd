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

    private val discardOutput: (String) -> Any = {}

    @Throws(Exception::class)
    fun executeCommand(
        command: String,
        listener: (String) -> Any = discardOutput
    ): Boolean {
        val updatedCommand = busyboxWrapper.addBusybox(command)
        val env = busyboxWrapper.getBusyboxEnv(filesDir)
        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        collectOutput(process.inputStream, listener)
        return process.waitFor() == 0
    }

    @Throws(Exception::class)
    fun executeProotCommand(
        command: String,
        filesystemDirName: String,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ): Process {
        val prootDebugEnabled = defaultPreferences.getProotDebuggingEnabled()
        val prootDebugLevel =
                if (prootDebugEnabled) defaultPreferences.getProotDebuggingLevel() else "-1"
        val prootDebugLocation = defaultPreferences.getProotDebugLogLocation()

        val updatedCommand = busyboxWrapper.addBusyboxAndProot(command)
        val filesystemDir = File("${filesDir.absolutePath}/$filesystemDirName")

        env.putAll(busyboxWrapper.getProotEnv(filesDir, filesystemDir, prootDebugLevel, externalStorageDir))

        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        if (prootDebugEnabled) redirectOutputToDebugLog(process.inputStream, prootDebugLocation, coroutineScope)
        else if (commandShouldTerminate) {
            collectOutput(process.inputStream, listener)
        }
        return process
    }

    suspend fun recursivelyDelete(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        val command = "rm -rf $absolutePath"
        return@withContext executeCommand(command)
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        val buf = inputStream.bufferedReader(Charsets.UTF_8)

        buf.forEachLine { listener(it) }

        buf.close()
    }

    private fun redirectOutputToDebugLog(
            inputStream: InputStream,
            prootDebugLocation: String,
            coroutineScope: CoroutineScope
    ) = coroutineScope.launch {
        val prootLogFile = File(prootDebugLocation)
        if (prootLogFile.exists()) {
            prootLogFile.delete()
        }
        prootLogFile.createNewFile()
        val reader = inputStream.bufferedReader(UTF_8)
        val writer = prootLogFile.writer(UTF_8)
        reader.forEachLine { line -> writer.write("$line\n") }
        reader.close()
        writer.flush()
        writer.close()
    }
}

// This class is intended to allow stubbing of elements that are unavailable during unit tests.
class BusyboxWrapper {
    // For basic commands, CWD should be `applicationFilesDir`
    fun addBusybox(command: String): List<String> {
        return listOf("support/busybox", "sh", "-c", command)
    }

    fun getBusyboxEnv(filesDir: File): HashMap<String, String> {
        return hashMapOf("ROOT_PATH" to filesDir.absolutePath)
    }

    // Proot scripts expect CWD to be `applicationFilesDir/<filesystem`
    fun addBusyboxAndProot(command: String): List<String> {
        val commandWithProot = "support/execInProot.sh $command"
        return listOf("support/busybox", "sh", "-c", commandWithProot)
    }

    fun getProotEnv(filesDir: File, filesystemDir: File, prootDebugLevel: String, externalStorageDir: File): HashMap<String, String> {
        return hashMapOf(
                "LD_LIBRARY_PATH" to "${filesDir.absolutePath}/support",
                "ROOT_PATH" to filesDir.absolutePath,
                "ROOTFS_PATH" to filesystemDir.absolutePath,
                "PROOT_DEBUG_LEVEL" to prootDebugLevel,
                "EXTRA_BINDINGS" to "-b ${externalStorageDir.absolutePath}:/sdcard",
                "OS_VERSION" to System.getProperty("os.version"))
    }
}