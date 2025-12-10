package tech.ula.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.* // ktlint-disable no-wildcard-imports
import java.net.HttpURLConnection
import java.net.URL

class HttpStream {
    @Throws(IOException::class)
    suspend fun toLines(url: String): List<String> = withContext(Dispatchers.IO) {
        return@withContext fromUrl(url) { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLines()
            }
        }
    }

    @Throws(IOException::class)
    suspend fun toFile(url: String, file: File) = withContext(Dispatchers.IO) {
        ensureDestination(file)
        fromUrl(url) { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    @Throws(IOException::class)
    suspend fun toTextFile(url: String, file: File) = withContext(Dispatchers.IO) {
        ensureDestination(file)
        val contents = URL(url).readText()
        file.writeText(contents)
    }

    private fun ensureDestination(file: File) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
    }

    private fun <T> fromUrl(url: String, block: (InputStream) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        return connection.inputStream.use { inputStream ->
            block(inputStream)
        }.also {
            connection.disconnect()
        }
    }
}
