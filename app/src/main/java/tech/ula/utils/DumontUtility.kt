package tech.ula.utils

import com.squareup.moshi.Moshi
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class DumontUtility {

    private val baseUrl = "http://ef09ce2d.ngrok.io"
    var client = OkHttpClient()

    val JSON = MediaType.parse("application/json; charset=utf-8")

    private fun loginAndGetBearerToken(email: String, password: String): String {
        val loginUrl = "$baseUrl/login"
        val json = JSONObject()
        json.put("email", "email")
        json.put("password", password)
        val jsonString = json.toString()
        return postJSON(loginUrl, jsonString)
    }

    private fun postJSON(url: String, json: String): String {
        val body = RequestBody.create(JSON, json)
        val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

        val response = client.newCall(request).execute()
        val jsonString = response.body()?.string() ?: ""

        if (jsonString.isNotEmpty()) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter = moshi.adapter<LoginToken>(LoginToken::class.java)
            val credentials = jsonAdapter.fromJson(jsonString)
            return credentials?.access_token.toString()
        }

        return jsonString
    }
}

class LoginToken {
    var access_token: String = ""
}