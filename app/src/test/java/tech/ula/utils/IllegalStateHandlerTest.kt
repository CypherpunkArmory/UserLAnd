package tech.ula.utils

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.R
import tech.ula.viewmodel.* // ktlint-disable no-wildcard-imports

@RunWith(MockitoJUnitRunner::class)
class IllegalStateHandlerTest {

    private lateinit var illegalStateHandler: IllegalStateHandler

    @Before
    fun setup() {
        illegalStateHandler = IllegalStateHandler()
    }

    @Test
    fun `IllegalStateTransition returns correct id and strings`() {
        val reason = "reason"
        val state = IllegalStateTransition(reason)

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_transition
        val expectedResult = LocalizationData(resId, listOf(reason))
        assertEquals(expectedResult, result)
    }

    @Test
    fun `TooManySelectionsMadeWhenPermissionsGranted returns correct id and strings`() {
        val state = TooManySelectionsMadeWhenPermissionsGranted

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_too_many_selections_when_permissions_granted
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `NoSelectionsMadeWhenPermissionsGranted returns correct id and strings`() {
        val state = NoSelectionsMadeWhenPermissionsGranted

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_no_selections_when_permissions_granted
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `NoFilesystemSelectedWhenCredentialsSubmitted returns correct id and strings`() {
        val state = NoFilesystemSelectedWhenCredentialsSubmitted

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_no_filesystem_selected_when_credentials_selected
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `NoAppSelectedWhenPreferenceSubmitted returns correct id and strings`() {
        val state = NoAppSelectedWhenPreferenceSubmitted

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_no_app_selected_when_preference_submitted
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `NoAppSelectedWhenTransitionNecessary returns correct id and strings`() {
        val state = NoAppSelectedWhenTransitionNecessary

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_no_app_selected_when_preparation_started
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ErrorFetchingAppDatabaseEntries returns correct id and strings`() {
        val state = ErrorFetchingAppDatabaseEntries

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_error_fetching_app_database_entries
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ErrorCopyingAppScript returns correct id and strings`() {
        val state = ErrorCopyingAppScript

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_error_copying_app_script
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `NoSessionSelectedWhenTransitionNecessary returns correct id and strings`() {
        val state = NoSessionSelectedWhenTransitionNecessary

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_no_session_selected_when_preparation_started
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ErrorFetchingAssetLists returns correct id and strings`() {
        val state = ErrorFetchingAssetLists

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_error_fetching_asset_lists
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ErrorGeneratingDownloads returns correct id and strings`() {
        val state = ErrorGeneratingDownloads(0)

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = 0
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `DownloadsDidNotCompleteSuccessfully returns correct id and strings`() {
        val reason = DownloadFailureLocalizationData(0)
        val state = DownloadsDidNotCompleteSuccessfully(reason)

        val result = illegalStateHandler.getLocalizationData(state)

        assertEquals(reason, result)
    }

    @Test
    fun `FailedToCopyAssetsToLocalStorage returns correct id and strings`() {
        val state = FailedToCopyAssetsToLocalStorage

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_failed_to_copy_assets_to_local
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `AssetsHaveNotBeenDownloaded returns correct id and strings`() {
        val state = AssetsHaveNotBeenDownloaded

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_assets_have_not_been_downloaded
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `DownloadCacheAccessedWhileEmpty returns correct id and strings`() {
        val state = DownloadCacheAccessedWhileEmpty

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_empty_download_cache_access
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `FailedToCopyAssetsToFilesystem returns correct id and strings`() {
        val state = FailedToCopyAssetsToFilesystem

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_failed_to_copy_assets_to_filesystem
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `FailedToExtractFilesystem returns correct id and strings`() {
        val reason = "reason"
        val state = FailedToExtractFilesystem(reason)

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_failed_to_extract_filesystem
        val expectedResult = LocalizationData(resId, listOf(reason))
        assertEquals(expectedResult, result)
    }

    @Test
    fun `FailedToClearSupportFiles returns correct id and strings`() {
        val state = FailedToClearSupportFiles

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_failed_to_clear_support_files
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `InsufficientAvailableStorage returns correct id and strings`() {
        val state = InsufficientAvailableStorage

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_insufficient_storage
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }

    @Test
    fun `BusyboxMissing returns correct id and strings`() {
        val state = BusyboxMissing

        val result = illegalStateHandler.getLocalizationData(state)

        val resId = R.string.illegal_state_busybox_missing
        val expectedResult = LocalizationData(resId, listOf())
        assertEquals(expectedResult, result)
    }
}