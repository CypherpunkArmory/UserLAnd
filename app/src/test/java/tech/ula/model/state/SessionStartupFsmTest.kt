package tech.ula.model.state

import org.junit.Assert.*
import org.junit.Test

class SessionStartupFsmTest {

    @Test
    fun `Initial state is WaitingForSessionSelection`() {

    }

    @Test
    fun `State is SingleSesssionSupported if active session is not selected one`() {

    }

    @Test
    fun `State is SessionIsRestartable if active session is selected one`() {

    }

    @Test
    fun `State is RetrievingRemoteAssetLists if network is available`() {

    }

    @Test
    fun `State is RetrievingCachedAssetLists if network is unavailable`() {

    }

    @Test
    fun `State is AssetListsAreUnavailable if remote and cached assets cannot be fetched`() {

    }

    @Test
    fun `State is GeneratingDownloadRequirements while determining downloads`() {

    }

    @Test
    fun `State is LargeDownloadRequired if a rootfs needs to be downloaded`() {

    }

    @Test
    fun `State is DownloadingRequirements while downloads are incomplete`() {

    }

    @Test
    fun `State is DownloadsHaveCompleted once downloads succeed`() {

    }

    @Test
    fun `State is ExtractingFilesystem() after downloads complete `() {

    }

    @Test
    fun `State is ExtractionSucceeded if extraction succeeds`() {

    }

    @Test
    fun `State is ExtractionFailed if extraction fails`() {

    }

    @Test
    fun `State is VerifyingFilesystemAssets if filesystem has extracted successfully`() {

    }

    @Test
    fun `State is SessionCanBeActivated if all previous steps pass`() {

    }
}