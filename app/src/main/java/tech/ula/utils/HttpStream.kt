package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.* // ktlint-disable no-wildcard-imports
import java.net.HttpURLConnection
import java.net.URL

class HttpStream {
    // TODO this function should be made private and usages be reworked to match other public functions
    fun fromUrl(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        return conn.inputStream
    }

    @Throws(IOException::class)
    suspend fun toLines(url: String): List<String> = withContext(Dispatchers.IO) {
        val inputStream = fromUrl(url)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()
        reader.close()
        return@withContext lines
    }

    @Throws(IOException::class)
    suspend fun toFile(url: String, file: File) = withContext(Dispatchers.IO) {
        file.parentFile!!.mkdirs()
        file.createNewFile()
        val inputStream = fromUrl(url)
        val outputStream = file.outputStream()
        outputStream.write(inputStream.readBytes())
        inputStream.close()
        outputStream.close()
    }

    @Throws(IOException::class)
    suspend fun toTextFile(url: String, file: File) = withContext(Dispatchers.IO) {
        file.parentFile!!.mkdirs()
        file.createNewFile()
        val contents = URL(url).readText()
        file.writeText(contents)
    }
}
