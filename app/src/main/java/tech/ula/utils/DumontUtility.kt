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
        val loginUrl = "$baseUrl/login"
        val jsonString = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
        return postJSON(loginUrl, jsonString)
    }

    private fun postJSON(url: String, json: String): String {
        val body = RequestBody.create(jsonType, json)
        val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

        val response = client.newCall(request).execute()
        val jsonString = response.body()?.string() ?: ""

        if (jsonString.isNotEmpty()) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter = moshi.adapter<Credentials>(Credentials::class.java)
            val credentials = jsonAdapter.fromJson(jsonString)
            return credentials?.access_token.toString()
        }

        return jsonString
    }
}

class Credentials {
    var access_token: String = ""
}