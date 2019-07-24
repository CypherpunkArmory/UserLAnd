package tech.ula.model.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ula.model.entities.App
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL

class GithubAppsFetcher(
    private val applicationFilesDir: String,
    private val httpStream: HttpStream = HttpStream(),
    private val logger: Logger = SentryLogger()
) {

    // Allows destructing of the list of application elements
    operator fun <T> List<T>.component6() = get(5)
    operator fun <T> List<T>.component7() = get(6)

    private val branch = "master"// Base off different support branches for testing.
    private val baseUrl = "://github.com/CypherpunkArmory/UserLAnd-Assets-Support/raw/$branch/apps"
    private var protocol = "https"

    @Throws(IOException::class)
    suspend fun fetchAppsList(): List<App> = withContext(Dispatchers.IO) {
        val appsList = ArrayList<App>()

        val url = "$protocol$baseUrl/apps.txt"
        val numLinesToSkip = 1 // Skip first line defining schema
        return@withContext try {
            val reader = BufferedReader(InputStreamReader(httpStream.fromUrl(url)))
            reader.readLines().drop(numLinesToSkip).forEach {
                val (name, category, filesystemRequired, supportsCli, supportsGui, isPaidApp, version) =
                        it.toLowerCase().split(", ")
                appsList.add(
                        App(name, category, filesystemRequired, supportsCli.toBoolean(), supportsGui.toBoolean(),
                                isPaidApp.toBoolean(), version.toLong()))
            }
            reader.close()
            appsList.toList()
        } catch (err: Exception) {
            val exception = IOException("Error getting apps list")
            logger.addExceptionBreadcrumb(exception)
            throw exception
        }
    }

    suspend fun fetchAppIcon(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.png"
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile!!.mkdirs()
        file.createNewFile()
        val url = "$protocol$baseUrl/$directoryAndFilename"
        val inputStream = httpStream.fromUrl(url)
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = file.outputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream) // Int arg quality is ignored for lossless formats
        inputStream.close()
        outputStream.close()
    }

    suspend fun fetchAppDescription(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.txt"
        val url = "$protocol$baseUrl/$directoryAndFilename"
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile!!.mkdirs()
        file.createNewFile()
        val contents = URL(url).readText()
        file.writeText(contents)
    }

    suspend fun fetchAppScript(app: App) = withContext(Dispatchers.IO) {
        val directoryAndFilename = "${app.name}/${app.name}.sh"
        val url = "$protocol$baseUrl/$directoryAndFilename"
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile!!.mkdirs()
        file.createNewFile()
        val contents = URL(url).readText()
        file.writeText(contents)
    }
}