package tech.ula.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

// TODO This class represents an excellent opportunity for integration tests.
class HttpStream {
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
    suspend fun toPngFile(url: String, file: File) = withContext(Dispatchers.IO) {
        file.parentFile!!.mkdirs()
        file.createNewFile()
        val inputStream = fromUrl(url)
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = file.outputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream) // Int arg quality is ignored for lossless formats
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
