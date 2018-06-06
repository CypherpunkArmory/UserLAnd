package tech.ula.ui

import android.os.Bundle
import android.preference.PreferenceFragment
import tech.ula.R

class SettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.preferences)
    }
}