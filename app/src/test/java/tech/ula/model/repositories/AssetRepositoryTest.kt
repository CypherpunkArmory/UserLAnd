package tech.ula.model.repositories

import com.nhaarman.mockitokotlin2.* // ktlint-disable no-wildcard-imports
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import tech.ula.utils.AssetListPreferenceUtility
import tech.ula.utils.ConnectionUtility
import tech.ula.utils.TimestampPreferenceUtility
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

@RunWith(MockitoJUnitRunner::class)
class AssetRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var timestampPreferenceUtility: TimestampPreferenceUtility

    @Mock
    lateinit var assetListPreferenceUtility: AssetListPreferenceUtility

    @Mock
    lateinit var connectionUtility: ConnectionUtility

    lateinit var applicationFilesDirPath: String

    private val archType = "arch"
    private val distType = "dist"
    val allAssetListTypes = listOf(
            "support" to "all",
            "support" to archType,
            distType to "all",
            distType to archType
    )

    lateinit var assetRepository: AssetRepository

    @Before
    fun setup() {
        applicationFilesDirPath = tempFolder.root.path
        assetRepository = AssetRepository(archType, distType, applicationFilesDirPath,
                timestampPreferenceUtility, assetListPreferenceUtility, connectionUtility)
    }

    @Test
    fun allTypesOfCachedAssetListsAreRetrieved() {
        assetRepository.getCachedAssetLists()
        verify(assetListPreferenceUtility).getAssetLists(allAssetListTypes)
    }

    @Test
    fun fetchingDistributionAssetListsRemovesRootfsFiles() {
        val distTypeAssetLists = allAssetListTypes.filter {
            (assetType, _) ->
            assetType == distType
        }
        val asset1 = Asset("name", distType, archType, 0)
        val asset2 = Asset("rootfs.tar.gz", distType, archType, 0)
        val assetListWithRootfsFile = listOf(listOf(asset1, asset2))
        `when`(assetListPreferenceUtility.getAssetLists(distTypeAssetLists)).thenReturn(assetListWithRootfsFile)

        val returnedAssetList = assetRepository.getDistributionAssetsList(distType)

        assertTrue(returnedAssetList.size == 1)
        assertFalse(returnedAssetList.any { it.name == "rootfs.tar.gz" })
    }

    @Test
    fun formatsUrlBasedOnHttpsAccessibility() {
        val inputStream = ByteArrayInputStream("asset 0".toByteArray()) as InputStream
        `when`(connectionUtility.getUrlInputStream(anyString())).thenReturn(inputStream)

        val allUrlsWithoutProtocols = allAssetListTypes.map { (dist, arch) ->
            "://github.com/CypherpunkArmory/UserLAnd-Assets-" +
                    "$dist/raw/master/assets/$arch/assets.txt"
        }

        argumentCaptor<String>().apply {
            assetRepository.retrieveAllRemoteAssetLists(httpsIsAccessible = true)
            verify(connectionUtility, times(4)).getUrlInputStream(capture())
            allUrlsWithoutProtocols.forEach {
                assertTrue(allValues.contains("https$it"))
            }
        }

        argumentCaptor<String>().apply {
            assetRepository.retrieveAllRemoteAssetLists(httpsIsAccessible = false)
            // The mock tracks how many times a method is invoked since instantiation, so it will be
            // called 4 times in the first part of the test and another 4 here.
            verify(connectionUtility, times(4 + 4)).getUrlInputStream(capture())
            allUrlsWithoutProtocols.forEach {
                assertTrue(allValues.contains("http$it"))
            }
        }
    }

    @Test
    fun assetsNeedToBeUpdatedIfTheyAreNonexistent() {
        val asset = Asset("name", distType, archType, Long.MAX_VALUE)

        assertTrue(assetRepository.doesAssetNeedToUpdated(asset))
        verify(timestampPreferenceUtility, never()).getSavedTimestampForFile(anyString())
    }

    @Test
    fun assetWillBeUpdatedIfLocalTimestampIsEarlierThanRemote() {
        val asset = Asset("late", distType, archType, Long.MAX_VALUE)
        tempFolder.newFolder("dist")
        File("${tempFolder.root.path}/${asset.pathName}").createNewFile()
        `when`(timestampPreferenceUtility.getSavedTimestampForFile(asset.concatenatedName))
                .thenReturn(Long.MIN_VALUE)

        assertTrue(assetRepository.doesAssetNeedToUpdated(asset))
    }

    @Test
    fun assetWillNotBeUpdatedIfLocalTimestampIsLaterThanRemote() {
        val asset = Asset("early", distType, archType, Long.MIN_VALUE)
        tempFolder.newFolder("dist")
        File("${tempFolder.root.path}/${asset.pathName}").createNewFile()
        `when`(timestampPreferenceUtility.getSavedTimestampForFile(asset.concatenatedName))
                .thenReturn(Long.MAX_VALUE)

        assertFalse(assetRepository.doesAssetNeedToUpdated(asset))
    }
}