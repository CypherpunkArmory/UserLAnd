package tech.ula.model.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import tech.ula.model.entities.App
import tech.ula.utils.AcraWrapper
import tech.ula.utils.ConnectionUtility
import tech.ula.utils.getBranchToDownloadAssetsFrom
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL

interface RemoteAppsSource {
    fun getHostname(): String

    suspend fun fetchAppsList(): List<App>

    suspend fun fetchAppIcon(app: App)

    suspend fun fetchAppDescription(app: App)

    suspend fun fetchAppScript(app: App)
}

class GithubAppsFetcher(
    private val applicationFilesDir: String,
    private val connectionUtility: ConnectionUtility = ConnectionUtility(),
    private val acraWrapper: AcraWrapper = AcraWrapper()
) : RemoteAppsSource {

    // Allows destructing of the list of application elements
    operator fun <T> List<T>.component6() = get(5)
    operator fun <T> List<T>.component7() = get(6)

    private val assetTypeForApps = "apps"
    private val branch = getBranchToDownloadAssetsFrom(assetTypeForApps) // Base off different support branches for testing.
    private val baseUrl = "://github.com/CypherpunkArmory/UserLAnd-Assets-Support/raw/$branch/apps"
    private var protocol = "https"
    private val hostname = "$protocol$baseUrl"

    override fun getHostname(): String { return hostname }

    @Throws(IOException::class)
    override suspend fun fetchAppsList(): List<App> {
        val appsList = ArrayList<App>()

        val url = "$protocol$baseUrl/apps.txt"
        val numLinesToSkip = 1 // Skip first line defining schema
        return try {
            val reader = BufferedReader(InputStreamReader(connectionUtility.getUrlInputStream(url)))
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
            acraWrapper.logAndThrow(IOException("Error getting apps list"))
            listOf() // Never reaches here
        }
    }

    override suspend fun fetchAppIcon(app: App) {
        val directoryAndFilename = "${app.name}/${app.name}.png"
        val url = "$protocol$baseUrl/$directoryAndFilename"
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
        val url = "$protocol$baseUrl/$directoryAndFilename"
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile.mkdirs()
        file.createNewFile()
        val contents = URL(url).readText()
        file.writeText(contents)
    }

    override suspend fun fetchAppScript(app: App) {
        val directoryAndFilename = "${app.name}/${app.name}.sh"
        val url = "$protocol$baseUrl/$directoryAndFilename"
        val file = File("$applicationFilesDir/apps/$directoryAndFilename")
        file.parentFile.mkdirs()
        file.createNewFile()
        val contents = URL(url).readText()
        file.writeText(contents)
    }
}