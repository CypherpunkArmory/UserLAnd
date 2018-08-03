package tech.ula.utils

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import javax.net.ssl.SSLHandshakeException

class NetworkUtility(private val connectivityManager: ConnectivityManager, private val connectionUtility: ConnectionUtility) {

    fun networkIsActive(): Boolean {
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        activeNetworkInfo?.let {
            return true
        }
        return false
    }

    fun wifiIsEnabled(): Boolean {
        for (network in connectivityManager.allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
        }
        return false
    }

    fun httpsIsAccessible(): Boolean {
        val url = "https://www.google.com"
        return try {
            connectionUtility.getUrlConnection(url)
            true
        }
        catch (err: SSLHandshakeException) {
            false
        }
    }
}