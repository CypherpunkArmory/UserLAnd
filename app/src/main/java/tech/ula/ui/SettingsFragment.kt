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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.ula.utils.ProotDebugLogger
import tech.ula.utils.defaultSharedPreferences
import tech.ula.utils.storageRoot
import kotlin.coroutines.CoroutineContext

private const val EXPORT_REQUEST_CODE = 42

class SettingsFragment : PreferenceFragmentCompat(), CoroutineScope {

    private val prootDebugLogger by lazy {
        ProotDebugLogger(activity!!.defaultSharedPreferences, activity!!.storageRoot.path)
    }

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val exportFilePreference: Preference = findPreference("pref_proot_export_debug_file")!!
        exportFilePreference.setOnPreferenceClickListener {
            val intent = generateCreateIntent()
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
                // TODO coroutines should be launched from a viewmodel scope to survive config changes
                launch {
                    val result = prootDebugLogger.copyLogToDestination(it, activity!!.contentResolver)
                    if (result) Toast.makeText(activity, R.string.debug_log_export_success, Toast.LENGTH_LONG).show()
                    else Toast.makeText(activity, R.string.debug_log_export_failure, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generateCreateIntent(): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, prootDebugLogger.logName)
        }
    }
}