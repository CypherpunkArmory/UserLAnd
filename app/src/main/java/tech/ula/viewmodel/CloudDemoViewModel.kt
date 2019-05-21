package tech.ula.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

sealed class CloudState
sealed class LoginResult : CloudState() {
    object InProgress: LoginResult()
    object Success : LoginResult()
    object Failure : LoginResult()
}
sealed class ConnectResult : CloudState() {
    object InProgress : ConnectResult()
    object Success : ConnectResult()
    object PublicKeyNotFound : ConnectResult()
    object RequestFailed : ConnectResult()
    object PrivateKeyNotFound : ConnectResult()
    object BoxCreateFailure : ConnectResult()
    object ConnectFailure : ConnectResult()
}

@JsonClass(generateAdapter = true)
private data class LoginResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "expires-in") val expiresIn: Int,
        @Json(name = "refresh_token") val refreshToken: String,
        @Json(name = "token_type")val tokenType: String
)

@JsonClass(generateAdapter = true)
private data class CreateResponse(
        val data: CreateData
)

@JsonClass(generateAdapter = true)
private data class CreateData(
        val type: String,
        val attributes: CreateAttributes,
        val id: Int
)

@JsonClass(generateAdapter = true)
private data class CreateAttributes(
        val port: List<String>,
        val sshPort: Int,
        val ipAddress: String
)

class CloudDemoViewModel : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val client = OkHttpClient()
    private val baseUrl = "https://api.ula.orbtestenv.net"
    private val jsonType = MediaType.parse("application/json")
    private val moshi = Moshi.Builder().build()
    private var accessToken = ""

    private val cloudState = MutableLiveData<CloudState>()

    fun getCloudState(): LiveData<CloudState> {
        return cloudState
    }

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

        val adapter = moshi.adapter<LoginResponse>(LoginResponse::class.java)
        val loginResponse = adapter.fromJson(response.body()!!.source())!!
        accessToken = loginResponse.accessToken
        cloudState.postValue(LoginResult.Success)
    } }

    fun handleConnectClick(filesDir: File) = launch { withContext(Dispatchers.IO) {
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
            cloudState.postValue(ConnectResult.RequestFailed)
            return@withContext
        }

        val adapter = moshi.adapter<CreateResponse>(CreateResponse::class.java)
        // TODO null responses should be handled for demo
        val createResponse = adapter.fromJson(response.body()!!.source())!!
        val ipAddress = createResponse.data.attributes.ipAddress
        val port = createResponse.data.attributes.port
        cloudState.postValue(ConnectResult.Success)
    } }

    private fun createLoginRequest(email: String, password: String): Request {
//        val json = JSONObject()
//                .put("email", email)
//                .put("password", password)
//                .toString()
        val json = "{\"email:\" \"$email\", \"password:\" \"$password\"}"

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
                .url("$baseUrl/login")
                .post(body)
                .build()
    }

    private fun createBoxCreateRequest(filesDir: File): Request? {
//        val attributesJson = JSONObject()
//                .put("port", JSONArray().put("http"))
//                .put("sshKey")
//        val dataJson = JSONObject()
//                .put("type", "tunnel")
//                .put("attributes")
//        val json = JSONObject()
//                .put("data", dataJson)
//                .toString()

        val sshKeyFile = File(filesDir, "publicKey")
        if (!sshKeyFile.exists()) {
            cloudState.postValue(ConnectResult.PublicKeyNotFound)
            return null
        }
        val sshKey = sshKeyFile.readText()
        val json = """
            {
                "data": {
                    "type": "tunnel",
                    "attributes": {
                        "port": ["http"],
                        "sshKey": "$sshKey"
                    }
                }
            }
        """.trimIndent()

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
                .url("$baseUrl/login")
                .post(body)
                .build()
    }
}

class CloudDemoViewModelFactory : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return CloudDemoViewModel() as T
    }
}