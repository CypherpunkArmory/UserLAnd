package tech.ula.utils.preferences

import android.content.Context

class AppsPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)

    fun setDistributionsList(distributionList: Set<String>) {
        with(prefs.edit()) {
            putStringSet("distributionsList", distributionList)
            apply()
        }
    }

    fun getDistributionsList(): Set<String> {
        return prefs.getStringSet("distributionsList", setOf()) ?: setOf()
    }

    fun setCloudDistributionsList(distributionList: Set<String>) {
        with(prefs.edit()) {
            putStringSet("cloudDistributionsList", distributionList)
            apply()
        }
    }

    fun getCloudDistributionsList(): Set<String> {
        return prefs.getStringSet("cloudDistributionsList", setOf()) ?: setOf()
    }
}