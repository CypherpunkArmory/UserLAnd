package tech.userland.userland.utils

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.HashMap
import kotlin.text.Charsets.UTF_8

class Exec {

    companion object {
        val EXEC_INFO_LOGGER = { line: String -> Unit
            Log.i("EXEC_INFO_LOGGER", line)
        }

        val NOOP_CONSUMER: (line: String) -> Int = {0}
    }

    fun execLocal(executionDirectory: File, command: ArrayList<String>, env: HashMap<String, String> = hashMapOf(), listener: (String) -> Int = NOOP_CONSUMER): Process {
        try {
            val pb = ProcessBuilder(command)
            pb.directory(executionDirectory)
            pb.redirectErrorStream(true)
            pb.environment().putAll(env)
            Log.i("ExecUtils","Running: ${pb.command()} \n with env $env")

            val process = pb.start()
            val result = collectOutput(process.inputStream, listener)

            if (process.waitFor() != 0) {
                Log.e("Exec","Failed to execute command ${pb.command()}\nstdout: $result")
            } else {
                Log.i("Exec", "stdout: $result")
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
}