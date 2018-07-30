package tech.ula.utils

import android.app.DownloadManager
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session

@RunWith(MockitoJUnitRunner::class)
class DownloadUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var downloadManager: DownloadManager

    @Mock
    lateinit var sharedPreferences: SharedPreferences

    @Mock
    lateinit var connectivityManager: ConnectivityManager

    @Mock
    lateinit var network: Network

    @Mock
    lateinit var networkInfo: NetworkInfo

    @Mock
    lateinit var networkCapabilities: NetworkCapabilities


    lateinit var downloadUtility: DownloadUtility

    lateinit var applicationFilesDirPath: String

    @Before
    fun setup() {
        applicationFilesDirPath = tempFolder.root.path
    }

    @Test
    fun largeAssetIsRequiredAndThereIsNoWifi() {
        val session = Session(0, filesystemId = 0, isExtracted = false)
        val filesystem = Filesystem(0, isDownloaded = false)
        downloadUtility = DownloadUtility(session, filesystem, downloadManager, sharedPreferences, applicationFilesDirPath, connectivityManager)

        `when`(connectivityManager.allNetworks).thenReturn(arrayOf())

        assertTrue(downloadUtility.largeAssetRequiredAndNoWifi())
    }

    @Test
    fun largeAssetIsNotRequiredAndThereIsNotWifi() {
        val session = Session(0, filesystemId = 0, isExtracted = true)
        val filesystem = Filesystem(0, isDownloaded = true)
        downloadUtility = DownloadUtility(session, filesystem, downloadManager, sharedPreferences, applicationFilesDirPath, connectivityManager)

        `when`(connectivityManager.allNetworks).thenReturn(arrayOf())

        assertFalse(downloadUtility.largeAssetRequiredAndNoWifi())
    }

    @Test
    fun largeAssetIsRequiredAndThereIsWifi() {
        val session = Session(0, filesystemId = 0, isExtracted = false)
        val filesystem = Filesystem(0, isDownloaded = false)
        downloadUtility = DownloadUtility(session, filesystem, downloadManager, sharedPreferences, applicationFilesDirPath, connectivityManager)

        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(network))
        `when`(connectivityManager.getNetworkCapabilities(network)).thenReturn(networkCapabilities)
        `when`(networkCapabilities.hasTransport(anyInt())).thenReturn(true)

        assertFalse(downloadUtility.largeAssetRequiredAndNoWifi())
    }

    @Test
    fun largeAssetIsNotRequiredAndThereIsWifi() {
        val session = Session(0, filesystemId = 0, isExtracted = true)
        val filesystem = Filesystem(0, isDownloaded = true)
        downloadUtility = DownloadUtility(session, filesystem, downloadManager, sharedPreferences, applicationFilesDirPath, connectivityManager)

        `when`(connectivityManager.allNetworks).thenReturn(arrayOf(network))
        `when`(connectivityManager.getNetworkCapabilities(network)).thenReturn(networkCapabilities)
        `when`(networkCapabilities.hasTransport(anyInt())).thenReturn(true)

        assertFalse(downloadUtility.largeAssetRequiredAndNoWifi())
    }

    @Test
    fun internetIsAccessibleWhenActiveNetworkInfoPresent() {
        val session = Session(0, filesystemId = 0)
        val filesystem = Filesystem(0)
        downloadUtility = DownloadUtility(session, filesystem, downloadManager, sharedPreferences, applicationFilesDirPath, connectivityManager)

        `when`(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        assertTrue(downloadUtility.internetIsAccessible())
    }

    @Test
    fun internetIsAccessibleWhenActiveNetworkInfoNotPresent() {
        val session = Session(0, filesystemId = 0)
        val filesystem = Filesystem(0)
        downloadUtility = DownloadUtility(session, filesystem, downloadManager, sharedPreferences, applicationFilesDirPath, connectivityManager)

        assertFalse(downloadUtility.internetIsAccessible())
    }
}