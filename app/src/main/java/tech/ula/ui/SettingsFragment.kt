package tech.ula.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import tech.ula.R
import androidx.preference.PreferenceFragmentCompat
import android.widget.Toast
import androidx.preference.Preference
import tech.ula.utils.ProotDebugLogger
import tech.ula.utils.defaultSharedPreferences
import tech.ula.utils.storageRoot

private const val EXPORT_REQUEST_CODE = 42

class SettingsFragment : PreferenceFragmentCompat() {

    private val prootDebugLogger by lazy {
        ProotDebugLogger(activity!!.defaultSharedPreferences, activity!!.storageRoot.path)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val exportFilePreference: Preference = findPreference("pref_proot_export_debug_file")!!
        exportFilePreference.setOnPreferenceClickListener {
            val intent = prootDebugLogger.generateCreateIntent()
            startActivityForResult(intent, EXPORT_REQUEST_CODE)
            true
        }

        val deleteFilePreference: Preference = findPreference("pref_proot_delete_debug_file")!!
        deleteFilePreference.setOnPreferenceClickListener {
            prootDebugLogger.deleteLog()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_REQUEST_CODE) {
            data?.data?.let {
                prootDebugLogger.copyLogToDestination(it, activity!!.contentResolver)
            }

        }
    }
}