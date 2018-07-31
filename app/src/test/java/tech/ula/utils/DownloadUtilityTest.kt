package tech.ula.utils

import android.app.DownloadManager
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL

@RunWith(MockitoJUnitRunner::class)
class DownloadUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var downloadManager: DownloadManager

    @Mock
    lateinit var timestampPreferenceUtility: TimestampPreferenceUtility

    @Mock
    lateinit var connectivityManager: ConnectivityManager

    @Mock
    lateinit var network: Network

    @Mock
    lateinit var networkInfo: NetworkInfo

    @Mock
    lateinit var networkCapabilities: NetworkCapabilities

    @Mock
    lateinit var connectionUtility: ConnectionUtility

    @Mock
    lateinit var requestUtility: RequestUtility

    @Mock
    lateinit var environmentUtility: EnvironmentUtility

    @Mock
    lateinit var requestReturn: DownloadManager.Request

    lateinit var downloadUtility: DownloadUtility

    lateinit var applicationFilesDirPath: String

    val filename = "asset1"
    lateinit var assetFile: File
    val remotetimestamp = 1000
    val repo = "repo"
    val scope = "scope"
    val branch = "master"
    val assetListUrl = "https://github.com/CypherpunkArmory/UserLAnd-Assets-$repo/raw/$branch/assets/$scope/assets.txt"
    val downloadUrl = "https://github.com/CypherpunkArmory/UserLAnd-Assets-$repo/raw/$branch/assets/$scope/$filename"
    val destination = "UserLAnd:$repo:$filename"
    val assetListTypes = listOf(repo to scope)
    lateinit var assetList: File

    @Before
    fun setup() {
        applicationFilesDirPath = tempFolder.root.path
    }

    fun initDownloadUtility(session: Session, filesystem: Filesystem): DownloadUtility {
        return DownloadUtility(session, filesystem,
                downloadManager, timestampPreferenceUtility,
                applicationFilesDirPath, connectivityManager,
                connectionUtility, requestUtility, environmentUtility)
    }

    fun setupDownloadTest(includeRootfsFile: Boolean = false) {
        assetList = File("${tempFolder.root.path}/assets.txt")
        assetList.delete()
        val writer = FileWriter(assetList)
        writer.write("$filename $remotetimestamp")
        if (includeRootfsFile) writer.write("\nrootfs.tar.gz $remotetimestamp")
        writer.flush()
        writer.close()

        tempFolder.newFolder(repo)
        assetFile = File("${tempFolder.root.path}/$repo/$filename")
        if(assetFile.exists()) assetFile.delete()

        val inputStream = FileInputStream(assetList)
        `when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        `when`(connectionUtility.getAssetListConnection(assetListUrl)).thenReturn(inputStream)
        `when`(requestUtility.generateTypicalDownloadRequest(downloadUrl, destination)).thenReturn(requestReturn)
        `when`(environmentUtility.getDownloadsDirectory()).thenReturn(tempFolder.root)
    }

    @Test
    fun largeAssetIsRequiredAndThereIsNoWifi() {
        val session = Session(0, filesystemId = 0, isExtracted = false)
        val filesystem = Filesystem(0, isDownloaded = false)
        downloadUtility = initDownloadUtility(session, filesystem)

        `when`(connectivityManager.allNetworks).thenReturn(arrayOf())

        assertTrue(downloadUtility.largeAssetRequiredAndNoWifi())
    }

    @Test
    fun largeAssetIsRequiredAndThereIsWifi() {
        val session = Session(0, filesystemId = 0, isExtracted = false)
        val filesystem = Filesystem(0, isDownloaded = false)
        downloadUtility = initDownloadUtility(session, filesystem)

        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(network))
        `when`(connectivityManager.getNetworkCapabilities(network)).thenReturn(networkCapabilities)
        `when`(networkCapabilities.hasTransport(anyInt())).thenReturn(true)

        assertFalse(downloadUtility.largeAssetRequiredAndNoWifi())
    }

    @Test
    fun largeAssetIsNotRequired() {
        val session = Session(0, filesystemId = 0, isExtracted = true)
        val filesystem = Filesystem(0, isDownloaded = true)
        downloadUtility = initDownloadUtility(session, filesystem)

        assertFalse(downloadUtility.largeAssetRequiredAndNoWifi())
    }

    @Test
    fun internetIsAccessibleWhenActiveNetworkInfoPresent() {
        val session = Session(0, filesystemId = 0)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        `when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        assertTrue(downloadUtility.internetIsAccessible())
    }

    @Test
    fun internetIsAccessibleWhenActiveNetworkInfoNotPresent() {
        val session = Session(0, filesystemId = 0)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        assertFalse(downloadUtility.internetIsAccessible())
    }

    @Test
    fun downloadsRequirementsWhenForced() {
        val session = Session(0, filesystemId = 0)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest()
        assetFile.createNewFile()

        downloadUtility.downloadRequirements(updateIsBeingForced = true, assetListTypes = assetListTypes)

        verify(timestampPreferenceUtility).setLastUpdateCheck(anyLong())
        verify(timestampPreferenceUtility).setSavedTimestampForFile(anyString(), anyLong())
        verify(downloadManager).enqueue(any())
    }

    @Test
    fun doesNotUpdateIfLastCheckedIsWithinADayOfNow() {
        val session = Session(0, filesystemId = 0, isExtracted = true)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest()
        assetFile.createNewFile()

        val lastUpdateChecked = currentTimeSeconds()
        `when`(timestampPreferenceUtility.getLastUpdateCheck()).thenReturn(lastUpdateChecked)

        downloadUtility.downloadRequirements(updateIsBeingForced = false, assetListTypes = assetListTypes)

        verify(downloadManager, never()).enqueue(any())
    }

    @Test
    fun downloadsIfTheAssetDoesNotExist() {
        val session = Session(0, filesystemId = 0, isExtracted = true)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest()

        downloadUtility.downloadRequirements(updateIsBeingForced = false, assetListTypes = assetListTypes)

        verify(timestampPreferenceUtility).setLastUpdateCheck(anyLong())
        verify(timestampPreferenceUtility).setSavedTimestampForFile(anyString(), anyLong())
        verify(downloadManager).enqueue(any())
    }

    @Test
    fun downloadsIfTheSessionIsNotExtracted() {
        val session = Session(0, filesystemId = 0, isExtracted = false)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest()
        assetFile.createNewFile()

        downloadUtility.downloadRequirements(updateIsBeingForced = false, assetListTypes = assetListTypes)

        verify(timestampPreferenceUtility).setLastUpdateCheck(anyLong())
        verify(timestampPreferenceUtility).setSavedTimestampForFile(anyString(), anyLong())
        verify(downloadManager).enqueue(any())
    }

    @Test
    fun deletesAssetIfLocalTimestampIsLessThanRemote() {
        val session = Session(0, filesystemId = 0, isExtracted = false)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest()
        assetFile.createNewFile()

        val localTimestamp = 1L
        `when`(timestampPreferenceUtility.getSavedTimestampForFile("$repo:$filename")).thenReturn(localTimestamp)

        downloadUtility.downloadRequirements(updateIsBeingForced = false, assetListTypes = assetListTypes)

        assertFalse(assetFile.exists())
        verify(timestampPreferenceUtility).setLastUpdateCheck(anyLong())
        verify(timestampPreferenceUtility).setSavedTimestampForFile(anyString(), anyLong())
        verify(downloadManager).enqueue(any())
    }

    @Test
    fun doesNotUpdateRootfsButDoesUpdateAssetsIfSessionIsExtracted() {
        val session = Session(0, filesystemId = 0, isExtracted = true)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest(includeRootfsFile = true)
        assetFile.createNewFile()

        downloadUtility.downloadRequirements(updateIsBeingForced = false, assetListTypes = assetListTypes)

        verify(timestampPreferenceUtility, times(1)).setLastUpdateCheck(anyLong())
        verify(timestampPreferenceUtility, times(1)).setSavedTimestampForFile(anyString(), anyLong())
        verify(downloadManager, times(1)).enqueue(any())
    }

    @Test
    fun updatesRootfsIfSessionIsNotExtracted() {
        val session = Session(0, filesystemId = 0, isExtracted = false)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest(includeRootfsFile = true)
        assetFile.createNewFile()

        downloadUtility.downloadRequirements(updateIsBeingForced = false, assetListTypes = assetListTypes)

        verify(timestampPreferenceUtility, times(2)).setLastUpdateCheck(anyLong())
        verify(timestampPreferenceUtility, times(2)).setSavedTimestampForFile(anyString(), anyLong())
        verify(downloadManager, times(2)).enqueue(any())
    }

    @Test
    fun deletesDownloadedFilesIfTheyStillExist() {
        val session = Session(0, filesystemId = 0)
        val filesystem = Filesystem(0)
        downloadUtility = initDownloadUtility(session, filesystem)

        setupDownloadTest()
        val previousDownloadFile = File("${tempFolder.root.path}/UserLAnd:$repo:$filename")
        previousDownloadFile.createNewFile()
        assertTrue(previousDownloadFile.exists())

        downloadUtility.downloadRequirements(updateIsBeingForced = false, assetListTypes = assetListTypes)

        verify(environmentUtility).getDownloadsDirectory()
        assertFalse(previousDownloadFile.exists())
    }
}