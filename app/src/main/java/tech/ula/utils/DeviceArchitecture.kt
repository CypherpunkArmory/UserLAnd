package tech.ula.utils

import android.os.Build

class DeviceArchitecture {
    fun getArchType(): String {
        val supportedABIS = this.getSupportedAbis()
                .map {
                    translateABI(it)
                }
                .filter {
                    isSupported(it)
                }
        return if (supportedABIS.size == 1 && supportedABIS[0] == "") {
            val exception = IllegalStateException("No supported ABI!")
            SentryLogger().addExceptionBreadcrumb(exception)
            throw exception
        } else {
            supportedABIS[0]
        }
    }

    private fun getSupportedAbis(): Array<String> {
        return Build.SUPPORTED_ABIS
    }

    private fun isSupported(abi: String): Boolean {
        val supportedABIs = listOf("arm64", "arm", "x86_64", "x86")
        return supportedABIs.contains(abi)
    }

    private fun translateABI(abi: String): String {
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> ""
        }
    }
}