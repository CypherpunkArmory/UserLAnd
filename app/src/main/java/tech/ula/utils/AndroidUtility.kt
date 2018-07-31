package tech.ula.utils

import android.content.SharedPreferences
import android.os.Build
import android.os.Environment

interface PreferencesAccessor {
    fun getProotDebuggingEnabled(): Boolean

    fun getProotDebuggingLevel(): String

    fun getProotDebugLogLocation(): String
}

class PreferenceUtility(private val prefs: SharedPreferences) : PreferencesAccessor {

    override fun getProotDebuggingEnabled(): Boolean {
        return prefs.getBoolean("pref_proot_debug_enabled", false)
    }

    override fun getProotDebuggingLevel(): String {
        return prefs.getString("pref_proot_debug_level", "-1")
    }

    override fun getProotDebugLogLocation(): String {
        return prefs.getString("pref_proot_debug_log_location",
                "${Environment.getExternalStorageDirectory().path}/PRoot_Debug_Log")
    }
}

interface BuildAccessor {
    fun getSupportedAbis(): Array<String>
}

class BuildUtility : BuildAccessor {
    override fun getSupportedAbis(): Array<String> {
        return Build.SUPPORTED_ABIS
    }
}