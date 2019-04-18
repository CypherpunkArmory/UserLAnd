package tech.ula.utils

import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.R
import tech.ula.viewmodel.* // ktlint-disable no-wildcard-imports

@RunWith(MockitoJUnitRunner::class)
class IllegalStateHandlerTest {

    @Mock lateinit var mockAcraWrapper: AcraWrapper

    private lateinit var illegalStateHandler: IllegalStateHandler

    @Before
    fun setup() {
        illegalStateHandler = IllegalStateHandler(mockAcraWrapper)
    }

    @Test
    fun `IllegalStateTransition returns correct id and strings, silently logging an exception`() {
        val reason = "reason"
        val state = IllegalStateTransition(reason)

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_transition
        val expectedResult = LocalizationData(resId, arrayOf(reason))
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `TooManySelectionsMadeWhenPermissionsGranted returns correct id and strings, silently logging an exception`() {
        val state = TooManySelectionsMadeWhenPermissionsGranted

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_too_many_selections_when_permissions_granted
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `NoSelectionsMadeWhenPermissionsGranted returns correct id and strings, silently logging an exception`() {
        val state = NoSelectionsMadeWhenPermissionsGranted

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_no_selections_when_permissions_granted
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `NoFilesystemSelectedWhenCredentialsSubmitted returns correct id and strings, silently logging an exception`() {
        val state = NoFilesystemSelectedWhenCredentialsSubmitted

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_no_filesystem_selected_when_credentials_selected
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `NoAppSelectedWhenPreferenceSubmitted returns correct id and strings, silently logging an exception`() {
        val state = NoAppSelectedWhenPreferenceSubmitted

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_no_app_selected_when_preference_submitted
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `NoAppSelectedWhenTransitionNecessary returns correct id and strings, silently logging an exception`() {
        val state = NoAppSelectedWhenTransitionNecessary

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_no_app_selected_when_preparation_started
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `ErrorFetchingAppDatabaseEntries returns correct id and strings, silently logging an exception`() {
        val state = ErrorFetchingAppDatabaseEntries

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_error_fetching_app_database_entries
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `ErrorCopyingAppScript returns correct id and strings, silently logging an exception`() {
        val state = ErrorCopyingAppScript

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_error_copying_app_script
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `NoSessionSelectedWhenTransitionNecessary returns correct id and strings, silently logging an exception`() {
        val state = NoSessionSelectedWhenTransitionNecessary

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_no_session_selected_when_preparation_started
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `ErrorFetchingAssetLists returns correct id and strings, silently logging an exception`() {
        val state = ErrorFetchingAssetLists

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_error_fetching_asset_lists
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `ErrorGeneratingDownloads returns correct id and strings, silently logging an exception`() {
        val state = ErrorGeneratingDownloads(0)

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = 0
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `DownloadsDidNotCompleteSuccessfully returns correct id and strings, silently logging an exception`() {
        val reason = "reason"
        val state = DownloadsDidNotCompleteSuccessfully(reason)

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_downloads_did_not_complete_successfully
        val expectedResult = LocalizationData(resId, arrayOf(reason))
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `FailedToCopyAssetsToLocalStorage returns correct id and strings, silently logging an exception`() {
        val state = FailedToCopyAssetsToLocalStorage

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_failed_to_copy_assets_to_local
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `AssetsHaveNotBeenDownloaded returns correct id and strings, silently logging an exception`() {
        val state = AssetsHaveNotBeenDownloaded

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_assets_have_not_been_downloaded
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `DownloadCacheAccessedWhileEmpty returns correct id and strings, silently logging an exception`() {
        val state = DownloadCacheAccessedWhileEmpty

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_empty_download_cache_access
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `DownloadCacheAccessedInAnIncorrectState returns correct id and strings, silently logging an exception`() {
        val state = DownloadCacheAccessedInAnIncorrectState

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_download_cache_access_in_incorrect_state
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `FailedToCopyAssetsToFilesystem returns correct id and strings, silently logging an exception`() {
        val state = FailedToCopyAssetsToFilesystem

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_failed_to_copy_assets_to_filesystem
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `FailedToExtractFilesystem returns correct id and strings, silently logging an exception`() {
        val reason = "reason"
        val state = FailedToExtractFilesystem(reason)

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_failed_to_extract_filesystem
        val expectedResult = LocalizationData(resId, arrayOf(reason))
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `FailedToClearSupportFiles returns correct id and strings, silently logging an exception`() {
        val state = FailedToClearSupportFiles

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_failed_to_clear_support_files
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }

    @Test
    fun `InsufficientAvailableStorage returns correct id and strings, silently logging an exception`() {
        val state = InsufficientAvailableStorage

        val result = illegalStateHandler.logAndGetResourceId(state)

        val resId = R.string.illegal_state_insufficient_storage
        val expectedResult = LocalizationData(resId, arrayOf())
        assertEquals(expectedResult, result)
        verify(mockAcraWrapper).silentlySendIllegalStateReport()
    }
}