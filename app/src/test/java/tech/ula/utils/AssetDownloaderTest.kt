package tech.ula.utils

import android.app.DownloadManager
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.* // ktlint-disable no-wildcard-imports
import org.mockito.junit.MockitoJUnitRunner
import org.rauschig.jarchivelib.Archiver
import tech.ula.model.repositories.DownloadMetadata
import tech.ula.utils.preferences.AssetPreferences
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class AssetDownloaderTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var assetPreferences: AssetPreferences

    @Mock lateinit var downloadManagerWrapper: DownloadManagerWrapper

    @Mock lateinit var mockUlaFiles: UlaFiles

    private lateinit var mockScopedStorageDir: File

    private lateinit var mockFilesDir: File

    @Mock lateinit var requestReturn1: DownloadManager.Request

    @Mock lateinit var requestReturn2: DownloadManager.Request

    @Mock lateinit var mockArchiveFactoryWrapper: ArchiveFactoryWrapper

    @Mock lateinit var mockArchiver: Archiver

    private lateinit var downloadDirectory: File

    private val rootfsName = "rootfs.tar.gz"
    private val assetsName = "assets.tar.gz"
    private val type1 = "type1"
    private val type2 = "type2"
    private val url1 = "url1"
    private val url2 = "url2"
    private val version = "v0"

    private val downloadMetadata1 = DownloadMetadata(rootfsName, type1, version, url1)
    private val downloadMetadata2 = DownloadMetadata(assetsName, type2, version, url2)
    private val downloadList = listOf(downloadMetadata1, downloadMetadata2)

    private lateinit var destination1: File
    private lateinit var destination2: File

    private lateinit var assetDownloader: AssetDownloader

    @Before
    fun setup() {
        mockFilesDir = tempFolder.newFolder("files")
        mockScopedStorageDir = tempFolder.newFolder("scoped")
        whenever(mockUlaFiles.filesDir).thenReturn(mockFilesDir)
        whenever(mockUlaFiles.scopedDir).thenReturn(mockScopedStorageDir)

        downloadDirectory = File(mockScopedStorageDir, "downloads")
        downloadDirectory.mkdirs()

        destination1 = File(downloadDirectory, downloadMetadata1.downloadTitle)
        destination2 = File(downloadDirectory, downloadMetadata2.downloadTitle)

        whenever(downloadManagerWrapper.generateDownloadRequest(downloadMetadata1.url, destination1))
                .thenReturn(requestReturn1)
        whenever(downloadManagerWrapper.generateDownloadRequest(downloadMetadata2.url, destination2))
                .thenReturn(requestReturn2)

        assetDownloader = AssetDownloader(assetPreferences, downloadManagerWrapper, mockUlaFiles)
    }

    private fun setupDownloadState() {
        whenever(downloadManagerWrapper.enqueue(requestReturn1))
                .thenReturn(0)
        whenever(downloadManagerWrapper.enqueue(requestReturn2))
                .thenReturn(1)

        assetDownloader.downloadRequirements(downloadList)
    }

    @Test
    fun `Returns appropriate value from asset preferences about whether cache is populated`() {
        val expectedFirstResult = true
        val expectedSecondResult = false
        whenever(assetPreferences.getDownloadsAreInProgress())
                .thenReturn(expectedFirstResult)
                .thenReturn(expectedSecondResult)

        val firstResult = assetDownloader.downloadStateHasBeenCached()
        val secondResult = assetDownloader.downloadStateHasBeenCached()

        assertEquals(expectedFirstResult, firstResult)
        assertEquals(expectedSecondResult, secondResult)
    }

    @Test
    fun `Returns CacheSyncAttemptedWhileCacheIsEmpty if sync cache called while nothing is cached`() {
        whenever(assetPreferences.getDownloadsAreInProgress())
                .thenReturn(false)

        val result = assetDownloader.syncStateWithCache()

        assertTrue(result is CacheSyncAttemptedWhileCacheIsEmpty)
    }

    @Test
    fun `Returns AssetDownloadFailure while syncing if any cached downloads failed`() {
        val downloadId = 0L
        val failureReason = DownloadFailureLocalizationData(0)
        whenever(assetPreferences.getDownloadsAreInProgress())
                .thenReturn(true)
        whenever(assetPreferences.getEnqueuedDownloads())
                .thenReturn(setOf(downloadId))

        whenever(downloadManagerWrapper.downloadHasFailed(downloadId))
                .thenReturn(true)
        whenever(downloadManagerWrapper.getDownloadFailureReason(downloadId))
                .thenReturn(failureReason)

        val result = assetDownloader.syncStateWithCache()
        assertTrue(result is AssetDownloadFailure)
        val cast = result as AssetDownloadFailure
        assertEquals(failureReason, cast.reason)
        verify(downloadManagerWrapper).cancelAllDownloads(setOf(0))
    }

    @Test
    fun `Returns AllDownloadsCompletedSuccessfully if all downloads have completed since cache was updated`() {
        val downloadId = 0L
        whenever(assetPreferences.getDownloadsAreInProgress())
                .thenReturn(true)
        whenever(assetPreferences.getEnqueuedDownloads())
                .thenReturn(setOf(downloadId))
        whenever(downloadManagerWrapper.downloadHasFailed(downloadId))
                .thenReturn(false)
        whenever(downloadManagerWrapper.downloadHasSucceeded(downloadId))
                .thenReturn(true)

        val result = assetDownloader.syncStateWithCache()

        assertTrue(result is AllDownloadsCompletedSuccessfully)
        verify(assetPreferences).setDownloadsAreInProgress(false)
        verify(assetPreferences).clearEnqueuedDownloadsCache()
    }

    @Test
    fun `Returns CompletedDownloadsUpdate if downloads are still in progress during sync`() {
        val downloadIds = setOf<Long>(0, 1)
        whenever(assetPreferences.getDownloadsAreInProgress())
                .thenReturn(true)
        whenever(assetPreferences.getEnqueuedDownloads())
                .thenReturn(downloadIds)
        whenever(downloadManagerWrapper.downloadHasFailed(0))
                .thenReturn(false)
        whenever(downloadManagerWrapper.downloadHasSucceeded(0))
                .thenReturn(true)
        whenever(downloadManagerWrapper.downloadHasFailed(1))
                .thenReturn(false)
        whenever(downloadManagerWrapper.downloadHasSucceeded(1))
                .thenReturn(false)

        val result = assetDownloader.syncStateWithCache()

        assertTrue(result is CompletedDownloadsUpdate)
        val cast = result as CompletedDownloadsUpdate
        assertEquals(1, cast.numCompleted)
        assertEquals(2, cast.numTotal)
    }

    @Test
    fun `Sets up download process`() {
        whenever(downloadManagerWrapper.enqueue(requestReturn1))
                .thenReturn(0)
        whenever(downloadManagerWrapper.enqueue(requestReturn2))
                .thenReturn(1)

        assetDownloader.downloadRequirements(downloadList)

        verify(downloadManagerWrapper).generateDownloadRequest(url1, destination1)
        verify(downloadManagerWrapper).generateDownloadRequest(url2, destination2)
        verify(downloadManagerWrapper).enqueue(requestReturn1)
        verify(downloadManagerWrapper).enqueue(requestReturn2)
        verify(assetPreferences).clearEnqueuedDownloadsCache()
        verify(assetPreferences).setDownloadsAreInProgress(true)
        verify(assetPreferences).setEnqueuedDownloads(setOf(0, 1))
    }

    @Test
    fun `Returns NonUserLandDownloadFound if a a download we did not start is found`() {
        setupDownloadState()

        val result = assetDownloader.handleDownloadComplete(-1)

        assertTrue(result is NonUserlandDownloadFound)
    }

    @Test
    fun `Returns AssetDownloadFailure if any downloads fail`() {
        setupDownloadState()
        val localizationData = DownloadFailureLocalizationData(0)
        whenever(downloadManagerWrapper.downloadHasFailed(0))
                .thenReturn(true)
        whenever(downloadManagerWrapper.getDownloadFailureReason(0))
                .thenReturn(localizationData)

        val result = assetDownloader.handleDownloadComplete(0)

        assertTrue(result is AssetDownloadFailure)
        result as AssetDownloadFailure
        assertEquals(localizationData, result.reason)
    }

    @Test
    fun `Completes downloads and then resets cache when all complete`() {
        setupDownloadState()
        whenever(downloadManagerWrapper.downloadHasFailed(0))
                .thenReturn(false)
        whenever(downloadManagerWrapper.downloadHasFailed(1))
                .thenReturn(false)

        val result1 = assetDownloader.handleDownloadComplete(0)
        val result2 = assetDownloader.handleDownloadComplete(1)

        assertTrue(result1 is CompletedDownloadsUpdate)
        assertTrue(result2 is AllDownloadsCompletedSuccessfully)
        result1 as CompletedDownloadsUpdate
        result2 as AllDownloadsCompletedSuccessfully
        assertEquals(1, result1.numCompleted)
        assertEquals(2, result1.numTotal)
        verify(assetPreferences).setDownloadsAreInProgress(false)
        verify(assetPreferences, times(2)).clearEnqueuedDownloadsCache()
    }

    @Test
    fun `Clears download directory of userland files`() {
        val asset1DownloadsFile = File("${downloadDirectory.path}/${downloadMetadata1.downloadTitle}")
        val asset2DownloadsFile = File("${downloadDirectory.path}/${downloadMetadata2.downloadTitle}")
        asset1DownloadsFile.createNewFile()
        asset2DownloadsFile.createNewFile()
        assertTrue(asset1DownloadsFile.exists())
        assertTrue(asset2DownloadsFile.exists())

        assetDownloader.downloadRequirements(downloadList)

        assertFalse(asset1DownloadsFile.exists())
        assertFalse(asset2DownloadsFile.exists())
    }

    @Test
    fun `prepareDownloadsForUse deletes staging directory after use`() {
        val stagingDirectory = File("${mockFilesDir.absolutePath}/staging")
        stagingDirectory.mkdirs()

        runBlocking { assetDownloader.prepareDownloadsForUse() }

        assertFalse(stagingDirectory.exists())
    }

    @Test
    fun `prepareDownloadsForUse moves rootfs files internal`() {
        val downloadedRootfs = File("${downloadDirectory.absolutePath}/${downloadMetadata1.downloadTitle}")
        downloadedRootfs.createNewFile()

        val destinationDirectory = File("${mockFilesDir.absolutePath}/$type1")
        val destinationFile = File("${destinationDirectory.absolutePath}/$rootfsName")

        assertFalse(destinationDirectory.exists())
        assertFalse(destinationFile.exists())

        runBlocking { assetDownloader.prepareDownloadsForUse(mockArchiveFactoryWrapper) }

        assertFalse(downloadedRootfs.exists())

        assertTrue(destinationDirectory.exists())
        assertTrue(destinationFile.exists())
        verify(assetPreferences).setLatestDownloadFilesystemVersion(type1, version)
    }

    @Test
    fun `prepareDownloadsForUse deletes old rootfs part files`() {
        val downloadedRootfs = File("${downloadDirectory.absolutePath}/${downloadMetadata1.downloadTitle}")
        downloadedRootfs.createNewFile()

        val destinationDirectory = File("${mockFilesDir.absolutePath}/$type1")
        val destinationFile = File("${destinationDirectory.absolutePath}/$rootfsName")
        val rootfsPartFile1 = File("${destinationDirectory.absolutePath}/rootfs.tar.gz.part00")
        val rootFsPartFile2 = File("${destinationDirectory.absolutePath}/rootfs.tar.gz.part01")

        destinationDirectory.mkdirs()
        rootfsPartFile1.createNewFile()
        rootFsPartFile2.createNewFile()

        runBlocking { assetDownloader.prepareDownloadsForUse(mockArchiveFactoryWrapper) }

        assertFalse(downloadedRootfs.exists())

        assertTrue(destinationDirectory.exists())
        assertTrue(destinationFile.exists())
        assertFalse(rootfsPartFile1.exists())
        assertFalse(rootFsPartFile2.exists())
        verify(assetPreferences).setLatestDownloadFilesystemVersion(type1, version)
    }

    @Test
    fun `prepareDownloadsForUse overwrites stale rootfs files`() {
        val expectedText = "expected"
        val downloadedRootfs = File("${downloadDirectory.absolutePath}/${downloadMetadata1.downloadTitle}")
        downloadedRootfs.createNewFile()
        downloadedRootfs.writeText(expectedText)

        val destinationDirectory = File("${mockFilesDir.absolutePath}/$type1")
        val destinationFile = File("${destinationDirectory.absolutePath}/$rootfsName")
        destinationDirectory.mkdirs()
        destinationFile.createNewFile()
        destinationFile.writeText("original")

        runBlocking { assetDownloader.prepareDownloadsForUse(mockArchiveFactoryWrapper) }

        assertFalse(downloadedRootfs.exists())

        assertTrue(destinationDirectory.exists())
        assertTrue(destinationFile.exists())
        verify(assetPreferences).setLatestDownloadFilesystemVersion(type1, version)

        val text = destinationFile.readText()
        assertEquals(expectedText, text)
    }

    @Test
    fun `prepareDownloadsForUse extracts assets tar files`() {
        val downloadedAssets = File("${downloadDirectory.absolutePath}/${downloadMetadata2.downloadTitle}")
        downloadedAssets.createNewFile()
        assertTrue(downloadedAssets.exists())

        val stagingDirectory = File("${mockFilesDir.absolutePath}/staging")
        val stagingTarget = File("${stagingDirectory.absolutePath}/$assetsName")
        val destination = File("${mockFilesDir.absolutePath}/$type2")

        whenever(mockArchiveFactoryWrapper.createArchiver(stagingTarget))
                .thenReturn(mockArchiver)
        whenever(mockArchiver.extract(stagingTarget, destination))
                .then {
                    destination.mkdirs()
                    File("${destination.absolutePath}/test").createNewFile()
                }

        runBlocking { assetDownloader.prepareDownloadsForUse(mockArchiveFactoryWrapper) }

        assertFalse(downloadedAssets.exists())
        verify(mockArchiver).extract(stagingTarget, destination)
        assertFalse(stagingDirectory.exists())
        verify(assetPreferences).setLatestDownloadVersion(type2, version)
    }
}