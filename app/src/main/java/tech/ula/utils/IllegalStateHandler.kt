package tech.ula.utils

import tech.ula.R
import tech.ula.viewmodel.* // ktlint-disable no-wildcard-imports
import java.util.Arrays

data class LocalizationData(val resId: Int, val formatStrings: Array<String> = arrayOf()) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as LocalizationData

        if (!Arrays.equals(formatStrings, other.formatStrings)) return false

        return resId == other.resId
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(formatStrings)
    }
}

class IllegalStateHandler(private val acraWrapper: AcraWrapper = AcraWrapper()) {

    fun getResourceId(state: IllegalState): LocalizationData {
        return when (state) {
            is IllegalStateTransition -> {
                LocalizationData(R.string.illegal_state_transition, arrayOf(state.transition))
            }
            is TooManySelectionsMadeWhenPermissionsGranted -> {
                LocalizationData(R.string.illegal_state_too_many_selections_when_permissions_granted)
            }
            is NoSelectionsMadeWhenPermissionsGranted -> {
                LocalizationData(R.string.illegal_state_no_selections_when_permissions_granted)
            }
            is NoFilesystemSelectedWhenCredentialsSubmitted -> {
                LocalizationData(R.string.illegal_state_no_filesystem_selected_when_credentials_selected)
            }
            is NoAppSelectedWhenPreferenceSubmitted -> {
                LocalizationData(R.string.illegal_state_no_app_selected_when_preference_submitted)
            }
            is NoAppSelectedWhenTransitionNecessary -> {
                LocalizationData(R.string.illegal_state_no_app_selected_when_preparation_started)
            }
            is ErrorFetchingAppDatabaseEntries -> {
                LocalizationData(R.string.illegal_state_error_fetching_app_database_entries)
            }
            is ErrorCopyingAppScript -> {
                LocalizationData(R.string.illegal_state_error_copying_app_script)
            }
            is NoSessionSelectedWhenTransitionNecessary -> {
                LocalizationData(R.string.illegal_state_no_session_selected_when_preparation_started)
            }
            is ErrorFetchingAssetLists -> {
                LocalizationData(R.string.illegal_state_error_fetching_asset_lists)
            }
            is ErrorGeneratingDownloads -> {
                LocalizationData(state.errorId)
            }
            is DownloadsDidNotCompleteSuccessfully -> {
                LocalizationData(R.string.illegal_state_downloads_did_not_complete_successfully, arrayOf(state.reason))
            }
            is FailedToCopyAssetsToLocalStorage -> {
                LocalizationData(R.string.illegal_state_failed_to_copy_assets_to_local)
            }
            is AssetsHaveNotBeenDownloaded -> {
                LocalizationData(R.string.illegal_state_assets_have_not_been_downloaded)
            }
            is DownloadCacheAccessedWhileEmpty -> {
                LocalizationData(R.string.illegal_state_empty_download_cache_access)
            }
            is DownloadCacheAccessedInAnIncorrectState -> {
                LocalizationData(R.string.illegal_state_download_cache_access_in_incorrect_state)
            }
            is FailedToCopyAssetsToFilesystem -> {
                LocalizationData(R.string.illegal_state_failed_to_copy_assets_to_filesystem)
            }
            is FailedToExtractFilesystem -> {
                LocalizationData(R.string.illegal_state_failed_to_extract_filesystem, arrayOf(state.reason))
            }
            is FailedToClearSupportFiles -> {
                LocalizationData(R.string.illegal_state_failed_to_clear_support_files)
            }
            is InsufficientAvailableStorage -> {
                LocalizationData(R.string.illegal_state_insufficient_storage)
            }
        }
    }
}