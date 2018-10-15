package tech.ula.ui

import android.os.Bundle
import android.os.StrictMode
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.squareup.moshi.Moshi
import kotlinx.android.synthetic.main.frag_cloud.*
import kotlinx.coroutines.experimental.launch
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import tech.ula.R
import java.io.IOException
import java.net.URL

class CloudFragment : Fragment() {

    private var client = OkHttpClient()

    val baseUrl = "http://ef09ce2d.ngrok.io"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        return inflater.inflate(R.layout.frag_cloud, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dumont_login.setOnClickListener {
            text_bearer_token.text = getAccessTokenByLogin()
        }

        dumont_boxes.setOnClickListener {
            Toast.makeText(activity, "BOXES", Toast.LENGTH_SHORT).show()
        }
    }

    private fun run() {
        val request = Request.Builder()
                .url(baseUrl)
                .build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            text_bearer_token.text = response.body()?.string()
        } finally {

        }

    }

    val JSON = MediaType.parse("application/json; charset=utf-8")


    private fun getAccessTokenByLogin(): String {
        val loginUrl = "$baseUrl/login"
        val json = JSONObject()
        json.put("email", "thomas")
        json.put("password", "123123")
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