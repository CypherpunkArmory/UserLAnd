package tech.userland.userland

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import kotlin.concurrent.thread

class ProcessWrapper(val pb: ProcessBuilder, stdOutListener: (String) -> Int) {

    val process: Process
    val stdOutReader: Thread
    var exitCode: Int = -1

    init {
        process = pb.start()
        stdOutReader = readThread("OUT:", process.inputStream, stdOutListener)
        //Add a shutdown hook so that if the JVM is stopped the os process is also terminated
        Runtime.getRuntime().addShutdownHook(Thread() {
            stop(5)
        })
    }

    fun waitFor(): Int {
        return process.waitFor()
    }

    fun stop(timeoutSecs: Long) {
        if (isRunning()) {
            try {
                process.destroy()
                process.waitFor()
                exitCode = process.exitValue()
            } catch (e: Exception) {
                exitCode = -1
            }
        }
    }

    fun isRunning(): Boolean {
        try {
            var exitValue = process.exitValue()
            return false
        } catch (e: Exception) {
            return true
        }
    }

    private fun readThread(prefix: String, stream: InputStream, listener: (String) -> Int): Thread {
        return thread {
            try {
                val buf: BufferedReader = stream.bufferedReader()
                var line: String? = buf.readLine()
                do {
                    if (line != null) {
                        listener("$line")
                    }
                    line = buf.readLine()
                } while (line != null)
            } catch (e: Exception) {
                Log.e("ProcessWrapper", "Unexpected IO error:" + e.message)
            }
        }
    }

    fun command(): MutableList<String>? {
        return pb.command()
    }
}