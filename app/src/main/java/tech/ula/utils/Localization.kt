package tech.ula.utils

import android.content.Context
import tech.ula.R

interface Localization {
    fun getString(context: Context): String
}

data class LocalizationData(val resId: Int, val formatStrings: List<String> = listOf()) : Localization {
    override fun getString(context: Context): String {
        return context.getString(resId, formatStrings)
    }
}

data class DownloadFailureLocalizationData(val resId: Int, val formatStrings: List<String> = listOf()) : Localization {
    override fun getString(context: Context): String {
        val errorDescriptionResId = R.string.illegal_state_downloads_did_not_complete_successfully
        val errorTypeString = context.getString(resId, formatStrings)
        return context.getString(errorDescriptionResId, errorTypeString)
    }
}