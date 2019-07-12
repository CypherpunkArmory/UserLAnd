package tech.ula.utils

import tech.ula.R
import tech.ula.viewmodel.* // ktlint-disable no-wildcard-imports

object IllegalStateHandler {

    fun getLocalizationData(state: IllegalState): Localization {
        return when (state) {
            is IllegalStateTransition -> {
                LocalizationData(R.string.illegal_state_transition, listOf(state.transition))
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
                return state.reason
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
            is FailedToCopyAssetsToFilesystem -> {
                LocalizationData(R.string.illegal_state_failed_to_copy_assets_to_filesystem)
            }
            is FailedToExtractFilesystem -> {
                LocalizationData(R.string.illegal_state_failed_to_extract_filesystem, listOf(state.reason))
            }
            is FailedToClearSupportFiles -> {
                LocalizationData(R.string.illegal_state_failed_to_clear_support_files)
            }
            is InsufficientAvailableStorage -> {
                LocalizationData(R.string.illegal_state_insufficient_storage)
            }
            is BusyboxMissing -> {
                LocalizationData(R.string.illegal_state_busybox_missing)
            }
        }
    }
}