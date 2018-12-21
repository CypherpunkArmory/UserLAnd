package tech.ula.model.state

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.DownloadUtility
import tech.ula.utils.FilesystemUtility
import java.lang.Exception

@RunWith(MockitoJUnitRunner::class)
class SessionStartupFsmTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    // Mocks

    @Mock lateinit var activeSessionLiveData: MutableLiveData<List<Session>>

    @Mock lateinit var mockUlaDatabase: UlaDatabase

    @Mock lateinit var mockSessionDao: SessionDao

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockAssetRepository: AssetRepository

    @Mock lateinit var mockDownloadUtility: DownloadUtility

    @Mock lateinit var mockFilesystemUtility: FilesystemUtility

    @Mock lateinit var mockStateObserver: Observer<SessionStartupState>

    lateinit var sessionFsm: SessionStartupFsm

    // Test setup variables
    val activeSession = Session(id = -1, name = "active", filesystemId = -1, active = true)
    val inactiveSession = Session(id = -1, name = "inactive", filesystemId = -1, active = false)

    val asset = Asset("asset", "arch", "dist", -1)
    val largeAsset = Asset("rootfs.tar.gz", "arch", "dist", -1)
    val singleAssetList = listOf(asset)
    val assetLists = listOf(singleAssetList)
    val assetListsWithLargeAsset = listOf(listOf(asset, largeAsset))
    val emptyAssetLists = listOf(listOf<Asset>())

    val filesystem = Filesystem(id = -1)

    @Before
    fun setup() {
        activeSessionLiveData = MutableLiveData()
        val filesystemLiveData = MutableLiveData<List<Filesystem>>().apply { postValue(listOf(filesystem)) }

        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)
        whenever(mockSessionDao.findActiveSessions()).thenReturn(activeSessionLiveData)
        whenever(mockUlaDatabase.filesystemDao()).thenReturn(mockFilesystemDao)
        whenever(mockFilesystemDao.getAllFilesystems()).thenReturn(filesystemLiveData)

        sessionFsm = SessionStartupFsm(mockUlaDatabase, mockAssetRepository, mockFilesystemUtility, mockDownloadUtility)
    }

    @After
    fun teardown() {
        activeSessionLiveData = MutableLiveData()
    }

    @Test
    fun `Only allows correct state transitions`() = runBlocking {
        sessionFsm.getState().observeForever(mockStateObserver)

        val incorrectTransitionEvent = SessionSelected(inactiveSession)
        val incorrectTransitionState = RetrievingAssetLists
        val events = listOf(
                SessionSelected(inactiveSession),
                RetrieveAssetLists(filesystem),
                GenerateDownloads(filesystem, assetLists),
                DownloadAssets(singleAssetList),
                AssetDownloadComplete(0),
                CopyDownloadsToLocalStorage,
                ExtractFilesystem(filesystem),
                VerifyFilesystemAssets(filesystem)
        )
        val states = listOf(
                IncorrectSessionTransition(incorrectTransitionEvent, incorrectTransitionState),
                WaitingForSessionSelection,
                SingleSessionSupported,
                SessionIsRestartable(inactiveSession),
                SessionIsReadyForPreparation(inactiveSession, filesystem),
                RetrievingAssetLists,
                AssetListsRetrievalSucceeded(assetLists),
                AssetListsRetrievalFailed,
                GeneratingDownloadRequirements,
                NoDownloadsRequired,
                DownloadsRequired(singleAssetList, false),
                DownloadingRequirements(0, 0),
                DownloadsHaveSucceeded,
                DownloadsHaveFailed,
                CopyingFilesToRequiredDirectories,
                CopyingSucceeded,
                CopyingFailed,
                ExtractingFilesystem("test"),
                ExtractionSucceeded,
                ExtractionFailed,
                VerifyingFilesystemAssets,
                FilesystemHasRequiredAssets,
                FilesystemIsMissingRequiredAssets
        )

        for (event in events) {
            for (state in states) {
                sessionFsm.setState(state)
                val result = sessionFsm.transitionIsAcceptable(event)
                when {
                    event is SessionSelected && state is WaitingForSessionSelection -> assertTrue(result)
                    event is RetrieveAssetLists && state is SessionIsReadyForPreparation -> assertTrue(result)
                    event is GenerateDownloads && state is AssetListsRetrievalSucceeded -> assertTrue(result)
                    event is DownloadAssets && state is DownloadsRequired -> assertTrue(result)
                    event is AssetDownloadComplete && state is DownloadingRequirements -> assertTrue(result)
                    event is CopyDownloadsToLocalStorage && state is DownloadsHaveSucceeded -> assertTrue(result)
                    event is ExtractFilesystem && (state is NoDownloadsRequired || state is CopyingSucceeded) -> assertTrue(result)
                    event is VerifyFilesystemAssets && state is ExtractionSucceeded -> assertTrue(result)
                    else -> assertFalse(result)
                }
            }
        }
    }

    @Test
    fun `Exits early if incorrect transition event submitted`() = runBlocking {
        val state = WaitingForSessionSelection
        sessionFsm.setState(state)
        sessionFsm.getState().observeForever(mockStateObserver)

        val event = RetrieveAssetLists(filesystem)
        sessionFsm.submitEvent(event)

        verify(mockStateObserver, times(1)).onChanged(IncorrectSessionTransition(event, state))
        verify(mockStateObserver, times(2)).onChanged(any()) // Observes when registered and again on state emission
    }

    @Test
    fun `Initial state is WaitingForSessionSelection`() {
        sessionFsm.getState().observeForever(mockStateObserver)

        verify(mockStateObserver).onChanged(WaitingForSessionSelection)
    }

    @Test
    fun `State is SingleSessionSupported if active session is not selected one`() = runBlocking {
        sessionFsm.setState(WaitingForSessionSelection)
        sessionFsm.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf(activeSession))

        val differentSession = Session(id = 0, name = "inactive", filesystemId = -1, active = false)

        sessionFsm.submitEvent(SessionSelected(differentSession))

        verify(mockStateObserver).onChanged(SingleSessionSupported)
    }

    @Test
    fun `State is SessionIsRestartable if active session is selected one`() = runBlocking {
        sessionFsm.setState(WaitingForSessionSelection)
        sessionFsm.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf(activeSession))

        sessionFsm.submitEvent(SessionSelected(activeSession))

        verify(mockStateObserver).onChanged(SessionIsRestartable(activeSession))
    }

    @Test
    fun `State is SessionIsReadyForPreparation if there are no active sessions on selection`() = runBlocking {
        sessionFsm.setState(WaitingForSessionSelection)
        sessionFsm.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf())

        sessionFsm.submitEvent(SessionSelected(inactiveSession))

        verify(mockStateObserver).onChanged(SessionIsReadyForPreparation(inactiveSession, filesystem))
    }

    @Test
    fun `State is RetrievingAssetLists and then AssetListsRetrieved`() = runBlocking {
        sessionFsm.setState(SessionIsReadyForPreparation(inactiveSession, filesystem))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getAllAssetLists(filesystem.distributionType, filesystem.archType)).thenReturn(assetLists)

        sessionFsm.submitEvent(RetrieveAssetLists(filesystem))

        verify(mockStateObserver).onChanged(RetrievingAssetLists)
        verify(mockStateObserver).onChanged(AssetListsRetrievalSucceeded(assetLists))
    }

    @Test
    fun `State is AssetListsRetrievalFailed if remote and cached assets cannot be fetched`() = runBlocking {
        sessionFsm.setState(SessionIsReadyForPreparation(inactiveSession, filesystem))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getAllAssetLists(filesystem.distributionType, filesystem.archType)).thenReturn(emptyAssetLists)

        sessionFsm.submitEvent(RetrieveAssetLists(filesystem))

        verify(mockStateObserver).onChanged(RetrievingAssetLists)
        verify(mockStateObserver).onChanged(AssetListsRetrievalFailed)
    }

    @Test
    fun `State is DownloadsRequired and largeDownloadRequired is true if a rootfs needs to be downloaded`() = runBlocking {
        sessionFsm.setState(AssetListsRetrievalSucceeded(assetListsWithLargeAsset))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        whenever(mockAssetRepository.doesAssetNeedToUpdated(largeAsset)).thenReturn(true)
        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(false)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, assetListsWithLargeAsset))

        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
        verify(mockStateObserver).onChanged(DownloadsRequired(assetListsWithLargeAsset.flatten(), largeDownloadRequired = true))
    }

    @Test
    fun `State is DownloadsRequired and largeDownloadRequired is false if a rootfs needs updating but the filesystem already exists`() = runBlocking {
        sessionFsm.setState(AssetListsRetrievalSucceeded(assetListsWithLargeAsset))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        whenever(mockAssetRepository.doesAssetNeedToUpdated(largeAsset)).thenReturn(true)
        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(true)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, assetListsWithLargeAsset))

        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
        verify(mockStateObserver).onChanged(DownloadsRequired(singleAssetList, largeDownloadRequired = false))
    }

    @Test
    fun `State is DownloadsRequired and includes false if downloads do not include rootfs`() = runBlocking {
        sessionFsm.setState(AssetListsRetrievalSucceeded(assetLists))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, assetLists))

        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
        verify(mockStateObserver).onChanged(DownloadsRequired(assetLists.flatten(), largeDownloadRequired = false))
    }

    @Test
    fun `State is DownloadsHaveSucceeded once downloads succeed`() = runBlocking {
        val downloadList = listOf(asset, largeAsset)
        sessionFsm.setState(DownloadsRequired(downloadList, true))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockDownloadUtility.downloadRequirements(downloadList)).thenReturn(listOf(Pair(asset, 0L), Pair(largeAsset, 1L)))
        whenever(mockDownloadUtility.downloadedSuccessfully(0)).thenReturn(true)
        whenever(mockDownloadUtility.downloadedSuccessfully(1)).thenReturn(true)

        sessionFsm.submitEvent(DownloadAssets(downloadList))
        sessionFsm.submitEvent(AssetDownloadComplete(0))
        sessionFsm.submitEvent(AssetDownloadComplete(1))

        verify(mockStateObserver).onChanged(DownloadingRequirements(0, 2))
        verify(mockStateObserver).onChanged(DownloadingRequirements(1, 2))
        verify(mockStateObserver).onChanged(DownloadsHaveSucceeded)
    }

    @Test
    fun `State is DownloadsHaveFailed if any downloads fail`() = runBlocking {
        val downloadList = listOf(asset, largeAsset)
        sessionFsm.setState(DownloadsRequired(downloadList, true))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockDownloadUtility.downloadRequirements(downloadList)).thenReturn(listOf(Pair(asset, 0L), Pair(largeAsset, 1L)))
        whenever(mockDownloadUtility.downloadedSuccessfully(0)).thenReturn(true)
        whenever(mockDownloadUtility.downloadedSuccessfully(1)).thenReturn(false)

        sessionFsm.submitEvent(DownloadAssets(downloadList))
        sessionFsm.submitEvent(AssetDownloadComplete(0))
        sessionFsm.submitEvent(AssetDownloadComplete(1))

        verify(mockStateObserver).onChanged(DownloadingRequirements(0, 2))
        verify(mockStateObserver).onChanged(DownloadingRequirements(1, 2))
        verify(mockStateObserver).onChanged(DownloadsHaveFailed)
    }

    @Test
    fun `State is CopyingSucceeded if files are moved to correct subdirectories`() = runBlocking {
        sessionFsm.setState(DownloadsHaveSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        sessionFsm.submitEvent(CopyDownloadsToLocalStorage)

        verify(mockStateObserver).onChanged(CopyingFilesToRequiredDirectories)
        verify(mockStateObserver).onChanged(CopyingSucceeded)
        verify(mockDownloadUtility).moveAssetsToCorrectLocalDirectory()
    }

    @Test
    fun `State is CopyingFailed if a problem arises`() = runBlocking {
        sessionFsm.setState(DownloadsHaveSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        // TODO research how throws annotation actually wraps jvm interop
        whenever(mockDownloadUtility.moveAssetsToCorrectLocalDirectory()).thenThrow(Exception::class.java)

        sessionFsm.submitEvent(CopyDownloadsToLocalStorage)

        verify(mockStateObserver).onChanged(CopyingFilesToRequiredDirectories)
        verify(mockStateObserver).onChanged(CopyingFailed)
    }

    @Test
    fun `Exits early if filesystem is already extracted`() = runBlocking {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(true)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem))

        verify(mockFilesystemUtility, times(1)).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        verify(mockStateObserver).onChanged(ExtractionSucceeded)
    }

    @Test
    fun `State is CopyingFailed if copying assets to filesystem fails`() = runBlocking {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(false)
        // TODO throws here too
        whenever(mockFilesystemUtility.copyAssetsToFilesystem("${filesystem.id}", filesystem.distributionType)).thenThrow(Exception::class.java)

         sessionFsm.submitEvent(ExtractFilesystem(filesystem))

        verify(mockFilesystemUtility, times(1)).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        verify(mockStateObserver).onChanged(DistributionCopyFailed)
    }

    @Test
    fun `State is ExtractionSucceeded if extraction succeeds`() = runBlocking {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}"))
                .thenReturn(false)
                .thenReturn(true)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem))

        // TODO is there some way to verify extraction steps?
        verify(mockStateObserver).onChanged(ExtractionSucceeded)
    }

    @Test
    fun `State is ExtractionFailed if extraction fails`() = runBlocking {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}"))
                .thenReturn(false)
                .thenReturn(false)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem))

        verify(mockStateObserver).onChanged(ExtractionFailed)
    }

    @Test
    fun `State is FilesystemHasRequiredAssets if all assets are present`() = runBlocking {
        sessionFsm.setState(ExtractionSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getDistributionAssetsForExistingFilesystem(filesystem))
                .thenReturn(singleAssetList)
        whenever(mockFilesystemUtility.areAllRequiredAssetsPresent("${filesystem.id}", singleAssetList))
                .thenReturn(true)
        whenever(mockAssetRepository.getLastDistributionUpdate(filesystem.distributionType))
                .thenReturn(filesystem.lastUpdated)

        sessionFsm.submitEvent(VerifyFilesystemAssets(filesystem))

        verify(mockStateObserver).onChanged(VerifyingFilesystemAssets)
        verify(mockStateObserver).onChanged(FilesystemHasRequiredAssets)
    }

    @Test
    fun `State is FilesystemHasRequiredAssets if it needs to copy filesystem assets and succeeds`() = runBlocking {
        sessionFsm.setState(ExtractionSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getDistributionAssetsForExistingFilesystem(filesystem))
                .thenReturn(singleAssetList)
        whenever(mockFilesystemUtility.areAllRequiredAssetsPresent("${filesystem.id}", singleAssetList))
                .thenReturn(true)

        val updateTimeIsGreaterThanLastFilesystemUpdate = filesystem.lastUpdated + 1
        whenever(mockAssetRepository.getLastDistributionUpdate(filesystem.distributionType))
                .thenReturn(updateTimeIsGreaterThanLastFilesystemUpdate)

        sessionFsm.submitEvent(VerifyFilesystemAssets(filesystem))

        verify(mockFilesystemUtility).removeRootfsFilesFromFilesystem("${filesystem.id}")
        verify(mockStateObserver).onChanged(VerifyingFilesystemAssets)
        verify(mockStateObserver).onChanged(FilesystemHasRequiredAssets)
    }

    @Test
    fun `State is DistributionCopyFailed if filesystem assets are not up to date and copying fails`() = runBlocking {
        sessionFsm.setState(ExtractionSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getDistributionAssetsForExistingFilesystem(filesystem))
                .thenReturn(singleAssetList)
        whenever(mockFilesystemUtility.areAllRequiredAssetsPresent("${filesystem.id}", singleAssetList))
                .thenReturn(true)

        val updateTimeIsGreaterThanLastFilesystemUpdate = filesystem.lastUpdated + 1
        whenever(mockAssetRepository.getLastDistributionUpdate(filesystem.distributionType))
                .thenReturn(updateTimeIsGreaterThanLastFilesystemUpdate)
        whenever(mockFilesystemUtility.copyAssetsToFilesystem("${filesystem.id}", filesystem.distributionType))
                .thenThrow(Exception::class.java)

        sessionFsm.submitEvent(VerifyFilesystemAssets(filesystem))

        verify(mockStateObserver).onChanged(VerifyingFilesystemAssets)
        verify(mockStateObserver).onChanged(DistributionCopyFailed)
    }

    @Test
    fun `State is FilesystemIsMissingRequiredAssets if any assets are missing`() = runBlocking {
        sessionFsm.setState(ExtractionSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getDistributionAssetsForExistingFilesystem(filesystem))
                .thenReturn(singleAssetList)
        whenever(mockFilesystemUtility.areAllRequiredAssetsPresent("${filesystem.id}", singleAssetList))
                .thenReturn(false)

        sessionFsm.submitEvent(VerifyFilesystemAssets(filesystem))

        verify(mockStateObserver).onChanged(VerifyingFilesystemAssets)
        verify(mockStateObserver).onChanged(FilesystemIsMissingRequiredAssets)
    }
}