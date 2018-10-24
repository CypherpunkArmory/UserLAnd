package tech.ula.utils

import com.squareup.moshi.Moshi
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class DumontUtility {
    private val baseUrl = "http://9679867b.ngrok.io"
    private var client = OkHttpClient()
    private val jsonType = MediaType.parse("application/json; charset=utf-8")

    fun loginAndGetBearerToken(email: String, password: String): String {
        val jsonString = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()

        val body = RequestBody.create(jsonType, jsonString)
        val request = Request.Builder()
                .url("$baseUrl/login")
                .post(body)
                .build()

        val requestType = Login(request)
        return sendRequest(requestType)
    }

    fun getBoxes(bearerToken: String): String {
        val request = Request.Builder()
                .url("$baseUrl/boxes")
                .get()
                .addHeader("Authorization: Bearer ", bearerToken)
                .build()

        val requestType = GetBoxes(request)
        return sendRequest(requestType)
    }

    private fun makeBox(bearerToken: String): String {
        val body = RequestBody.create(jsonType, "")
        val request = Request.Builder()
                .url("$baseUrl/boxes")
                .addHeader("Authorization: Bearer ", bearerToken)
                .post(body)
                .build()

        val requestType = GetBoxes(request)
        return sendRequest(requestType)
    }

    private fun sendRequest(requestType: RequestType): String {
        val request = requestType.httpRequest
        val response = client.newCall(request).execute()
        val jsonString = response.body()?.string() ?: ""

        if (jsonString.isNotEmpty()) {
            return when (requestType) {
                is Login -> {
                    val credentials = requestType.jsonAdapter.fromJson(jsonString) as Credentials
                    credentials.access_token
                }
                is GetBoxes -> {
                    val boxes = requestType.jsonAdapter.fromJson(jsonString) as Boxes
                    boxes.data.first().attributes.name
                }
                is MakeBox -> {
                    val newBox = requestType.jsonAdapter.fromJson(jsonString) as Boxes
                    newBox.data.first().attributes.ip
                }
            }
        }

        return jsonString
    }
}

class Credentials {
    val access_token: String = ""
}

class Box {
    val type: String = ""
    val attributes: BoxAttributes = BoxAttributes()
    val id: String = ""
}

class BoxAttributes {
    val name: String = ""
    val ip: String = ""

}

class Boxes {
    val data: List<Box> = listOf()
}

sealed class RequestType(val httpRequest: Request) {
    val moshi = Moshi.Builder().build()
}

data class Login(val request: Request) : RequestType(httpRequest = request) {
    val jsonAdapter = moshi.adapter<Credentials>(Credentials::class.java)
}

data class GetBoxes(val request: Request) : RequestType(httpRequest = request) {
    val jsonAdapter = moshi.adapter<Credentials>(Boxes::class.java)
}

data class MakeBox(val request: Request) : RequestType(httpRequest = request) {
    val jsonAdapter = moshi.adapter<Credentials>(Boxes::class.java)
}