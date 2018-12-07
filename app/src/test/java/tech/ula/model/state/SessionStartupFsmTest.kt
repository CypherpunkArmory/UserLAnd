package tech.ula.model.state

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.utils.ConnectionUtility
import tech.ula.utils.DownloadUtility
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class SessionStartupFsmTest {

    @Mock lateinit var activeSessionLiveData: MutableLiveData<List<Session>>

    // Mocks

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var mockAssetRepository: AssetRepository

    @Mock lateinit var mockDownloadUtility: DownloadUtility

    @Mock lateinit var mockStateObserver: Observer<SessionStartupState>

    lateinit var sessionFsm: SessionStartupFsm

    // Test setup variables
    val activeSession = Session(id = -1, name = "active", filesystemId = -1, active = true)
    val inactiveSession = Session(id = -1, name = "inactive", filesystemId = -1, active = false)

    val asset = Asset("asset", "arch", "dist", -1)
    val largeAsset = Asset("rootfs.tar.gz", "arch", "dist", -1)
    val singleAssetList = listOf(asset)
    val assetLists = listOf(singleAssetList)
    val emptyAssetLists = listOf(listOf<Asset>())

    val filesystem = Filesystem(id = -1)

    @Before
    fun setup() {
        activeSessionLiveData = MutableLiveData()

        sessionFsm = SessionStartupFsm()
    }

    @After
    fun teardown() {
        activeSessionLiveData = MutableLiveData()
    }

    @Test
    fun `Initial state is WaitingForSessionSelection`() {
        sessionFsm.getState().observeForever(mockStateObserver)

        verify(mockStateObserver).onChanged(WaitingForSessionSelection)
    }

    @Test
    fun `State is SingleSesssionSupported if active session is not selected one`() {
        sessionFsm.setState(WaitingForSessionSelection)
        sessionFsm.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf(activeSession))

        val differentSession = Session(id = 0, name = "inactive", filesystemId = -1, active = false)

        sessionFsm.submitEvent(SessionSelected(differentSession))

        verify(mockStateObserver).onChanged(SingleSessionSupported)
    }

    @Test
    fun `State is SessionIsRestartable if active session is selected one`() {
        sessionFsm.setState(WaitingForSessionSelection)
        sessionFsm.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf(activeSession))

        sessionFsm.submitEvent(SessionSelected(activeSession))

        verify(mockStateObserver).onChanged(SessionIsRestartable(activeSession))
    }

    @Test
    fun `State is SessionIsReadyForPreparation if there are no active sessions on selection`() {
        sessionFsm.setState(WaitingForSessionSelection)
        sessionFsm.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf())

        sessionFsm.submitEvent(SessionSelected(inactiveSession))

        verify(mockStateObserver).onChanged(SessionIsReadyForPreparation(inactiveSession))
    }

    @Test
    fun `State is RetrievingAssetLists and then AssetListsRetrieved`() {
        sessionFsm.setState(SessionIsReadyForPreparation(inactiveSession))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.retrieveAllAssetLists()).thenReturn(assetLists)

        sessionFsm.submitEvent(RetrieveAssetLists(filesystem))

        verify(mockStateObserver).onChanged(RetrievingAssetLists)
        verify(mockStateObserver).onChanged(AssetListsRetrievalSucceeded)
    }

    @Test
    fun `State is AssetListsAreUnavailable if remote and cached assets cannot be fetched`() {
        sessionFsm.setState(SessionIsReadyForPreparation(inactiveSession))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.retrieveAllAssetLists()).thenReturn(emptyAssetLists)

        sessionFsm.submitEvent(RetrieveAssetLists(filesystem))

        verify(mockStateObserver).onChanged(RetrievingAssetLists)
        verify(mockStateObserver).onChanged(AssetListsRetrievalFailed)
    }

    @Test
    fun `State is GeneratingDownloadRequirements while determining downloads`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, emptyAssetLists))

        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
    }

    @Test
    fun `State is LargeDownloadRequired if a rootfs needs to be downloaded`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        val filesystem = Filesystem(id = -1)
        val assetListsWithLargeAsset = listOf(listOf(largeAsset))
        whenever(mockAssetRepository.doesAssetNeedToUpdated(largeAsset)).thenReturn(true)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, assetListsWithLargeAsset))

        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
        verify(mockStateObserver).onChanged(LargeDownloadRequired)
    }

    @Test
    fun `State is DownloadingRequirements while downloads are incomplete`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        sessionFsm.submitEvent(DownloadAssets(assetLists))

        verify(mockStateObserver).onChanged(DownloadingRequirements)
    }

    @Test
    fun `State is DownloadsHaveSucceeded once downloads succeed`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockDownloadUtility.downloadRequirements(singleAssetList)).thenReturn(listOf(Pair(asset, 0L)))
        whenever(mockDownloadUtility.downloadedSuccessfully(0L)).thenReturn(true)

        sessionFsm.submitEvent(DownloadAssets(assetLists))
        sessionFsm.submitEvent(AssetDownloadComplete(0))

        verify(mockStateObserver).onChanged(DownloadingRequirements)
        verify(mockStateObserver).onChanged(DownloadsHaveSucceeded)
    }

    @Test
    fun `State is DownloadsHaveFailed if any downloads fail`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        val assetList = listOf(asset, largeAsset)
        whenever(mockDownloadUtility.downloadRequirements(assetList)).thenReturn(listOf(Pair(asset, 0L), Pair(largeAsset, 1L)))
        whenever(mockDownloadUtility.downloadedSuccessfully(0)).thenReturn(true)
        whenever(mockDownloadUtility.downloadedSuccessfully(1)).thenReturn(false)

        sessionFsm.submitEvent(DownloadAssets(listOf(assetList)))
        sessionFsm.submitEvent(AssetDownloadComplete(0))
        sessionFsm.submitEvent(AssetDownloadComplete(1))

        verify(mockStateObserver).onChanged(DownloadingRequirements)
        verify(mockStateObserver).onChanged(DownloadsHaveFailed(listOf(largeAsset)))
    }

    @Test
    fun `State is CopyingFilesToRequisiteDirectories`() {
        sessionFsm.setState(DownloadsHaveSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        val filesDir = File("test")
        sessionFsm.submitEvent(CopyDownloadsToLocalStorage(filesDir, assetLists))

        verify(mockStateObserver).onChanged(CopyingFilesToRequiredDirectories)
    }

    @Test
    fun `State is CopyingSucceeded if files are moved to correct subdirectories`() {
        sessionFsm.setState(DownloadsHaveSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        val filesDir = File("test")
        val supportAsset = Asset(name = "support", distributionType = "support", architectureType = "arch", remoteTimestamp = 0)
        val distributionAsset = Asset(name = "dist", distributionType = "dist", architectureType = "arch", remoteTimestamp = 0)
        val assetListsWithSubdirectories = listOf(listOf(supportAsset), listOf(distributionAsset))
        // TODO whenever file is copied succeed

        sessionFsm.submitEvent(CopyDownloadsToLocalStorage(filesDir, assetListsWithSubdirectories))

        verify(mockStateObserver).onChanged(CopyingFilesToRequiredDirectories)
        verify(mockStateObserver).onChanged(CopyingSucceeded)
        // TODO verify copied to test/support/support and test/dist/dist
    }

    @Test
    fun `State is CopyingFailed if a problem arises`() {
        sessionFsm.setState(DownloadsHaveSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        val filesDir = File("test")
        // TODO whenever file is copied failed

        sessionFsm.submitEvent(CopyDownloadsToLocalStorage(filesDir, assetLists))

        verify(mockStateObserver).onChanged(CopyingFilesToRequiredDirectories)
        verify(mockStateObserver).onChanged(CopyingFailed)
    }

    @Test
    fun `State is ExtractingFilesystem()`() {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem))

        verify(mockStateObserver).onChanged(ExtractingFilesystem)
    }

    @Test
    fun `State is ExtractionSucceeded if extraction succeeds`() {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        // TODO whenever extraction starts, succeed

        sessionFsm.submitEvent(ExtractFilesystem(filesystem))

        verify(mockStateObserver).onChanged(ExtractingFilesystem)
        verify(mockStateObserver).onChanged(ExtractionSucceeded)
    }

    @Test
    fun `State is ExtractionFailed if extraction fails`() {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        // TODO whenever extraction starts, fail

        sessionFsm.submitEvent(ExtractFilesystem(filesystem))

        verify(mockStateObserver).onChanged(ExtractingFilesystem)
        verify(mockStateObserver).onChanged(ExtractionFailed)
    }

    @Test
    fun `State is VerifyingFilesystemAssets if filesystem has extracted successfully`() {
        sessionFsm.setState(ExtractionSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        sessionFsm.submitEvent(VerifyFilesystemAssets(filesystem))

        verify(mockStateObserver).onChanged(VerifyingFilesystemAssets)
    }

    @Test
    fun `State is FilesystemHasRequiredAssets if all assets are present`() {
        sessionFsm.setState(ExtractionSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        // TODO whenever verifying, succeed

        sessionFsm.submitEvent(VerifyFilesystemAssets(filesystem))

        verify(mockStateObserver).onChanged(VerifyingFilesystemAssets)
        verify(mockStateObserver).onChanged(ExtractionSucceeded)
    }

    @Test
    fun `State is FilesystemIsMissingRequiredAssets if an assets are missing`() {
        sessionFsm.setState(ExtractionSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        // TODO whenever verifying, fail

        sessionFsm.submitEvent(VerifyFilesystemAssets(filesystem))

        verify(mockStateObserver).onChanged(VerifyingFilesystemAssets)
        verify(mockStateObserver).onChanged(ExtractionFailed)
    }
}