package tech.ula.utils

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.io.InputStream
import java.util.ArrayList
import java.lang.ProcessBuilder
import kotlin.text.Charsets.UTF_8

class ExecUtility(val fileUtility: FileUtility, val defaultPreferenceUtility: DefaultPreferenceUtility) {

    companion object {
        val EXEC_DEBUG_LOGGER = { line: String -> Unit
            Log.d("EXEC_DEBUG_LOGGER", line)
        }

        val NOOP_CONSUMER: (line: String) -> Int = { 0 }
    }

    fun execLocal(
        executionDirectory: File,
        command: ArrayList<String>,
        listener: (String) -> Any = NOOP_CONSUMER,
        doWait: Boolean = true,
        wrapped: Boolean = false
    ): Process {

        // TODO refactor naming convention to command debugging log
        val prootDebuggingEnabled = defaultPreferenceUtility.getProotDebuggingEnabled()
        val prootDebuggingLevel =
                if (prootDebuggingEnabled) defaultPreferenceUtility.getProotDebuggingLevel()
                else "-1"
        val prootDebugLogLocation = defaultPreferenceUtility.getProotDebugLogLocation()

        val env = if (wrapped) hashMapOf("LD_LIBRARY_PATH" to (fileUtility.getSupportDirPath()),
                "ROOT_PATH" to fileUtility.getFilesDirPath(),
                "ROOTFS_PATH" to "${fileUtility.getFilesDirPath()}/${executionDirectory.name}",
                "PROOT_DEBUG_LEVEL" to prootDebuggingLevel,
                "EXTRA_BINDINGS" to "-b ${Environment.getExternalStorageDirectory().getAbsolutePath()}:/sdcard")
        else hashMapOf()

        try {
            val pb = ProcessBuilder(command)
            pb.directory(executionDirectory)
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)

            listener("Running: ${pb.command()} \n with env $env")

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
            throw RuntimeException(err)
        }
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        val buf = inputStream.bufferedReader(UTF_8)

        buf.forEachLine {
            listener(it)
        }

        buf.close()
    }

    private fun writeDebugLogFile(inputStream: InputStream, debugLogLocation: String) {
        // TODO Fix this bug. If logging is enabled and it doesn't write to a file, isServerInProcTree can't find dropbear.
        launch(CommonPool) {
            async {
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
    }

    fun wrapWithBusyboxAndExecute(targetDirectoryName: String, commandToWrap: String, listener: (String) -> Any = NOOP_CONSUMER, doWait: Boolean = true): Process {
        val executionDirectory = fileUtility.createAndGetDirectory(targetDirectoryName)
        val command = arrayListOf("../support/busybox", "sh", "-c", commandToWrap)

        return execLocal(executionDirectory, command, listener, doWait, wrapped = true)
    }
}