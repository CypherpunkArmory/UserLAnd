package tech.ula.utils

import android.app.DownloadManager
import android.database.Cursor
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
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
    lateinit var downloadManager: DownloadManager

    @Mock
    lateinit var timestampPreferences: TimestampPreferences

    @Mock
    lateinit var downloadManagerWrapper: DownloadManagerWrapper

    @Mock
    lateinit var requestReturn: DownloadManager.Request

    @Mock
    lateinit var queryReturn: DownloadManager.Query

    @Mock
    lateinit var cursor: Cursor

    lateinit var downloadDirectory: File

    lateinit var asset1: Asset
    lateinit var asset2: Asset
    lateinit var assetList: List<Asset>
    lateinit var downloadUtility: DownloadUtility

    val branch = "master"

    @Before
    fun setup() {
        downloadDirectory = tempFolder.newFolder("downloads")
        downloadUtility = DownloadUtility(downloadManager, timestampPreferences, downloadManagerWrapper,
                downloadDirectory, applicationFilesDir = tempFolder.root)

        asset1 = Asset("name1", "distType1", "archType1", 0)
        asset2 = Asset("name2", "distType2", "archType2", 0)
        assetList = listOf(asset1, asset2)

        val url1 = getDownloadUrl(asset1.distributionType, asset1.architectureType, asset1.name)
        val destination1 = "UserLAnd:${asset1.concatenatedName}"
        `when`(downloadManagerWrapper.generateDownloadRequest(url1, destination1)).thenReturn(requestReturn)

        val url2 = getDownloadUrl(asset1.distributionType, asset1.architectureType, asset1.name)
        val destination2 = "UserLAnd:${asset1.concatenatedName}"
        `when`(downloadManagerWrapper.generateDownloadRequest(url2, destination2)).thenReturn(requestReturn)
    }

    fun getDownloadUrl(distType: String, archType: String, name: String): String {
        return "https://github.com/CypherpunkArmory/UserLAnd-Assets-$distType/raw/$branch/assets/$archType/$name"
    }

    @Test
    fun enqueuesDownload() {
        downloadUtility.downloadRequirements(assetList)

        verify(downloadManager, times(2)).enqueue(any())
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

        val asset1DownloadsFile = File("${downloadDirectory.path}/UserLAnd:${asset1.concatenatedName}")
        val asset2DownloadsFile = File("${downloadDirectory.path}/UserLAnd:${asset2.concatenatedName}")
        asset1DownloadsFile.createNewFile()
        asset2DownloadsFile.createNewFile()
        assertTrue(asset1DownloadsFile.exists())
        assertTrue(asset2DownloadsFile.exists())

        downloadUtility.downloadRequirements(assetList)
        verify(downloadManager, times(2)).enqueue(any())

        assertFalse(asset1File.exists())
        assertFalse(asset2File.exists())
        assertFalse(asset1DownloadsFile.exists())
        assertFalse(asset2DownloadsFile.exists())
    }

    @Test
    fun setsTimestampWhenTitleIsRelevant() {
        val id = 1L
        val assetConcatenatedName = "type:name"
        val titleName = "UserLAnd:$assetConcatenatedName"
        `when`(downloadManagerWrapper.generateQuery(id)).thenReturn(queryReturn)
        `when`(downloadManagerWrapper.generateCursor(downloadManager, queryReturn)).thenReturn(cursor)
        `when`(downloadManagerWrapper.getDownloadTitle(cursor)).thenReturn(titleName)

        downloadUtility.setTimestampForDownloadedFile(id)

        verify(timestampPreferences).setSavedTimestampForFileToNow(assetConcatenatedName)
    }

    @Test
    fun ignoresIrrelevantDownloads() {
        val id = 1L
        val titleName = "notuserland"
        `when`(downloadManagerWrapper.generateQuery(id)).thenReturn(queryReturn)
        `when`(downloadManagerWrapper.generateCursor(downloadManager, queryReturn)).thenReturn(cursor)
        `when`(downloadManagerWrapper.getDownloadTitle(cursor)).thenReturn(titleName)

        downloadUtility.setTimestampForDownloadedFile(id)

        verify(timestampPreferences, never()).setSavedTimestampForFileToNow(ArgumentMatchers.anyString())
    }

    @Test
    fun movesAssetsToCorrectLocationAndUpdatesPermissions() {
        val asset1DownloadsFile = File("${downloadDirectory.path}/UserLAnd:${asset1.concatenatedName}")
        val asset2DownloadsFile = File("${downloadDirectory.path}/UserLAnd:${asset2.concatenatedName}")
        asset1DownloadsFile.createNewFile()
        asset2DownloadsFile.createNewFile()

        val asset1File = File("${tempFolder.root.path}/distType1/name1")
        val asset2File = File("${tempFolder.root.path}/distType2/name2")
        assertFalse(asset1File.exists())
        assertFalse(asset2File.exists())

        downloadUtility.moveAssetsToCorrectLocalDirectory()

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