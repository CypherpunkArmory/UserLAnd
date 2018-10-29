package tech.ula.utils

import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository

@RunWith(MockitoJUnitRunner::class)
class SessionControllerTest {

    // Class dependencies

    val testFilesystem = Filesystem(name = "testFS", id = 1, lastUpdated = 0)

    @Mock
    lateinit var buildWrapper: BuildWrapper

    @Mock
    lateinit var assetRepository: AssetRepository

    @Mock
    lateinit var filesystemUtility: FilesystemUtility

    @Mock
    lateinit var assetPreferences: AssetPreferences

    @Mock
    lateinit var timeUtility: TimeUtility

    @Mock
    lateinit var networkUtility: NetworkUtility

    @Mock
    lateinit var filesystemDao: FilesystemDao

    @Mock
    lateinit var sessionDao: SessionDao

    lateinit var sessionController: SessionController

    @Before
    fun setup() {
        sessionController = SessionController(assetRepository, filesystemUtility, assetPreferences, timeUtility)
    }

    // TODO fix test
//    @Test
//    fun insertsAppsFilesystemIfItDidNotExist() {
//        val requiredFilesystemType = "testDist"
//        val fakeArchitecture = "testArch"
//        val appsFilesystem = Filesystem(0, "apps",
//                archType = fakeArchitecture, distributionType = requiredFilesystemType, isAppsFilesystem = true)
//
//        whenever(buildWrapper.getArchType()).thenReturn(fakeArchitecture)
//        whenever(filesystemDao.findAppsFilesystemByType(requiredFilesystemType))
//                .thenReturn(listOf())
//                .thenReturn(listOf(appsFilesystem))
//
//        val returnedFs = runBlocking { sessionController.findAppsFilesystems(requiredFilesystemType, filesystemDao, buildWrapper) }
//
//        verify(filesystemDao).insertFilesystem(appsFilesystem)
//        verify(filesystemDao, times(2)).findAppsFilesystemByType(requiredFilesystemType)
//        assertEquals(appsFilesystem.name, returnedFs.name)
//        assertEquals(appsFilesystem.archType, returnedFs.archType)
//        assertEquals(appsFilesystem.distributionType, returnedFs.distributionType)
//        assertEquals(appsFilesystem.isAppsFilesystem, returnedFs.isAppsFilesystem)
//    }

    // TODO fix test
//    @Test
//    fun insertsAppSessionIfItDidNotExist() {
//        val fakeArchitecture = "testArch"
//        val requiredFilesystemType = "testDist"
//        val appsFilesystem = Filesystem(0, "apps",
//                archType = fakeArchitecture, distributionType = requiredFilesystemType, isAppsFilesystem = true,
//                defaultUsername = "username", defaultPassword = "userland", defaultVncPassword = "userland")
//
//        val appName = "testApp"
//        val serviceType = "ssh"
//        val appSession = Session(0, name = appName, filesystemId = 0, filesystemName = "apps",
//                serviceType = serviceType, username = "username", clientType = "ConnectBot", isAppsSession = true)
//
//        whenever(sessionDao.findAppsSession(appName))
//                .thenReturn(listOf())
//                .thenReturn(listOf(appSession))
//
//        val returnedSession = runBlocking {
//            sessionController.findAppSession(appName, serviceType, appsFilesystem, sessionDao)
//        }
//
//        verify(sessionDao).insertSession(appSession)
//        verify(sessionDao, times(2)).findAppsSession(appName)
//        assertEquals(appSession, returnedSession)
//    }

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

        val result = sessionController.getDownloadRequirements(testFilesystem, assetLists, forceDownload, networkUtility)

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

        val result = sessionController.getDownloadRequirements(testFilesystem, assetLists, forceDownload, networkUtility)

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

        val result = sessionController.getDownloadRequirements(testFilesystem, assetLists, forceDownload, networkUtility)

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
        whenever(assetPreferences.getLastDistributionUpdate(testFilesystem.distributionType)).thenReturn(-1)
        `when`(filesystemUtility.areAllRequiredAssetsPresent(filesystemDirectoryName, distAssetList))
                .thenReturn(false)

        sessionController.ensureFilesystemHasRequiredAssets(testFilesystem)

        verify(filesystemUtility).copyDistributionAssetsToFilesystem(filesystemDirectoryName, testFilesystem.distributionType)
        verify(filesystemUtility).removeRootfsFilesFromFilesystem(filesystemDirectoryName)
    }

    @Test
    fun copiesDistributionAssetsToFilesystemIfTheyAreOutdated() {
        val distAssetList = listOf(Asset("name", "dist", "arch", 0))
        val filesystemDirectoryName = "${testFilesystem.id}"
        `when`(assetRepository.getDistributionAssetsList(testFilesystem.distributionType))
                .thenReturn(distAssetList)
        whenever(assetPreferences.getLastDistributionUpdate(testFilesystem.distributionType)).thenReturn(1)

        sessionController.ensureFilesystemHasRequiredAssets(testFilesystem)

        verify(filesystemUtility).copyDistributionAssetsToFilesystem(filesystemDirectoryName, testFilesystem.distributionType)
        verify(filesystemUtility).removeRootfsFilesFromFilesystem(filesystemDirectoryName)
    }
}