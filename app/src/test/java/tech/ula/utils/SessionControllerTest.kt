package tech.ula.utils

import android.content.res.Resources
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.R
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository

@RunWith(MockitoJUnitRunner::class)
class SessionControllerTest {

    // Class dependencies

    val testFilesystem = Filesystem(name = "testFS", id = 1)

    @Mock
    lateinit var resourcesFetcher: ResourcesFetcher

    @Mock
    lateinit var resources: Resources

    @Mock
    lateinit var assetRepository: AssetRepository

    @Mock
    lateinit var filesystemUtility: FilesystemUtility

    val progressBarUpdater: (String, String) -> Unit = {
        step: String, details: String ->
        System.out.println("progress bar updater called with step: $step and details: $details")
    }

    @Mock
    lateinit var dialogBroadcaster: (String) -> Unit
//    val dialogBroadcaster: (String) -> Unit = {
//        type ->
//        System.out.println("dialog broadcast called with type: $type")
//    }

    // Function dependencies

    @Mock
    lateinit var networkUtility: NetworkUtility

    @Mock
    lateinit var downloadBroadcastReceiver: DownloadBroadcastReceiver

    @Mock
    lateinit var downloadUtility: DownloadUtility

    val filesystemExtractLogger: (String) -> Unit = {
        System.out.println("filesystem extract logger called with line: $it")
    }

    val session = Session(name = "testSession", id = 1, filesystemName = "testFS", filesystemId = 1)

    lateinit var sessionController: SessionController

    @Before
    fun setup() {
        `when`(resourcesFetcher.getAppResources()).thenReturn(resources)
        sessionController = SessionController(testFilesystem, resourcesFetcher, assetRepository,
                filesystemUtility, progressBarUpdater, dialogBroadcaster)
    }

    @Test
    fun retrievesCachedAssetsIfNetworkIsUnavaialable() {
        val asset = Asset("name", "dist", "arch", 0)
        `when`(resources.getString(R.string.progress_fetching_asset_lists))
                .thenReturn("retrievesCachedAssetsIfNetworkIsUnavailable")
        `when`(networkUtility.networkIsActive()).thenReturn(false)
        `when`(assetRepository.getCachedAssetLists()).thenReturn(listOf(listOf(asset)))

        val assetLists = sessionController.getAssetLists(networkUtility)

        val expectedNumberOfLists = 1
        val expectedNumberOfAssetsInList = 1
        val retrievedAsset = assetLists.first().first()
        verify(assetRepository).getCachedAssetLists()
        assertEquals(expectedNumberOfLists, assetLists.size)
        assertEquals(expectedNumberOfAssetsInList, assetLists.first().size)
        assertEquals(asset, retrievedAsset)
    }

    @Test
    fun retrievesRemoteAssetListsWhenNetworkIsAvailable() {
        val asset = Asset("name", "dist", "arch", 0)
        `when`(resources.getString(R.string.progress_fetching_asset_lists))
                .thenReturn("retrievesRemoteAssetListsWhenNetworkIsAvailable")
        `when`(networkUtility.networkIsActive()).thenReturn(true)
        `when`(networkUtility.httpsIsAccessible()).thenReturn(true)
        `when`(assetRepository.retrieveAllRemoteAssetLists(true)).thenReturn(listOf(listOf(asset)))

        val assetLists = sessionController.getAssetLists(networkUtility)

        val expectedNumberOfLists = 1
        val expectedNumberOfAssetsInList = 1
        val retrievedAsset = assetLists.first().first()
        verify(assetRepository).retrieveAllRemoteAssetLists(true)
        assertEquals(expectedNumberOfLists, assetLists.size)
        assertEquals(expectedNumberOfAssetsInList, assetLists.first().size)
        assertEquals(asset, retrievedAsset)
    }

    @Test
    fun broadcastsWhenRetrievedAssetListsAreEmpty() {
        `when`(resources.getString(R.string.progress_fetching_asset_lists))
                .thenReturn("broadcastsWhenRetrievedAssetListsAreEmpty")
        `when`(networkUtility.networkIsActive()).thenReturn(false)
        `when`(assetRepository.getCachedAssetLists()).thenReturn(listOf(listOf()))

        val assetLists = sessionController.getAssetLists(networkUtility)

        verify(dialogBroadcaster).invoke("errorFetchingAssetLists")
    }

    @Test
    fun exitsEarlyWithTrueReturnIfALargeAssetIsRequiredAndWifiIsDisabled() {
        val asset = Asset("rootfs.tar.gz", "dist", "arch", 0)
        val assetLists = listOf(listOf(asset))
        val forceDownload = false

        `when`(assetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        `when`(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${testFilesystem.id}"))
                .thenReturn(false)
        `when`(networkUtility.wifiIsEnabled()).thenReturn(false)

        val result = runBlocking { sessionController.downloadRequirements(assetLists, forceDownload, downloadBroadcastReceiver,
                downloadUtility, networkUtility) }

        verify(dialogBroadcaster).invoke("wifiRequired")
        verify(downloadUtility, never()).downloadRequirements(any())
        assertTrue(result)
    }

    @Test
    fun forceDownloadsAllowsDownloadsWithoutWifi() {
        val asset = Asset("rootfs.tar.gz", "dist", "arch", 0)
        val assetLists = listOf(listOf(asset))
        val forceDownload = true

        `when`(assetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        `when`(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${testFilesystem.id}"))
                .thenReturn(false)
        `when`(networkUtility.wifiIsEnabled()).thenReturn(false)

        val result = runBlocking { sessionController.downloadRequirements(assetLists, forceDownload, downloadBroadcastReceiver,
                downloadUtility, networkUtility) }

        val expectedRequirements = listOf(asset)
        verify(dialogBroadcaster, never()).invoke("wifiRequired")
        verify(downloadUtility).downloadRequirements(expectedRequirements)
        assertFalse(result)
    }

    @Test
    fun ignoresRootfsFilesIfFilesystemIsExtracted() {
        val rootfsAsset = Asset("rootfs.tar.gz", "dist", "arch", 0)
        val regularAsset = Asset("name", "dist", "arch", 0)
        val assetLists = listOf(listOf(rootfsAsset, regularAsset))
        val forceDownload = false

        `when`(assetRepository.doesAssetNeedToUpdated(rootfsAsset)).thenReturn(true)
        `when`(assetRepository.doesAssetNeedToUpdated(regularAsset)).thenReturn(true)

        `when`(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${testFilesystem.id}"))
                .thenReturn(true)
        `when`(networkUtility.wifiIsEnabled()).thenReturn(true)

        val result = runBlocking { sessionController.downloadRequirements(assetLists, forceDownload,
                downloadBroadcastReceiver, downloadUtility, networkUtility) }

        val expectedRequirements = listOf(regularAsset)
        verify(downloadUtility).downloadRequirements(expectedRequirements)
        assertFalse(result)
    }
}