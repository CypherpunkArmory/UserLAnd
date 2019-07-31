package tech.ula.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import tech.ula.R
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.Preference
import tech.ula.utils.ProotDebugLogger
import tech.ula.utils.UlaFiles
import tech.ula.utils.defaultSharedPreferences

class SettingsFragment : PreferenceFragmentCompat() {

    private val prootDebugLogger by lazy {
        val ulaFiles = UlaFiles(activity!!, activity!!.applicationInfo.nativeLibraryDir)
        ProotDebugLogger(activity!!.defaultSharedPreferences, ulaFiles)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val deleteFilePreference: Preference = findPreference("pref_proot_delete_debug_file")!!
        deleteFilePreference.setOnPreferenceClickListener {
            prootDebugLogger.deleteLogs()
            true
        }
    }

    override fun setDivider(divider: Drawable?) {
        super.setDivider(ColorDrawable(Color.TRANSPARENT))
    }

    override fun setDividerHeight(height: Int) {
        super.setDividerHeight(0)
    }
}