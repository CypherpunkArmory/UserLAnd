package tech.ula.utils

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.ArrayList
import kotlin.text.Charsets.UTF_8

class ExecUtility(val fileUtility: FileUtility, val preferenceUtility: PreferenceUtility) {

    companion object {
        val EXEC_DEBUG_LOGGER = { line: String -> Unit
            Log.d("EXEC_DEBUG_LOGGER", line)
        }

        val NOOP_CONSUMER: (line: String) -> Int = { 0 }
    }

    fun execLocal(executionDirectory: File, command: ArrayList<String>,
                  listener: (String) -> Any = NOOP_CONSUMER, doWait: Boolean = true,
                  wrapped: Boolean = false): Process {

        //TODO refactor naming convention to command debugging log
        val prootDebuggingEnabled = preferenceUtility.getProotDebuggingEnabled()
        val prootDebuggingLevel =
                if(prootDebuggingEnabled) preferenceUtility.getProotDebuggingLevel()
                else "-1"
        val prootDebugLogLocation = preferenceUtility.getProotDebugLogLocation()

        val commandToLog =
                // TODO Fix this bug. If logging is enabled and it doesn't write to a file, isServerInProcTree can't find dropbear.
                /*if(prootDebuggingEnabled && prootFileLogging) "$commandToWrap &> /mnt/sdcard/PRoot_Debug_Log"*/
                if(prootDebuggingEnabled) "${command[command.lastIndex]} &> $prootDebugLogLocation"
                else command[command.lastIndex]
        command[command.lastIndex] = commandToLog

        val env = if (wrapped) hashMapOf("LD_LIBRARY_PATH" to (fileUtility.getSupportDirPath()),
                "ROOT_PATH" to fileUtility.getFilesDirPath(),
                "ROOTFS_PATH" to "${fileUtility.getFilesDirPath()}/${executionDirectory.path}",
                "PROOT_DEBUG_LEVEL" to prootDebuggingLevel)
        else hashMapOf()

        try {
            val pb = ProcessBuilder(command)
            pb.directory(executionDirectory)
            pb.redirectErrorStream(true)
            pb.environment().putAll(env)
            listener("Running: ${pb.command()} \n with env $env")

            val process = pb.start()

            if (doWait) {
                collectOutput(process.inputStream, listener)

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
        val buf: BufferedReader = inputStream.bufferedReader(UTF_8)

        buf.forEachLine {
            listener(it)
        }

        buf.close()
    }

    fun wrapWithBusyboxAndExecute(targetDirectoryName: String, commandToWrap: String, listener: (String) -> Any = NOOP_CONSUMER, doWait: Boolean = true): Process {
        val executionDirectory = fileUtility.createAndGetDirectory(targetDirectoryName)
        val command = arrayListOf("../support/busybox", "sh", "-c", commandToWrap)

        return execLocal(executionDirectory, command, listener, doWait, wrapped = true)
    }
}