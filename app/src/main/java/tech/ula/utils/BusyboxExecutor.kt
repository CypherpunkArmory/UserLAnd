package tech.ula.utils

import android.os.Environment
import android.system.Os
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

sealed class ExecutionResult
data class MissingExecutionAsset(val asset: String) : ExecutionResult()
object SuccessfulExecution : ExecutionResult()
data class FailedExecution(val reason: String) : ExecutionResult()
data class OngoingExecution(val process: Process) : ExecutionResult()

class BusyboxExecutor(
    private val ulaFiles: UlaFiles,
    private val prootDebugLogger: ProotDebugLogger,
    private val busyboxWrapper: BusyboxWrapper = BusyboxWrapper(ulaFiles)
) {

    private val discardOutput: (String) -> Any = { }

    fun setupLinks() {
        busyboxWrapper.setupLibrarySymlinks()
    }

    fun executeCommand(
        command: String,
        listener: (String) -> Any = discardOutput
    ): ExecutionResult {
        if (!busyboxWrapper.busyboxIsPresent()) {
            return MissingExecutionAsset("busybox")
        }

        val updatedCommand = busyboxWrapper.addBusybox(command)
        val env = busyboxWrapper.getBusyboxEnv()
        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(ulaFiles.filesDir)
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

    fun executeProotCommand(
        command: String,
        filesystemDirName: String,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ): ExecutionResult {
        when {
            !busyboxWrapper.busyboxIsPresent() ->
                return MissingExecutionAsset("busybox")
            !busyboxWrapper.prootIsPresent() ->
                return MissingExecutionAsset("proot")
            !busyboxWrapper.executionScriptIsPresent() ->
                return MissingExecutionAsset("execution script")
        }

        val prootDebugEnabled = prootDebugLogger.isEnabled
        val prootDebugLevel =
                if (prootDebugEnabled) prootDebugLogger.verbosityLevel else "-1"

        val updatedCommand = busyboxWrapper.addBusyboxAndProot(command)
        val filesystemDir = File("${ulaFiles.filesDir.absolutePath}/$filesystemDirName")

        env.putAll(busyboxWrapper.getProotEnv(filesystemDir, prootDebugLevel))

        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(ulaFiles.filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        return try {
            val process = processBuilder.start()
            when {
                prootDebugEnabled && commandShouldTerminate -> {
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    getProcessResult(process)
                }
                prootDebugEnabled && !commandShouldTerminate -> {
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    OngoingExecution(process)
                }
                commandShouldTerminate -> {
                    collectOutput(process.inputStream, listener)
                    getProcessResult(process)
                }
                else -> {
                    OngoingExecution(process)
                }
            }
        } catch (err: Exception) {
            FailedExecution("$err")
        }
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

    private fun getProcessResult(process: Process): ExecutionResult {
        return if (process.waitFor() == 0) SuccessfulExecution
        else FailedExecution("Command failed with: ${process.exitValue()}")
    }
}

// This class is intended to allow stubbing of elements that are unavailable during unit tests.
class BusyboxWrapper(private val ulaFiles: UlaFiles) {
    // For basic commands, CWD should be `applicationFilesDir`
    fun addBusybox(command: String): List<String> {
        return listOf(ulaFiles.busybox.absolutePath, "sh") + command.split(" ")
    }

    fun getBusyboxEnv(): HashMap<String, String> {
        return hashMapOf(
                "LIB_PATH" to ulaFiles.libDir.absolutePath,
                "ROOT_PATH" to ulaFiles.filesDir.absolutePath
        )
    }

    fun busyboxIsPresent(): Boolean {
        return ulaFiles.busybox.exists()
    }

    // Proot scripts expect CWD to be `applicationFilesDir/<filesystem`
    fun addBusyboxAndProot(command: String): List<String> {
        return listOf(ulaFiles.busybox.absolutePath, "sh", "support/execInProot.sh") + command.split(" ")
    }

    fun getProotEnv(filesystemDir: File, prootDebugLevel: String): HashMap<String, String> {
        return hashMapOf(
                "LD_LIBRARY_PATH" to ulaFiles.libLinkDir.absolutePath,
                "LIB_PATH" to ulaFiles.libDir.absolutePath,
                "ROOT_PATH" to ulaFiles.filesDir.absolutePath,
                "ROOTFS_PATH" to filesystemDir.absolutePath,
                "PROOT_DEBUG_LEVEL" to prootDebugLevel,
                // TODO this should change as part of Q, but we need a way to import public files
//                "EXTRA_BINDINGS" to "-b ${ulaFiles.scopedDir.absolutePath}:/sdcard",
                "EXTRA_BINDINGS" to "-b ${Environment.getExternalStorageDirectory()}:/sdcard",
                "OS_VERSION" to System.getProperty("os.version")
        )
    }

    fun prootIsPresent(): Boolean {
        return ulaFiles.proot.exists()
    }

    fun executionScriptIsPresent(): Boolean {
        val execInProotFile = File(ulaFiles.supportDir, "execInProot.sh")
        return execInProotFile.exists()
    }

    fun setupLibrarySymlinks() {
        listOf(
                "libc++_shared.so" to "libcpp",
                "libcrypto.so.1.1" to "libcrypto.so.1.1",
                "libleveldb.so.1" to "libleveldb.so.1",
                "libtalloc.so.2" to "libtalloc.so.2",
                "libtermux-auth.so" to "libtermuxauth",
                "libutil.so" to "libutil"
        ).forEach { (requiredLinkName, actualLibName) ->
            if (!ulaFiles.libLinkDir.exists()) ulaFiles.libLinkDir.mkdirs()
            val libFile = File(ulaFiles.libDir, actualLibName)
            val linkFile = File(ulaFiles.libLinkDir, requiredLinkName)
            if (!libFile.exists()) throw NoSuchFileException(libFile)
            linkFile.delete()
            Log.e("SYMLINK", "${libFile.name} : ${linkFile.name}")
            Os.symlink(libFile.path, linkFile.path)
        }
    }
}