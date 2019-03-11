package tech.ula.model.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class GithubApiClient(private val client: OkHttpClient = OkHttpClient()) {
    @Throws()
    suspend fun getAssetsListDownloadUrl(repo: String): String = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/CypherpunkArmory/UserLAnd-Assets-$repo/releases/latest"
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter<ReleasesResponse>(ReleasesResponse::class.java)
        val request = Request.Builder()
                .url(url)
                .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code: $response")

        // TODO figure out a way to memoize the latest tag/downloadurls without introducing circular dependency on assetrepo
        val result = adapter.fromJson(response.body()!!.source())
        return@withContext result!!.assets.find { it.name == "assets.txt" }!!.downloadUrl
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