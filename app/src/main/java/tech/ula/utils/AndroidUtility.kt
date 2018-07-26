package tech.ula.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.preference.PreferenceManager

interface Preferences {
    fun getProotDebuggingEnabled(): Boolean

    fun getProotDebuggingLevel(): String

    fun getProotDebugLogLocation(): String
}

class PreferenceUtility(private val context: Context) : Preferences {

    val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

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