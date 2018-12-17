package tech.ula.model.state

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
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
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.DownloadUtility
import tech.ula.utils.FilesystemUtility
import java.io.File
import java.lang.Exception

@RunWith(MockitoJUnitRunner::class)
class SessionStartupFsmTest {

    @Mock lateinit var activeSessionLiveData: MutableLiveData<List<Session>>

    // Mocks

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

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

    val filesDir = File("test")

    val testLogger: (String) -> Unit = {}

    @Before
    fun setup() {
        activeSessionLiveData = MutableLiveData()

        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)
        whenever(mockUlaDatabase.filesystemDao()).thenReturn(mockFilesystemDao)
        whenever(mockSessionDao.findActiveSessions()).thenReturn(activeSessionLiveData)

        sessionFsm = SessionStartupFsm(mockUlaDatabase, mockAssetRepository, mockFilesystemUtility, mockDownloadUtility)
    }

    @After
    fun teardown() {
        activeSessionLiveData = MutableLiveData()
    }

    @Test
    fun `Only allows correct state transitions`() {
        sessionFsm.getState().observeForever(mockStateObserver)

        val testEvent = SessionSelected(inactiveSession)
        val testState = WaitingForSessionSelection
        val events = listOf(
                SessionSelected(inactiveSession),
                RetrieveAssetLists(filesystem),
                GenerateDownloads(filesystem, assetLists),
                DownloadAssets(singleAssetList),
                AssetDownloadComplete(0),
                CopyDownloadsToLocalStorage(File("test"), assetLists),
                ExtractFilesystem(filesystem, testLogger),
                VerifyFilesystemAssets(filesystem)
        )
        val states = listOf(
                IncorrectTransition(testEvent, testState),
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
                DownloadingRequirements,
                DownloadsHaveSucceeded,
                DownloadsHaveFailed,
                CopyingFilesToRequiredDirectories,
                CopyingSucceeded,
                CopyingFailed,
                ExtractingFilesystem,
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
    fun `Exits early if incorrect transition event submitted`() {
        val state = WaitingForSessionSelection
        sessionFsm.setState(state)
        sessionFsm.getState().observeForever(mockStateObserver)

        val event = RetrieveAssetLists(filesystem)
        sessionFsm.submitEvent(event)

        verify(mockStateObserver, times(1)).onChanged(IncorrectTransition(event, state))
        verify(mockStateObserver, times(2)).onChanged(any()) // Observes when registered and again on state emission
    }

    @Test
    fun `Initial state is WaitingForSessionSelection`() {
        sessionFsm.getState().observeForever(mockStateObserver)

        verify(mockStateObserver).onChanged(WaitingForSessionSelection)
    }

    @Test
    fun `State is SingleSessionSupported if active session is not selected one`() {
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

        verify(mockStateObserver).onChanged(SessionIsReadyForPreparation(inactiveSession, filesystem))
    }

    @Test
    fun `State is RetrievingAssetLists and then AssetListsRetrieved`() {
        sessionFsm.setState(SessionIsReadyForPreparation(inactiveSession, filesystem))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getAllAssetLists(filesystem.distributionType, filesystem.archType)).thenReturn(assetLists)

        sessionFsm.submitEvent(RetrieveAssetLists(filesystem))

        verify(mockStateObserver).onChanged(RetrievingAssetLists)
        verify(mockStateObserver).onChanged(AssetListsRetrievalSucceeded(assetLists))
    }

    @Test
    fun `State is AssetListsRetrievalFailed if remote and cached assets cannot be fetched`() {
        sessionFsm.setState(SessionIsReadyForPreparation(inactiveSession, filesystem))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.getAllAssetLists(filesystem.distributionType, filesystem.archType)).thenReturn(emptyAssetLists)

        sessionFsm.submitEvent(RetrieveAssetLists(filesystem))

        // TODO determine why this verification isn't needed but is for downloadsrequired tests
        verify(mockStateObserver).onChanged(SessionIsReadyForPreparation(inactiveSession, filesystem))
        verify(mockStateObserver).onChanged(RetrievingAssetLists)
        verify(mockStateObserver).onChanged(AssetListsRetrievalFailed)
    }

    @Test
    fun `State is DownloadsRequired and largeDownloadRequired is true if a rootfs needs to be downloaded`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded(assetListsWithLargeAsset))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        whenever(mockAssetRepository.doesAssetNeedToUpdated(largeAsset)).thenReturn(true)
        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(false)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, assetListsWithLargeAsset))

        verify(mockStateObserver).onChanged(AssetListsRetrievalSucceeded(assetListsWithLargeAsset))
        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
        verify(mockStateObserver).onChanged(DownloadsRequired(assetListsWithLargeAsset.flatten(), largeDownloadRequired = true))
    }

    @Test
    fun `State is DownloadsRequired and largeDownloadRequired is false if a rootfs needs updating but the filesystem already exists`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded(assetListsWithLargeAsset))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        whenever(mockAssetRepository.doesAssetNeedToUpdated(largeAsset)).thenReturn(true)
        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(true)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, assetListsWithLargeAsset))

        verify(mockStateObserver).onChanged(AssetListsRetrievalSucceeded(assetListsWithLargeAsset))
        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
        verify(mockStateObserver).onChanged(DownloadsRequired(singleAssetList, largeDownloadRequired = false))
    }

    @Test
    fun `State is DownloadsRequired and includes false if downloads do not include rootfs`() {
        sessionFsm.setState(AssetListsRetrievalSucceeded(assetLists))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockAssetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)

        sessionFsm.submitEvent(GenerateDownloads(filesystem, assetLists))

        // TODO and not needed here?
        verify(mockStateObserver).onChanged(GeneratingDownloadRequirements)
        verify(mockStateObserver).onChanged(DownloadsRequired(assetLists.flatten(), largeDownloadRequired = false))
    }

    @Test
    fun `State is DownloadsHaveSucceeded once downloads succeed`() {
        sessionFsm.setState(DownloadsRequired(singleAssetList, false))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockDownloadUtility.downloadRequirements(singleAssetList)).thenReturn(listOf(Pair(asset, 0L)))
        whenever(mockDownloadUtility.downloadedSuccessfully(0L)).thenReturn(true)

        sessionFsm.submitEvent(DownloadAssets(singleAssetList))
        sessionFsm.submitEvent(AssetDownloadComplete(0))

        verify(mockStateObserver).onChanged(DownloadingRequirements)
        verify(mockStateObserver).onChanged(DownloadsHaveSucceeded)
    }

    @Test
    fun `State is DownloadsHaveFailed if any downloads fail`() {
        val assetList = listOf(asset, largeAsset)
        sessionFsm.setState(DownloadsRequired(assetList, true))
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockDownloadUtility.downloadRequirements(assetList)).thenReturn(listOf(Pair(asset, 0L), Pair(largeAsset, 1L)))
        whenever(mockDownloadUtility.downloadedSuccessfully(0)).thenReturn(true)
        whenever(mockDownloadUtility.downloadedSuccessfully(1)).thenReturn(false)

        sessionFsm.submitEvent(DownloadAssets(assetList))
        sessionFsm.submitEvent(AssetDownloadComplete(0))
        sessionFsm.submitEvent(AssetDownloadComplete(1))

        verify(mockStateObserver).onChanged(DownloadingRequirements)
        verify(mockStateObserver).onChanged(DownloadsHaveFailed)
    }

    @Test
    fun `State is CopyingSucceeded if files are moved to correct subdirectories`() {
        sessionFsm.setState(DownloadsHaveSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        sessionFsm.submitEvent(CopyDownloadsToLocalStorage(filesDir, assetLists))

        verify(mockStateObserver).onChanged(CopyingFilesToRequiredDirectories)
        verify(mockStateObserver).onChanged(CopyingSucceeded)
        verify(mockDownloadUtility).moveAssetsToCorrectLocalDirectory()
    }

    @Test
    fun `State is CopyingFailed if a problem arises`() {
        sessionFsm.setState(DownloadsHaveSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        // TODO research how throws annotation actually wraps jvm interop
        whenever(mockDownloadUtility.moveAssetsToCorrectLocalDirectory()).thenThrow(Exception::class.java)

        sessionFsm.submitEvent(CopyDownloadsToLocalStorage(filesDir, assetLists))

        verify(mockStateObserver).onChanged(CopyingFilesToRequiredDirectories)
        verify(mockStateObserver).onChanged(CopyingFailed)
    }

    @Test
    fun `Exits early if filesystem is already extracted`() {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(true)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem, testLogger))

        verify(mockFilesystemUtility, times(1)).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        verify(mockStateObserver).onChanged(ExtractionSucceeded)
    }

    @Test
    fun `State is CopyingFailed if copying assets to filesystem fails`() {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")).thenReturn(false)
        // TODO throws here too
        whenever(mockFilesystemUtility.copyAssetsToFilesystem("${filesystem.id}", filesystem.distributionType)).thenThrow(Exception::class.java)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem, testLogger))

        verify(mockFilesystemUtility, times(1)).hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}")
        verify(mockStateObserver).onChanged(DistributionCopyFailed)
    }

    @Test
    fun `State is ExtractionSucceeded if extraction succeeds`() {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.isExtractionComplete("${filesystem.id}")).thenReturn(true)
        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}"))
                .thenReturn(false)
                .thenReturn(true)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem, testLogger))

        verify(mockStateObserver).onChanged(ExtractingFilesystem)
        verify(mockStateObserver).onChanged(ExtractionSucceeded)
    }

    @Test
    fun `State is ExtractionFailed if extraction fails`() {
        sessionFsm.setState(CopyingSucceeded)
        sessionFsm.getState().observeForever(mockStateObserver)

        whenever(mockFilesystemUtility.isExtractionComplete("${filesystem.id}")).thenReturn(true)
        whenever(mockFilesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${filesystem.id}"))
                .thenReturn(false)
                .thenReturn(false)

        sessionFsm.submitEvent(ExtractFilesystem(filesystem, testLogger))

        verify(mockStateObserver).onChanged(ExtractingFilesystem)
        verify(mockStateObserver).onChanged(ExtractionFailed)
    }

    @Test
    fun `State is FilesystemHasRequiredAssets if all assets are present`() {
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
    fun `State is FilesystemHasRequiredAssets if it needs to copy filesystem assets and succeeds`() {
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
    fun `State is DistributionCopyFailed if filesystem assets are not up to date and copying fails`() {
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
    fun `State is FilesystemIsMissingRequiredAssets if any assets are missing`() {
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