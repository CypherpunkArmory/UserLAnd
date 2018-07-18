package tech.ula.utils

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.HashMap
import kotlin.text.Charsets.UTF_8

class ExecUtility(private val context: Context) {

    companion object {
        val EXEC_DEBUG_LOGGER = { line: String -> Unit
            Log.d("EXEC_DEBUG_LOGGER", line)
        }

        val NOOP_CONSUMER: (line: String) -> Int = {0}
    }

    private val fileManager by lazy {
        FileUtility(context)
    }

    fun execLocal(executionDirectory: File, command: ArrayList<String>, env: HashMap<String, String> = hashMapOf(), listener: (String) -> Int = NOOP_CONSUMER, doWait: Boolean = true): Process {
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
                    Log.e("Exec", "Failed to execute command ${pb.command()}")
                }
            }
            return process
        } catch (err: Exception) {
            Log.e("Exec", err.toString())
            throw RuntimeException(err)
        }
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Int) {
        val buf: BufferedReader = inputStream.bufferedReader(UTF_8)

        buf.forEachLine {
            listener(it)
        }

        buf.close()
    }


    fun wrapWithBusyboxAndExecute(targetDirectoryName: String, commandToWrap: String, listener: (String) -> Int = NOOP_CONSUMER, doWait: Boolean = true): Process {
        val executionDirectory = fileManager.createAndGetDirectory(targetDirectoryName)

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prootDebuggingEnabled = preferences.getBoolean("pref_proot_debug_enabled", false)
        val prootDebuggingLevel =
                if(prootDebuggingEnabled) preferences.getString("pref_proot_debug_level", "-1")
                else "-1"
        /* val prootFileLogging = preferences.getBoolean("pref_proot_local_file_enabled", false) */

        val command = arrayListOf("../support/busybox", "sh", "-c")

        val commandToAdd =
                // TODO Fix this bug. If logging is enabled and it doesn't write to a file, isServerInProcTree can't find dropbear.
                /*if(prootDebuggingEnabled && prootFileLogging) "$commandToWrap &> /mnt/sdcard/PRoot_Debug_Log"*/
                if(prootDebuggingEnabled) "$commandToWrap &> /mnt/sdcard/PRoot_Debug_Log"
                else commandToWrap

        command.add(commandToAdd)

        val env = hashMapOf("LD_LIBRARY_PATH" to (fileManager.getSupportDirPath()),
                "ROOT_PATH" to fileManager.getFilesDirPath(),
                "ROOTFS_PATH" to "${fileManager.getFilesDirPath()}/$targetDirectoryName",
                "PROOT_DEBUG_LEVEL" to prootDebuggingLevel,
                "EXTRA_BINDINGS" to "-b ${Environment.getExternalStorageDirectory().getAbsolutePath()}:/sdcard")

        return execLocal(executionDirectory, command, env, listener, doWait)
    }

}