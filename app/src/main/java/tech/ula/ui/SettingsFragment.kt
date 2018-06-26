package tech.ula.ui

import android.os.Bundle
import android.os.Environment
import tech.ula.R
import java.io.File
import android.support.v7.preference.PreferenceFragmentCompat

// TODO upgrade this preferencefragmentcompat when support lib v28 is stable
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val deleteFilePreference = findPreference("pref_proot_delete_debug_file")
        deleteFilePreference.setOnPreferenceClickListener {
            val debugFile = File("${Environment.getExternalStorageDirectory()}/PRoot_Debug_Log")
            if(debugFile.exists()) debugFile.delete()
            true
        }
    }
}