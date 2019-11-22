package tech.ula.utils

import android.system.Os
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.lang.Exception

@JsonClass(generateAdapter = true)
internal data class LoginResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "expires-in") val expiresIn: Int,
        @Json(name = "refresh_token") val refreshToken: String,
        @Json(name = "token_type")val tokenType: String
)

@JsonClass(generateAdapter = true)
internal data class CreateResponse(
        val data: CreateData
)

@JsonClass(generateAdapter = true)
internal data class CreateData(
        val type: String,
        val attributes: CreateAttributes,
        val id: Int
)

@JsonClass(generateAdapter = true)
internal data class CreateAttributes(
        val sshPort: Int,
        val ipAddress: String
)

@JsonClass(generateAdapter = true)
internal data class ListResponse(val data: List<TunnelData>)

@JsonClass(generateAdapter = true)
internal data class TunnelData(val id: Int)

class CloudService {

    private val baseUrl = "https://api.userland.tech/"
    private val jsonType = MediaType.parse("application/json")
    private var accessToken = ""
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()
    private val publicKey ="hi"

    /*

    fun handleLoginClick(email: String, password: String) = launch { withContext(Dispatchers.IO) {
        cloudState.postValue(LoginResult.InProgress)

        val request = createLoginRequest(email, password)

        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            cloudState.postValue(LoginResult.Failure)
            return@withContext
        }
        if (!response.isSuccessful) {
            cloudState.postValue(LoginResult.Failure)
            return@withContext
        }

        val adapter = moshi.adapter(LoginResponse::class.java)
        val loginResponse = adapter.fromJson(response.body()!!.source())!!
        accessToken = loginResponse.accessToken
        cloudState.postValue(LoginResult.Success)
    } }

    fun handleConnectClick(filesDir: File) = launch { withContext(Dispatchers.IO) {
        val busyboxFile = File(filesDir, "/support/busybox")
        if (!busyboxFile.exists()) {
            cloudState.postValue(ConnectResult.BusyboxMissing)
            return@withContext
        }

        try {
            val shFile = File(filesDir, "/support/sh")
            if (shFile.exists()) shFile.delete()
            Os.symlink(busyboxFile.path, shFile.path)
        } catch (err: Exception) {
            cloudState.postValue(ConnectResult.LinkFailed)
            return@withContext
        }

        if (accessToken == "") {
            cloudState.postValue(LoginResult.Failure)
            return@withContext
        }

        cloudState.postValue(ConnectResult.InProgress)
        val request = createBoxCreateRequest(filesDir) ?: return@withContext

        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            cloudState.postValue(ConnectResult.BoxCreateFailure)
            return@withContext
        }
        if (!response.isSuccessful) {
            cloudState.postValue(ConnectResult.RequestFailed(response.message()))
            return@withContext
        }

        val adapter = moshi.adapter(CreateResponse::class.java)
        val createResponse = try {
            adapter.fromJson(response.body()!!.source())!!
        } catch (err: NullPointerException) {
            cloudState.postValue(ConnectResult.NullResponseFromCreate)
            return@withContext
        }
        val ipAddress = createResponse.data.attributes.ipAddress
        val sshPort = createResponse.data.attributes.sshPort

        cloudState.postValue(ConnectResult.Success(ipAddress, sshPort))
    } }

    fun handleDeleteClick() = launch { withContext(Dispatchers.IO) {
        if (accessToken == "") {
            cloudState.postValue(LoginResult.Failure)
            return@withContext
        }

        cloudState.postValue(DeleteResult.InProgress)

        val request = createListRequest()
        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            cloudState.postValue(DeleteResult.ListRequestFailure)
            return@withContext
        }
        if (!response.isSuccessful) {
            cloudState.postValue(DeleteResult.ListResponseFailure(response.message()))
            return@withContext
        }

        val listAdapter = moshi.adapter(ListResponse::class.java)
        val id = try {
            listAdapter.fromJson(response.body()!!.source())!!.data.first().id
        } catch (err: NullPointerException) {
            cloudState.postValue(DeleteResult.NullResponseFromList)
            return@withContext
        }

        val deleteRequest = createDeleteRequest(id)
        val deleteResponse = try {
            client.newCall(deleteRequest).execute()
        } catch (err: Exception) {
            cloudState.postValue(DeleteResult.DeleteRequestFailure)
            return@withContext
        }
        if (!deleteResponse.isSuccessful) {
            cloudState.postValue(DeleteResult.DeleteResponseFailure(response.message()))
            return@withContext
        }

        cloudState.postValue(DeleteResult.Success(id))
    } }
    */

    private fun createLoginRequest(email: String, password: String): Request {
        val json = """
            {
                "email": "$email",
                "password": "$password"
            }
        """.trimIndent()

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
                .url("$baseUrl/login")
                .post(body)
                .build()
    }

    private fun createBoxCreateRequest(filesDir: File): Request? {
        val sshKey = publicKey

        val json = """
            {
              "data": {
                "type": "box",
                "attributes": {
                  "port": ["http"],
                  "sshKey": "$sshKey"
                }
              }
            }
        """.trimIndent()

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
                .url("$baseUrl/boxes")
                .post(body)
                .addHeader("Authorization","Bearer $accessToken")
                .build()
    }

    private fun createListRequest(): Request {
        return Request.Builder()
                .url("$baseUrl/boxes")
                .addHeader("Authorization","Bearer $accessToken")
                .get()
                .build()
    }

    private fun createDeleteRequest(id: Int): Request {
        return Request.Builder()
                .url("$baseUrl/boxes/$id")
                .addHeader("Authorization","Bearer $accessToken")
                .delete()
                .build()
    }

}