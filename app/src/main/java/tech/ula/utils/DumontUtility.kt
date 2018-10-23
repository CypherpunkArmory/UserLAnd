package tech.ula.utils

import com.squareup.moshi.Moshi
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class DumontUtility {
    private val baseUrl = "http://33ecc4bc.ngrok.io"
    private var client = OkHttpClient()
    private val jsonType = MediaType.parse("application/json; charset=utf-8")

    fun loginAndGetBearerToken(email: String, password: String): String {
        val requestType = Login(url = "$baseUrl/login")
        val jsonString = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
        return postJSON(requestType, jsonString)
    }

    private fun getBoxes(): String {
        return ""
    }

    private fun postJSON(requestType: RequestType, json: String): String {
        val body = RequestBody.create(jsonType, json)
        val request = Request.Builder()
                .url(requestType.urlPath)
                .post(body)
                .build()

        val response = client.newCall(request).execute()
        val jsonString = response.body()?.string() ?: ""

        if (jsonString.isNotEmpty()) {
            return when (requestType) {
                is Login -> {
                    val credentials = requestType.jsonAdapter.fromJson(jsonString) as Credentials
                    credentials.access_token
                }
                is GetBoxes -> { "" }
            }
        }

        return jsonString
    }
}

class Credentials {
    var access_token: String = ""
}

sealed class RequestType(val urlPath: String) { val moshi = Moshi.Builder().build() }
data class Login(val url: String) : RequestType(urlPath = url) {
    val jsonAdapter = moshi.adapter<Credentials>(Credentials::class.java)
}

data class GetBoxes(val url: String) : RequestType(urlPath = url)