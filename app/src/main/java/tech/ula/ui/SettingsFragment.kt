package tech.ula.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import tech.ula.R
import java.io.File
import android.support.v7.preference.PreferenceFragmentCompat
import android.widget.Toast

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val deleteFilePreference = findPreference("pref_proot_delete_debug_file")
        deleteFilePreference.setOnPreferenceClickListener {
            val debugFile = File("${Environment.getExternalStorageDirectory()}/PRoot_Debug_Log")
            if(debugFile.exists()) debugFile.delete()
            Toast.makeText(activity, R.string.debug_log_deleted, Toast.LENGTH_LONG).show()
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