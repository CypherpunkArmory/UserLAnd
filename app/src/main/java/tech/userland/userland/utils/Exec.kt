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

//    fun executeCommand(commandDir: File, command: String, env: HashMap<String, String> = hashMapOf(), listener: (String) -> Int = NOOP_CONSUMER): String {
//        return execLocal(commandDir, command, env, listener)
//    }

//    fun executeCommandAsync(commandDir: File, command: String, env: HashMap<String, String>, listener: (String) -> Int): ProcessWrapper {
//        return execLocalAsync(commandDir, command, env, listener)
//    }

    fun execLocal(executionDirectoryPath: String, command: String, env: HashMap<String, String> = hashMapOf(), listener: (String) -> Int = NOOP_CONSUMER): String {
        try {
            val executionDirectory = File(executionDirectoryPath)
            val commands = ArrayList<String>()
            commands.add("sh")
            commands.add("-c")
            commands.add(command)

            val pb = ProcessBuilder(commands)
            pb.directory(executionDirectory)
            pb.redirectErrorStream(true)
            pb.environment().putAll(env)
            Log.i("ExecUtils","Running: ${pb.command()} \n with env $env")

            val process = pb.start()
            val result = collectOutput(process.inputStream, listener)

            if (process.waitFor() != 0) {
                Log.e("UserLAnd","Failed to execute command ${pb.command()}\nstdout: $result")
            } else {
                Log.i("UserLAnd", "stdout: $result")
            }
            return result
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Int): String {
        val out = StringBuilder()
        val buf: BufferedReader = inputStream.bufferedReader(UTF_8)
        var line: String? = buf.readLine()
        do {
            if (line != null) {
                out.append(line).append("\n")
                listener(line)
            }
            line = buf.readLine()
        } while (line != null)
        return out.toString()
    }

//    fun execLocalAsync(commandDir: File, command: String, env: HashMap<String, String>, listener: (String) -> Int): ProcessWrapper {
//        val commands = ArrayList<String>()
//        commands.add("sh")
//        commands.add("-c")
//        commands.add(command)
//
//        val pb = ProcessBuilder(commands)
//        pb.redirectErrorStream(true)
//        pb.environment().putAll(env)
//        pb.directory(commandDir)
//
//        Log.i("ExecUtils","Running: ${pb.command()} \n with env $env")
//        return ProcessWrapper(pb, listener)
//    }

}