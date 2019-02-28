package tech.ula.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.text.Charsets.UTF_8

sealed class ExecutionResult
data class MissingExecutionAsset(val asset: String) : ExecutionResult()
object SuccessfulExecution : ExecutionResult()
data class FailedExecution(val reason: String) : ExecutionResult()
data class OngoingExecution(val process: Process) : ExecutionResult()

class BusyboxExecutor(
    private val filesDir: File,
    private val externalStorageDir: File,
    private val defaultPreferences: DefaultPreferences,
    private val busyboxWrapper: BusyboxWrapper = BusyboxWrapper()
) {

    private val discardOutput: (String) -> Any = { Log.e("BusyboxExecutor", it)}

    @Throws(Exception::class)
    fun executeCommand(
        command: String,
        listener: (String) -> Any = discardOutput
    ): ExecutionResult {
        if (!busyboxWrapper.busyboxIsPresent(filesDir)) {
            return MissingExecutionAsset("busybox")
        }
        val updatedCommand = busyboxWrapper.addBusybox(command)
        val env = busyboxWrapper.getBusyboxEnv(filesDir)
        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        return try {
            val process = processBuilder.start()
            collectOutput(process.inputStream, listener)
            getProcessResult(process)
        } catch (err: Exception) {
            FailedExecution("$err")
        }
    }

    @Throws(Exception::class)
    fun executeProotCommand(
        command: String,
        filesystemDirName: String,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ): ExecutionResult {
        when {
            !busyboxWrapper.busyboxIsPresent(filesDir) -> return MissingExecutionAsset("busybox")
            !busyboxWrapper.prootIsPresent(filesDir) -> return MissingExecutionAsset("proot")
            !busyboxWrapper.executionScriptIsPresent(filesDir) -> return MissingExecutionAsset("execution script")
        }

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
            return getProcessResult(process)
        }
        return OngoingExecution(process)
    }

    suspend fun recursivelyDelete(absolutePath: String): ExecutionResult = withContext(Dispatchers.IO) {
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

    private fun getProcessResult(process: Process): ExecutionResult {
        return if (process.waitFor() == 0) SuccessfulExecution
        else FailedExecution("Command failed with: ${process.exitValue()}")
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

    fun busyboxIsPresent(filesDir: File): Boolean {
        val busyboxFile = File("${filesDir.absolutePath}/support/busybox")
        return busyboxFile.exists()
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

    fun prootIsPresent(filesDir: File): Boolean {
        val prootFile = File("${filesDir.absolutePath}/support/proot")
        return prootFile.exists()
    }

    fun executionScriptIsPresent(filesDir: File): Boolean {
        val execInProotFile = File("${filesDir.absolutePath}/support/execInProot.sh")
        return execInProotFile.exists()
    }
}