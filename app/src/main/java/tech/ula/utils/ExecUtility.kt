package tech.ula.utils

import android.content.Context
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
        val EXEC_INFO_LOGGER = { line: String -> Unit
            Log.i("EXEC_INFO_LOGGER", line)
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
            Log.i("ExecUtils","Running: ${pb.command()} \n with env $env")

            val process = pb.start()

            if (doWait) {
                val result = collectOutput(process.inputStream, listener)

                if (process.waitFor() != 0) {
                    Log.e("Exec", "Failed to execute command ${pb.command()}\nstdout: $result")
                } else {
                    Log.i("Exec", "stdout: $result")
                }
            }
            return process
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Int): String {
        val out = StringBuilder()
        val buf: BufferedReader = inputStream.bufferedReader(UTF_8)

        val allText = buf.use(BufferedReader::readText)
        listener(allText)
        out.append(allText)
        return out.toString()
    }


    fun wrapWithBusyboxAndExecute(targetDirectoryName: String, commandToWrap: String, doWait: Boolean = true): Process {
        val executionDirectory = fileManager.createAndGetDirectory(targetDirectoryName)

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prootDebuggingEnabled = preferences.getBoolean("pref_proot_debug_enabled", false)
        val prootDebuggingLevel =
                if(prootDebuggingEnabled) preferences.getString("pref_proot_debug_level", "-1")
                else "-1"

        val command = arrayListOf("../support/busybox", "sh", "-c")

        val commandToAdd =
                if(prootDebuggingEnabled) "$commandToWrap &> /mnt/sdcard/PRoot_Debug_Log"
                else commandToWrap

        command.add(commandToAdd)

        val env = hashMapOf("LD_LIBRARY_PATH" to (fileManager.getSupportDirPath()),
                "ROOT_PATH" to fileManager.getFilesDirPath(),
                "ROOTFS_PATH" to "${fileManager.getFilesDirPath()}/$targetDirectoryName",
                "PROOT_DEBUG_LEVEL" to prootDebuggingLevel)

        return execLocal(executionDirectory, command, env, EXEC_INFO_LOGGER, doWait)
    }

}