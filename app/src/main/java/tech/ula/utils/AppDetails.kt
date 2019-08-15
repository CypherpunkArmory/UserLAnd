package tech.ula.utils

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import tech.ula.R
import java.io.File

class AppDetails(private val applicationFilesDir: String, private val resources: Resources) {
    fun findIconUri(appName: String): Uri {
        val icon =
                File("$applicationFilesDir/apps/$appName/$appName.png")
        if (icon.exists()) return Uri.fromFile(icon)
        return getDefaultIconUri()
    }

    private fun getDefaultIconUri(): Uri {
        val resId = R.mipmap.ic_launcher_foreground
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + resources.getResourcePackageName(resId) + '/' +
                resources.getResourceTypeName(resId) + '/' +
                resources.getResourceEntryName(resId))
    }

    fun findAppDescription(appName: String): String {
        val appDescriptionFile =
                File("$applicationFilesDir/apps/$appName/$appName.txt")
        if (!appDescriptionFile.exists()) {
            return resources.getString(R.string.error_app_description_not_found)
        }
        return appDescriptionFile.readText()
    }
}