package tech.ula.utils

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.io.FileNotFoundException

@RunWith(MockitoJUnitRunner::class)
class AssetFileClearerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock lateinit var mockUlaFiles: UlaFiles

    @Mock lateinit var busyboxExecutor: BusyboxExecutor

    @Mock lateinit var mockLogger: Logger

    private lateinit var filesDir: File
    private lateinit var supportDir: File
    private lateinit var debianDir: File
    private lateinit var filesystemDir: File

    private lateinit var filesystemSupportDir: File
    private lateinit var topLevelSupportAssetFile: File
    private lateinit var topLevelDebianAssetFile: File
    private lateinit var hiddenFilesystemSupportFile: File
    private lateinit var nestedFilesystemAssetFile: File
    private lateinit var randomTopLevelFile: File
    private lateinit var randomTopLevelDir: File

    private val filesDirName = "filesDir"
    private val debianDirName = "debian"
    private val supportDirName = "support"
    private val filesystemDirName = "1"
    private val assetDirectoryNames = setOf(debianDirName, supportDirName)
    private val assetName = "asset"
    private val hiddenFileName = ".hidden_file"

    private lateinit var assetFileClearer: AssetFileClearer

    @Before
    fun setup() {
        createTestFiles()

        assetFileClearer = AssetFileClearer(mockUlaFiles, assetDirectoryNames, busyboxExecutor, mockLogger)
    }

    private fun createTestFiles() {
        randomTopLevelFile = tempFolder.newFile()
        randomTopLevelDir = tempFolder.newFolder()
        filesDir = tempFolder.newFolder(filesDirName)
        debianDir = tempFolder.newFolder(filesDirName, debianDirName)
        supportDir = tempFolder.newFolder(filesDirName, supportDirName)
        filesystemDir = tempFolder.newFolder(filesDirName, filesystemDirName)
        filesystemSupportDir = tempFolder.newFolder(filesDirName, filesystemDirName, supportDirName)

        topLevelSupportAssetFile = File("${supportDir.absolutePath}/$assetName")
        topLevelSupportAssetFile.createNewFile()

        topLevelDebianAssetFile = File("${debianDir.absolutePath}/$assetName")
        topLevelDebianAssetFile.createNewFile()

        hiddenFilesystemSupportFile = File("${filesystemSupportDir.absolutePath}/$hiddenFileName")
        hiddenFilesystemSupportFile.createNewFile()
        nestedFilesystemAssetFile = File("${filesystemSupportDir.absolutePath}/$assetName")
        nestedFilesystemAssetFile.createNewFile()

        whenever(mockUlaFiles.filesDir).thenReturn(filesDir)
        whenever(mockUlaFiles.busybox).thenReturn(topLevelSupportAssetFile)
    }

    @Test(expected = FileNotFoundException::class)
    fun `Throws FileNotFoundException if files directory does not exist`() {
        filesDir.deleteRecursively()

        runBlocking { assetFileClearer.clearAllSupportAssets() }

        verify(mockLogger.addExceptionBreadcrumb(FileNotFoundException()))
    }

    @Test
    fun `Clears all assets and leaves filesystem structure intact`() = runBlocking {
        whenever(busyboxExecutor.recursivelyDelete(any())).thenReturn(SuccessfulExecution)
        assetFileClearer.clearAllSupportAssets()

        verify(busyboxExecutor, never()).recursivelyDelete(filesDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(randomTopLevelFile.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(randomTopLevelDir.absolutePath)

        verify(busyboxExecutor).recursivelyDelete(debianDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(supportDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(topLevelDebianAssetFile.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(topLevelSupportAssetFile.absolutePath)

        verify(busyboxExecutor, never()).recursivelyDelete(filesystemDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(filesystemSupportDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(hiddenFilesystemSupportFile.absolutePath)
        verify(busyboxExecutor).recursivelyDelete(nestedFilesystemAssetFile.absolutePath)
        verify(mockUlaFiles).setupLinks()
    }
}