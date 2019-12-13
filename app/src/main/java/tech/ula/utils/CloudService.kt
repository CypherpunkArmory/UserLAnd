package tech.ula.utils

import android.system.Os
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import tech.ula.model.entities.Session
import java.io.File

@JsonClass(generateAdapter = true)
internal data class LoginResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires-in") val expiresIn: Int,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "token_type") val tokenType: String
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

    companion object {
        var accountEmail = ""
        var accountPassword = ""
        var filesPath = ""
    }

    private val baseUrl = "https://api.userland.tech/"
    private val jsonType = MediaType.parse("application/json")
    private var accessToken = ""
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()
    private var publicKey = ""

    val SUCCESS = 0
    val LOGIN_FAILURE = -1
    val BOX_FAILURE = -2
    val LIST_FAILURE = -3
    val DELETE_FAILURE = -4

    fun createBox(session: Session): Int {
        // TODO: this symlink stuff should be somewhere else
        val busyboxFile = File(filesPath, "/support/busybox")
        val shFile = File(filesPath, "/support/sh")
        if (shFile.exists()) shFile.delete()
        Os.symlink(busyboxFile.path, shFile.path)

        var result = login()
        if (result != 0)
            return result
        createKeys()
        return box(session)
    }

    fun stopBox(session: Session): Int {
        var result = login()
        if (result != 0)
            return result
        return delete()
    }

    fun isActive(session: Session): Boolean {
        var result = login()
        if (result != 0)
            return session.active
        return (find() == session.pid.toInt())
    }

    fun isBoxRunning(session: Session): Boolean {
        val env = hashMapOf(
            "SHELL" to "$filesPath/support/sh",
            "LD_LIBRARY_PATH" to "$filesPath/support"
        )

        val proxyCommand = "$filesPath/support/ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $filesPath/sshkey.priv -W %h:%p punch@api.userland.tech"
        val command = "$filesPath/support/ssh -o ProxyCommand=\"$proxyCommand\" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i $filesPath/sshkey.priv -p ${session.port} -t -A userland@${session.ip} '/bin/ls'"
        val sshArgs = listOf("$filesPath/support/busybox", "sh", "-c", command)

        val processBuilder = ProcessBuilder(sshArgs)
        processBuilder.directory(File(filesPath))
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        try {
            val process = processBuilder.start()
            if (process.waitFor() != 0)
                return false
        } catch (err: Exception) {
            return false
        }
        return true
    }

    private fun createKeys() {
        val jsch = JSch()
        val kpair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 1024)
        kpair.writePrivateKey(filesPath + "/sshkey.priv")
        kpair.writePublicKey(filesPath + "/sshkey.pub", "bogus@bogus.com")
        publicKey = File(filesPath + "/sshkey.pub").readText(Charsets.UTF_8).trim()
        val privateKey = File(filesPath + "/sshkey.priv").readText(Charsets.UTF_8).trim()
        File(filesPath + "/sshkey.priv").setExecutable(false, false)
        File(filesPath + "/sshkey.priv").setReadable(false, false)
        File(filesPath + "/sshkey.priv").setWritable(false, false)
        File(filesPath + "/sshkey.priv").setReadable(true, true)
        File(filesPath + "/sshkey.priv").setWritable(true, true)
    }

    private fun login(): Int {
        val request = createLoginRequest()

        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            return LOGIN_FAILURE
        }
        if (!response.isSuccessful) {
            return LOGIN_FAILURE
        }

        val adapter = moshi.adapter(LoginResponse::class.java)
        val loginResponse = adapter.fromJson(response.body()!!.source())!!
        accessToken = loginResponse.accessToken

        return SUCCESS
    }

    private fun box(session: Session): Int {
        if (accessToken == "") {
            return LOGIN_FAILURE
        }

        val request = createBoxCreateRequest(session)

        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            return BOX_FAILURE
        }
        if (!response.isSuccessful) {
            return BOX_FAILURE
        }

        val adapter = moshi.adapter(CreateResponse::class.java)
        val createResponse = try {
            adapter.fromJson(response.body()!!.source())!!
        } catch (err: Exception) {
            return BOX_FAILURE
        }
        session.ip = createResponse.data.attributes.ipAddress
        session.port = createResponse.data.attributes.sshPort.toLong()
        return createResponse.data.id
    }

    fun find(): Int {
        if (accessToken == "") {
            return LOGIN_FAILURE
        }

        val request = createListRequest()
        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            return LIST_FAILURE
        }
        if (!response.isSuccessful) {
            return LIST_FAILURE
        }

        val listAdapter = moshi.adapter(ListResponse::class.java)
        val id = try {
            // TODO: this should find a specific box and kill it, this will be needed when we support multiple
            listAdapter.fromJson(response.body()!!.source())!!.data.first().id
        } catch (err: Exception) {
            return LIST_FAILURE
        }
        return id
    }

    fun delete(): Int {
        if (accessToken == "") {
            return LOGIN_FAILURE
        }

        val request = createListRequest()
        val response = try {
            client.newCall(request).execute()
        } catch (err: Exception) {
            return LIST_FAILURE
        }
        if (!response.isSuccessful) {
            return LIST_FAILURE
        }

        val listAdapter = moshi.adapter(ListResponse::class.java)
        val id = try {
            // TODO: this should find a specific box and kill it, this will be needed when we support multiple
            listAdapter.fromJson(response.body()!!.source())!!.data.first().id
        } catch (err: Exception) {
            return LIST_FAILURE
        }

        val deleteRequest = createDeleteRequest(id)
        val deleteResponse = try {
            client.newCall(deleteRequest).execute()
        } catch (err: Exception) {
            return DELETE_FAILURE
        }
        if (!deleteResponse.isSuccessful) {
            return DELETE_FAILURE
        }

        return SUCCESS
    }

    private fun createLoginRequest(): Request {
        val json = """
            {
                "email": "$accountEmail",
                "password": "$accountPassword"
            }
        """.trimIndent()

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
            .url("$baseUrl/login")
            .post(body)
            .build()
    }

    private fun createBoxCreateRequest(session: Session): Request? {
        val sshKey = publicKey

        val json = """
            {
              "data": {
                "type": "box",
                "attributes": {
                  "port": ["http"],
                  "image": "${session.filesystemType.toLowerCase()}",
                  "sshKey": "$sshKey"
                }
              }
            }
        """.trimIndent()

        val body = RequestBody.create(jsonType, json)
        return Request.Builder()
            .url("$baseUrl/boxes")
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
    }

    private fun createListRequest(): Request {
        return Request.Builder()
            .url("$baseUrl/boxes")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
    }

    private fun createDeleteRequest(id: Int): Request {
        return Request.Builder()
            .url("$baseUrl/boxes/$id")
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()
    }
}