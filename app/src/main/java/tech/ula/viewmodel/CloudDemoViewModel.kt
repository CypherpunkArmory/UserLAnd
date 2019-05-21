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
import org.json.JSONObject
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

sealed class CloudState
sealed class LoginResult : CloudState() {
    object Success : LoginResult()
    object Failure : LoginResult()
}
sealed class ConnectResult : CloudState() {
    object Success : ConnectResult()
    object BoxCreateFailure : ConnectResult()
    object ConnectFailure : ConnectResult()
}

@JsonClass(generateAdapter = true)
internal data class LoginResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "expires-in") val expiresIn: Int,
        @Json(name = "refresh_token") val refreshToken: String,
        @Json(name = "token_type")val tokenType: String
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

    private val cloudState = MutableLiveData<CloudState>()

    fun getCloudState(): LiveData<CloudState> {
        return cloudState
    }

    fun handleLoginClick(email: String, password: String) = launch { withContext(Dispatchers.IO) {
        val json = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()

        val body = RequestBody.create(jsonType, json)
        val request = Request.Builder()
                .url("$baseUrl/login")
                .post(body)
                .build()

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

        val adapter = Moshi.Builder().build().adapter<LoginResponse>(LoginResponse::class.java)
        val loginResponse = adapter.fromJson(response.body()!!.source())
        cloudState.postValue(LoginResult.Success)

    } }

    fun handleConnectClick() {
        cloudState.postValue(LoginResult.Failure)
    }
}

class CloudDemoViewModelFactory : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return CloudDemoViewModel() as T
    }
}