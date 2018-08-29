package tech.ula.utils

import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.repositories.AssetRepository

@RunWith(MockitoJUnitRunner::class)
class SessionControllerTest {

    // Class dependencies

    val testFilesystem = Filesystem(name = "testFS", id = 1)

    @Mock
    lateinit var assetRepository: AssetRepository

    @Mock
    lateinit var filesystemUtility: FilesystemUtility

    @Mock
    lateinit var networkUtility: NetworkUtility

    lateinit var sessionController: SessionController

    @Before
    fun setup() {
        sessionController = SessionController(testFilesystem, assetRepository, filesystemUtility)
    }

    @Test
    fun retrievesRemoteAssetLists() {
        val asset = Asset("name", "dist", "arch", 0)
        `when`(assetRepository.retrieveAllRemoteAssetLists()).thenReturn(listOf(listOf(asset)))

        val assetLists = sessionController.getAssetLists()

        val expectedNumberOfLists = 1
        val expectedNumberOfAssetsInList = 1
        val retrievedAsset = assetLists.first().first()
        verify(assetRepository).retrieveAllRemoteAssetLists()
        assertEquals(expectedNumberOfLists, assetLists.size)
        assertEquals(expectedNumberOfAssetsInList, assetLists.first().size)
        assertEquals(asset, retrievedAsset)
    }

    @Test
    fun retrievesCachedAssetsIfRemoteRetrievalFails() {
        val asset = Asset("name", "dist", "arch", 0)
        `when`(assetRepository.retrieveAllRemoteAssetLists()).thenThrow(Exception())
        `when`(assetRepository.getCachedAssetLists()).thenReturn(listOf(listOf(asset)))

        val assetLists = sessionController.getAssetLists()

        val expectedNumberOfLists = 1
        val expectedNumberOfAssetsInList = 1
        val retrievedAsset = assetLists.first().first()
        verify(assetRepository).getCachedAssetLists()
        assertEquals(expectedNumberOfLists, assetLists.size)
        assertEquals(expectedNumberOfAssetsInList, assetLists.first().size)
        assertEquals(asset, retrievedAsset)
    }

    @Test
    fun returnsRequiresWifiResultWhenNecessary() {
        val asset = Asset("rootfs.tar.gz", "dist", "arch", 0)
        val assetLists = listOf(listOf(asset))
        val forceDownload = false

        `when`(assetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        `when`(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${testFilesystem.id}"))
                .thenReturn(false)
        `when`(networkUtility.wifiIsEnabled()).thenReturn(false)

        val result = sessionController.getDownloadRequirements(assetLists, forceDownload, networkUtility)

        assertTrue(result is RequiresWifiResult)
    }

    @Test
    fun returnsRequiresAssetsResultWithLargeAssetWhenDownloadsAreForced() {
        val asset = Asset("rootfs.tar.gz", "dist", "arch", 0)
        val assetLists = listOf(listOf(asset))
        val forceDownload = true

        `when`(assetRepository.doesAssetNeedToUpdated(asset)).thenReturn(true)
        `when`(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${testFilesystem.id}"))
                .thenReturn(false)

        val result = sessionController.getDownloadRequirements(assetLists, forceDownload, networkUtility)

        val expectedRequirements = listOf(asset)
        assertTrue(result is RequiredAssetsResult)
        if (result is RequiredAssetsResult) assertEquals(expectedRequirements, result.assetList)
    }

    @Test
    fun doesNotAddRootfsFilesToRequiredDownloadsIfFilesystemIsExtracted() {
        val rootfsAsset = Asset("rootfs.tar.gz", "dist", "arch", 0)
        val regularAsset = Asset("name", "dist", "arch", 0)
        val assetLists = listOf(listOf(rootfsAsset, regularAsset))
        val forceDownload = false

        `when`(assetRepository.doesAssetNeedToUpdated(rootfsAsset)).thenReturn(true)
        `when`(assetRepository.doesAssetNeedToUpdated(regularAsset)).thenReturn(true)

        `when`(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("${testFilesystem.id}"))
                .thenReturn(true)

        val result = sessionController.getDownloadRequirements(assetLists, forceDownload, networkUtility)

        val expectedRequirements = listOf(regularAsset)
        assertTrue(result is RequiredAssetsResult)
        if (result is RequiredAssetsResult) assertEquals(expectedRequirements, result.assetList)
    }

    @Test
    fun copiesDistributionAssetsToFilesystemIfTheyAreMissing() {
        val distAssetList = listOf(Asset("name", "dist", "arch", 0))
        val filesystemDirectoryName = "${testFilesystem.id}"
        `when`(assetRepository.getDistributionAssetsList(testFilesystem.distributionType))
                .thenReturn(distAssetList)
        `when`(filesystemUtility.areAllRequiredAssetsPresent(filesystemDirectoryName, distAssetList))
                .thenReturn(false)

        sessionController.ensureFilesystemHasRequiredAssets()

        verify(filesystemUtility).copyDistributionAssetsToFilesystem(filesystemDirectoryName, testFilesystem.distributionType)
        verify(filesystemUtility).removeRootfsFilesFromFilesystem(filesystemDirectoryName)
    }
}