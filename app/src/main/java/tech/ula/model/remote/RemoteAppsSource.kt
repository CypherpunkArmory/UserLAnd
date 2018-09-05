package tech.ula.model.remote

import tech.ula.model.entities.App
import tech.ula.utils.ConnectionUtility
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.net.ssl.SSLHandshakeException

interface RemoteAppsSource {
    suspend fun fetchAppsList(): List<App>

    suspend fun fetchAppIcon(): File

    suspend fun fetchAppDescription(): String

    suspend fun fetchAppScript(): File
}

class GithubAppsFetcher(private val connectionUtility: ConnectionUtility = ConnectionUtility()) : RemoteAppsSource {

    // Allows destructing of the list of application elements
    operator fun <T> List<T>.component6() = get(5)

    private val baseUrl = "://github.com/CypherpunkArmory/UserLAnd-Assets-Support/raw/staging" // TODO change to master
    private var protocol = "https"

    @Throws
    override suspend fun fetchAppsList(): List<App> {
        val appsList = ArrayList<App>()

        val url = "$protocol$baseUrl/applications/applications.txt"
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

    override suspend fun fetchAppIcon(): File {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun fetchAppDescription(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun fetchAppScript(): File {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}