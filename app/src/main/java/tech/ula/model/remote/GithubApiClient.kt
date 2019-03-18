package tech.ula.model.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.ula.utils.BuildWrapper
import java.io.IOException

class GithubApiClient(private val client: OkHttpClient = OkHttpClient()) {
    private val latestResults: HashMap<String, ReleasesResponse?> = hashMapOf()

    @Throws()
    suspend fun getAssetsListDownloadUrl(repo: String): String = withContext(Dispatchers.IO) {
        val result = latestResults[repo] ?: queryLatestRelease(repo)

        return@withContext result.assets.find { it.name == "assets.txt" }!!.downloadUrl
    }

    @Throws()
    suspend fun getLatestReleaseVersion(repo: String): String = withContext(Dispatchers.IO) {
        val result = latestResults[repo] ?: queryLatestRelease(repo)

        return@withContext result.tag
    }

    @Throws()
    suspend fun getAssetEndpoint(
            assetType: String,
            repo: String,
            buildWrapper: BuildWrapper = BuildWrapper()
    ): String = withContext(Dispatchers.IO) {
        val result = latestResults[repo] ?: queryLatestRelease(repo)
        val assetName = "${buildWrapper.getArchType()}-$assetType"

        return@withContext result.assets.find { it.name == assetName }!!.downloadUrl
    }

    @Throws()
    private suspend fun queryLatestRelease(repo: String): ReleasesResponse = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/CypherpunkArmory/UserLAnd-Assets-$repo/releases/latest"
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter<ReleasesResponse>(ReleasesResponse::class.java)
        val request = Request.Builder()
                .url(url)
                .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code: $response")

        val result = adapter.fromJson(response.body()!!.source())!!
        latestResults[repo] = result // Memoize results
        return@withContext result
    }

    @JsonClass(generateAdapter = true)
    internal data class ReleasesResponse(
            val url: String,
            val name: String,
            @Json(name = "tag_name") val tag: String,
            val assets: List<GithubAsset>
    )

    @JsonClass(generateAdapter = true)
    internal data class GithubAsset(
            val url: String,
            val name: String,
            @Json(name = "browser_download_url") val downloadUrl: String
    )
}