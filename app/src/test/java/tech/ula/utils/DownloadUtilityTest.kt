package tech.ula.utils

import android.app.DownloadManager
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.* // ktlint-disable no-wildcard-imports
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import java.io.File
import kotlin.text.Charsets.UTF_8

@RunWith(MockitoJUnitRunner::class)
class DownloadUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var assetPreferences: AssetPreferences

    @Mock
    lateinit var downloadManagerWrapper: DownloadManagerWrapper

    @Mock
    lateinit var requestReturn: DownloadManager.Request

    lateinit var downloadDirectory: File

    lateinit var asset1: Asset
    lateinit var asset2: Asset
    lateinit var assetList: List<Asset>
    lateinit var downloadUtility: DownloadUtility

    val branch = "master"

    @Before
    fun setup() {
        downloadDirectory = tempFolder.newFolder("downloads")
        `when`(downloadManagerWrapper.getDownloadsDirectory()).thenReturn(downloadDirectory)
        downloadUtility = DownloadUtility(assetPreferences, downloadManagerWrapper, applicationFilesDir = tempFolder.root)

        asset1 = Asset("name1", "distType1", "archType1", 0)
        asset2 = Asset("name2", "distType2", "archType2", 0)
        assetList = listOf(asset1, asset2)

        val url1 = getDownloadUrl(asset1.distributionType, asset1.architectureType, asset1.name)
        val destination1 = asset1.concatenatedName
        `when`(downloadManagerWrapper.generateDownloadRequest(url1, destination1)).thenReturn(requestReturn)

        val url2 = getDownloadUrl(asset2.distributionType, asset2.architectureType, asset2.name)
        val destination2 = asset2.concatenatedName
        `when`(downloadManagerWrapper.generateDownloadRequest(url2, destination2)).thenReturn(requestReturn)
    }

    fun getDownloadUrl(distType: String, archType: String, name: String): String {
        return "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/$name"
    }

    @Test
    fun enqueuesDownload() {
        downloadUtility.downloadRequirements(assetList)

        verify(downloadManagerWrapper, times(2)).enqueue(requestReturn)
    }

    @Test
    fun `Clears download directory of userland files`() {
        val asset1DownloadsFile = File("${downloadDirectory.path}/${asset1.concatenatedName}")
        val asset2DownloadsFile = File("${downloadDirectory.path}/${asset2.concatenatedName}")
        asset1DownloadsFile.createNewFile()
        asset2DownloadsFile.createNewFile()
        assertTrue(asset1DownloadsFile.exists())
        assertTrue(asset2DownloadsFile.exists())

        downloadUtility.downloadRequirements(assetList)

        assertFalse(asset1DownloadsFile.exists())
        assertFalse(asset2DownloadsFile.exists())
    }

    @Test
    fun deletesPreviousDownloads() {
        tempFolder.newFolder("distType1")
        tempFolder.newFolder("distType2")
        val asset1File = File("${tempFolder.root.path}/distType1/name1")
        val asset2File = File("${tempFolder.root.path}/distType2/name2")
        asset1File.createNewFile()
        asset2File.createNewFile()
        assertTrue(asset1File.exists())
        assertTrue(asset2File.exists())

        val asset1DownloadsFile = File("${downloadDirectory.path}/${asset1.concatenatedName}")
        val asset2DownloadsFile = File("${downloadDirectory.path}/${asset2.concatenatedName}")
        asset1DownloadsFile.createNewFile()
        asset2DownloadsFile.createNewFile()
        assertTrue(asset1DownloadsFile.exists())
        assertTrue(asset2DownloadsFile.exists())

        downloadUtility.downloadRequirements(assetList)
        verify(downloadManagerWrapper, times(2)).enqueue(requestReturn)

        assertFalse(asset1File.exists())
        assertFalse(asset2File.exists())
        assertFalse(asset1DownloadsFile.exists())
        assertFalse(asset2DownloadsFile.exists())
    }

    @Test
    fun setsTimestampWhenTitleIsRelevant() {
        val id = 1L
        `when`(downloadManagerWrapper.getDownloadTitle(id)).thenReturn(asset1.concatenatedName)

        downloadUtility.setTimestampForDownloadedFile(id)

        // TODO update test
//        verify(timestampPreferences).setSavedTimestampForFileToNow(asset1.concatenatedName)
    }

    @Test
    fun ignoresIrrelevantDownloads() {
        val id = 1L
        val titleName = "notuserland"
        `when`(downloadManagerWrapper.getDownloadTitle(id)).thenReturn(titleName)

        downloadUtility.setTimestampForDownloadedFile(id)

        // TODO update test
//        verify(timestampPreferences, never()).setSavedTimestampForFileToNow(ArgumentMatchers.anyString())
    }

    @Test
    fun movesAssetsToCorrectLocationAndUpdatesPermissions() {
        val asset1DownloadsFile = File("${downloadDirectory.path}/${asset1.concatenatedName}")
        val asset2DownloadsFile = File("${downloadDirectory.path}/${asset2.concatenatedName}")
        asset1DownloadsFile.createNewFile()
        asset2DownloadsFile.createNewFile()

        val asset1File = File("${tempFolder.root.path}/distType1/name1")
        val asset2File = File("${tempFolder.root.path}/distType2/name2")
        assertFalse(asset1File.exists())
        assertFalse(asset2File.exists())

        downloadUtility.moveAssetsToCorrectLocalDirectory()

        assertFalse(asset1DownloadsFile.exists())
        assertFalse(asset2DownloadsFile.exists())
        assertTrue(asset1File.exists())
        assertTrue(asset2File.exists())

        var output = ""
        val proc1 = Runtime.getRuntime().exec("ls -l ${asset1File.path}")

        proc1.inputStream.bufferedReader(UTF_8).forEachLine { output += it }
        val permissions1 = output.substring(0, 10)
        assertTrue(permissions1 == "-rwxrwxrwx")

        output = ""
        val proc2 = Runtime.getRuntime().exec("ls -l ${asset2File.path}")

        proc2.inputStream.bufferedReader(UTF_8).forEachLine { output += it }
        val permissions2 = output.substring(0, 10)
        assertTrue(permissions2 == "-rwxrwxrwx")
    }
}