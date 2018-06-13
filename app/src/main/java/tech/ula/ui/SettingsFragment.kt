package tech.ula.ui

import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceFragment
import tech.ula.R
import java.io.File

// TODO upgrade this preferencefragmentcompat when support lib v28 is stable
class SettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.preferences)

        val deleteFilePreference = findPreference("pref_proot_delete_debug_file")
        deleteFilePreference.setOnPreferenceClickListener {
            val debugFile = File("${Environment.getExternalStorageDirectory()}/PRoot_Debug_Log")
            if(debugFile.exists()) debugFile.delete()
            true
        }
    }
}