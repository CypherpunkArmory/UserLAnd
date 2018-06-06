package tech.userland.userland.ui

import android.os.Bundle
import android.preference.PreferenceFragment
import tech.userland.userland.R

class SettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.preferences)
    }
}