package tech.ula.model.repositories

import com.nhaarman.mockitokotlin2.* // ktlint-disable no-wildcard-imports
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.utils.AssetPreferences
import tech.ula.utils.ConnectionUtility
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class AssetRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var assetPreferences: AssetPreferences

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
        assetRepository = AssetRepository(applicationFilesDirPath,
                assetPreferences, connectionUtility)
    }

    @Test
    fun `Retrieves cached assets if remote are unavailable`() {
        // TODO
    }

    @Test
    fun allTypesOfCachedAssetListsAreRetrieved() {
        whenever(connectionUtility.httpsHostIsReachable("github.com")).thenReturn(false)

        assetRepository.getAllAssetLists(distType, archType)
        verify(assetPreferences).getAssetLists(allAssetListTypes)
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
        `when`(assetPreferences.getAssetLists(distTypeAssetLists)).thenReturn(assetListWithRootfsFile)

        val filesystem = Filesystem(-1, distributionType = distType, archType = archType)
        val returnedAssetList = assetRepository.getDistributionAssetsForExistingFilesystem(filesystem)

        assertTrue(returnedAssetList.size == 1)
        assertFalse(returnedAssetList.any { it.name == "rootfs.tar.gz" })
    }

    @Test
    fun assetsNeedToBeUpdatedIfTheyAreNonexistent() {
        val asset = Asset("name", distType, archType, Long.MAX_VALUE)

        assertTrue(assetRepository.doesAssetNeedToUpdated(asset))
//        verify(timestampPreferences, never()).getSavedTimestampForFile(anyString())
    }

    @Test
    fun assetWillBeUpdatedIfLocalTimestampIsEarlierThanRemote() {
        val asset = Asset("late", distType, archType, Long.MAX_VALUE)
        tempFolder.newFolder("dist")
        File("${tempFolder.root.path}/${asset.pathName}").createNewFile()
//        `when`(timestampPreferences.getSavedTimestampForFile(asset.concatenatedName))
//                .thenReturn(Long.MIN_VALUE)

        assertTrue(assetRepository.doesAssetNeedToUpdated(asset))
    }

    @Test
    fun assetWillNotBeUpdatedIfLocalTimestampIsLaterThanRemote() {
        val asset = Asset("early", distType, archType, Long.MIN_VALUE)
        tempFolder.newFolder("dist")
        File("${tempFolder.root.path}/${asset.pathName}").createNewFile()
//        `when`(timestampPreferences.getSavedTimestampForFile(asset.concatenatedName))
//                .thenReturn(Long.MAX_VALUE)

        assertFalse(assetRepository.doesAssetNeedToUpdated(asset))
    }
}