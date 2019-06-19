package tech.ula.utils

import android.content.SharedPreferences
import tech.ula.BuildConfig

class QWarningHandler(private val prefs: SharedPreferences, private val ulaFiles: UlaFiles) {

    companion object {
        val prefsString = "q_warning_preferences"
    }

    private val versionKey = "appVersion"
    private val hasBeenDisplayedKey = "messageHasBeenDisplayed"

    fun messageShouldBeDisplayed(): Boolean {
        return versionDoesNotMatchRequirement() &&
                !messageHasPreviouslyBeenDisplayed() &&
                userHasFilesystems()
    }

    fun messageHasBeenDisplayed() {
        setCachedVersion()
        setMessageDisplayed()
    }

    private fun versionDoesNotMatchRequirement(): Boolean {
        return prefs.getString(versionKey, "") ?: "" < "v2.1.5"
    }

    private fun userHasFilesystems(): Boolean {
        val hasFilesystems = ulaFiles.filesDir.listFiles().mapNotNull { it.name.toIntOrNull() }.isNotEmpty()
        if (hasFilesystems) setMessageDisplayed()
        return hasFilesystems
    }

    private fun messageHasPreviouslyBeenDisplayed(): Boolean {
        return prefs.getBoolean(hasBeenDisplayedKey, false)
    }

    private fun setCachedVersion() {
        with(prefs.edit()) {
            putString(versionKey, BuildConfig.VERSION_NAME)
            apply()
        }
    }

    private fun setMessageDisplayed() {
        with(prefs.edit()) {
            putBoolean(hasBeenDisplayedKey, true)
            apply()
        }
    }
}