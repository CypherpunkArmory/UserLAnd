package tech.ula.utils

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpStream {
    fun fromUrl(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        return conn.inputStream
    }
}
