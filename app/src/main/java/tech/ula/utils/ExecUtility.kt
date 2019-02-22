package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.ArrayList
import java.lang.ProcessBuilder
import kotlin.text.Charsets.UTF_8

class ExecUtility(
    private val applicationFilesDirPath: String,
    private val externalStoragePath: String,
    private val defaultPreferences: DefaultPreferences
) {

    private val noOperationConsumer: (line: String) -> Int = { 0 }

    fun execLocal(
        executionDirectory: File,
        command: ArrayList<String>,
        listener: (String) -> Any = noOperationConsumer,
        doWait: Boolean = true,
        wrapped: Boolean = false,
        environmentVars: HashMap<String, String> = HashMap()
    ): Process {

        val prootDebuggingEnabled = defaultPreferences.getProotDebuggingEnabled()
        val prootDebuggingLevel =
                if (prootDebuggingEnabled) defaultPreferences.getProotDebuggingLevel()
                else "-1"
        val prootDebugLogLocation = defaultPreferences.getProotDebugLogLocation()

        val env = if (wrapped) hashMapOf("LD_LIBRARY_PATH" to "$applicationFilesDirPath/support",
                "ROOT_PATH" to applicationFilesDirPath,
                "ROOTFS_PATH" to "$applicationFilesDirPath/${executionDirectory.name}",
                "PROOT_DEBUG_LEVEL" to prootDebuggingLevel,
                "EXTRA_BINDINGS" to "-b $externalStoragePath:/sdcard",
                "OS_VERSION" to System.getProperty("os.version"))
        else hashMapOf()

        env.putAll(environmentVars)

        try {
            val pb = ProcessBuilder(command)
            pb.directory(executionDirectory)
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)

            val process = pb.start()
            val logProot = prootDebuggingEnabled && command.any { it.contains("execInProot") }

            if (logProot) {
                writeDebugLogFile(process.inputStream, prootDebugLogLocation)
                listener("Output being redirected to PRoot debug log.")
            }

            if (doWait) {
                if (!logProot) {
                    collectOutput(process.inputStream, listener)
                }
                if (process.waitFor() != 0) {
                    listener("Exec: Failed to execute command ${pb.command()}")
                }
            }

            return process
        } catch (err: Exception) {
            listener("Exec: $err")
            val errorMessage = "\n\tError while executing ExecLocal: $err"
            throw RuntimeException(errorMessage)
        }
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        val buf = inputStream.bufferedReader(UTF_8)

        buf.forEachLine { listener(it) }

        buf.close()
    }

    private fun writeDebugLogFile(inputStream: InputStream, debugLogLocation: String) {
        GlobalScope.launch {
            val reader = inputStream.bufferedReader(UTF_8)
            val writer = File(debugLogLocation).writer(UTF_8)
            reader.forEachLine {
                writer.write("$it\n")
            }
            reader.close()
            writer.flush()
            writer.close()
        }
    }

    fun wrapWithBusyboxAndExecute(
        targetDirectoryName: String,
        commandToWrap: String,
        listener: (String) -> Any = noOperationConsumer,
        doWait: Boolean = true,
        environmentVars: HashMap<String, String> = HashMap()
    ): Process {
        val executionDirectory = File("$applicationFilesDirPath/$targetDirectoryName")
        val command = arrayListOf("../support/busybox", "sh", "-c", commandToWrap)
        try {
            return execLocal(executionDirectory, command, listener, doWait, true, environmentVars)
        } catch (err: Exception) {
            listener("Exec: $err")
            val errorMessage = "Error while executing BusyBox: \nCommand = $command\n\tError = $err"
            throw RuntimeException(errorMessage)
        }
    }

    suspend fun recursivelyDelete(pathToDirectoryToDelete: String): Boolean = withContext(Dispatchers.IO) {
        val command = "rm -r $pathToDirectoryToDelete"
        val proc = wrapWithBusyboxAndExecute("", command, doWait = false)
        return@withContext proc.waitFor() == 0
    }
}
