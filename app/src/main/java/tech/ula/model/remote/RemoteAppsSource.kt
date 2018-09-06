package tech.ula.model.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import tech.ula.model.entities.App
import tech.ula.utils.ConnectionUtility
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.SSLHandshakeException

interface RemoteAppsSource {
    suspend fun fetchAppsList(): List<App>

    suspend fun fetchAppIcon(app: App)

    suspend fun fetchAppDescription(app: App)

    suspend fun fetchAppScript(app: App)
}

class GithubAppsFetcher(private val applicationFilesDir: String, private val connectionUtility: ConnectionUtility = ConnectionUtility()) : RemoteAppsSource {

    // Allows destructing of the list of application elements
    operator fun <T> List<T>.component6() = get(5)

    private val baseUrl = "://github.com/CypherpunkArmory/UserLAnd-Assets-Support/raw/staging/applications" // TODO change to master
    private var protocol = "https"

    @Throws
    override suspend fun fetchAppsList(): List<App> {
        val appsList = ArrayList<App>()

        val url = "$protocol$baseUrl/applications.txt"
        val numLinesToSkip = 1 // Skip first line defining schema
        return try {
            val reader = BufferedReader(InputStreamReader(connectionUtility.getUrlInputStream(url)))
            reader.readLines().drop(numLinesToSkip).forEach {
                val (name, category, supportsCli, supportsGui, isPaidApp, version) =
                        it.split(", ")
                appsList.add(
                        App(name, category, supportsCli.toBoolean(), supportsGui.toBoolean(),
                                isPaidApp.toBoolean(), version.toLong()))
            }
            reader.close()
            appsList.toList()
        } catch (err: SSLHandshakeException) {
            protocol = "http"
            fetchAppsList()
        } catch (err: Exception) {
            throw object : Exception("Error getting apps list") {}
        }
    }

    @Throws
    override suspend fun fetchAppIcon(app: App) {
        val directoryAndFilename = "${app.name}/${app.name}.png"
        val url = "$protocol$baseUrl/$directoryAndFilename".toLowerCase()
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile.mkdirs()
        file.createNewFile()
        val inputStream = connectionUtility.getUrlInputStream(url)
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = file.outputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream) // Int arg quality is ignored for lossless formats
        inputStream.close()
        outputStream.close()
    }

    override suspend fun fetchAppDescription(app: App) {
        val directoryAndFilename = "${app.name}/${app.name}.txt"
        val url = "$protocol$baseUrl/$directoryAndFilename".toLowerCase() // TODO figure out what to do here
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile.mkdirs()
        file.createNewFile()
        val contents = URL(url).readText()
        file.writeText(contents)
    }

    @Throws
    override suspend fun fetchAppScript(app: App) {
        val directoryAndFilename = "${app.name}/${app.name}.sh"
        val url = "$protocol$baseUrl/$directoryAndFilename".toLowerCase()
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile.mkdirs()
        file.createNewFile()
        val contents = URL(url).readText()
        file.writeText(contents)
    }
}