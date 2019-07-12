package tech.ula.utils.preferences

import android.content.Context
import android.os.Build
import tech.ula.model.entities.App

sealed class AppServiceTypePreference
object PreferenceHasNotBeenSelected : AppServiceTypePreference() {
    override fun toString(): String {
        return "unselected"
    }
}
object SshTypePreference : AppServiceTypePreference() {
    override fun toString(): String {
        return "ssh"
    }
}
object VncTypePreference : AppServiceTypePreference() {
    override fun toString(): String {
        return "vnc"
    }
}

object XsdlTypePreference : AppServiceTypePreference() {
    override fun toString(): String {
        return "xsdl"
    }
}

class AppsPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)

    fun setAppServiceTypePreference(appName: String, serviceType: AppServiceTypePreference) {
        val prefAsString = when (serviceType) {
            is SshTypePreference -> "ssh"
            is VncTypePreference -> "vnc"
            is XsdlTypePreference -> "xsdl"
            else -> "unselected"
        }
        with(prefs.edit()) {
            putString(appName, prefAsString)
            apply()
        }
    }

    fun getAppServiceTypePreference(app: App): AppServiceTypePreference {
        val pref = prefs.getString(app.name, "") ?: ""

        val xsdlAvailable = Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1
        val onlyCliSupported = app.supportsCli && !app.supportsGui
        val onlyVncSupported = app.supportsGui && !app.supportsCli && !xsdlAvailable
        return when {
            pref.toLowerCase() == "ssh" || onlyCliSupported -> SshTypePreference
            pref.toLowerCase() == "xsdl" -> XsdlTypePreference
            pref.toLowerCase() == "vnc" || onlyVncSupported -> VncTypePreference
            else -> PreferenceHasNotBeenSelected
        }
    }

    fun setDistributionsList(distributionList: Set<String>) {
        with(prefs.edit()) {
            putStringSet("distributionsList", distributionList)
            apply()
        }
    }

    fun getDistributionsList(): Set<String> {
        return prefs.getStringSet("distributionsList", setOf()) ?: setOf()
    }
}