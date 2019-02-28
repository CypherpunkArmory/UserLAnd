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

    @Mock lateinit var busyboxExecutor: BusyboxExecutor

    lateinit var filesDir: File
    lateinit var supportDir: File
    lateinit var debianDir: File
    lateinit var filesystemDir: File

    lateinit var filesystemSupportDir: File
    lateinit var topLevelSupportAssetFile: File
    lateinit var topLevelDebianAssetFile: File
    lateinit var hiddenFilesystemSupportFile: File
    lateinit var nestedFilesystemAssetFile: File
    lateinit var randomTopLevelFile: File
    lateinit var randomTopLevelDir: File

    val filesDirName = "filesDir"
    val debianDirName = "debian"
    val supportDirName = "support"
    val filesystemDirName = "1"
    val assetDirectoryNames = setOf(debianDirName, supportDirName)
    val assetName = "asset"
    val hiddenFileName = ".hidden_file"

    lateinit var assetFileClearer: AssetFileClearer

    @Before
    fun setup() {
        createTestFiles()

        assetFileClearer = AssetFileClearer(filesDir, assetDirectoryNames, busyboxExecutor)
    }

    fun createTestFiles() {
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
    }

    @Test(expected = FileNotFoundException::class)
    fun `Throws FileNotFoundException if files directory does not exist`() {
        filesDir.deleteRecursively()

        runBlocking { assetFileClearer.clearAllSupportAssets() }
    }

    @Test
    fun `Clears all assets and leaves filesystem structure intact`() = runBlocking {
        whenever(busyboxExecutor.recursivelyDelete(any())).thenReturn(true)
        assetFileClearer.clearAllSupportAssets()

        verify(busyboxExecutor, never()).recursivelyDelete(filesDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(randomTopLevelFile.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(randomTopLevelDir.absolutePath)

        verify(busyboxExecutor).recursivelyDelete(debianDir.absolutePath)
        verify(busyboxExecutor).recursivelyDelete(supportDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(topLevelDebianAssetFile.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(topLevelSupportAssetFile.absolutePath)

        verify(busyboxExecutor, never()).recursivelyDelete(filesystemDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(filesystemSupportDir.absolutePath)
        verify(busyboxExecutor, never()).recursivelyDelete(hiddenFilesystemSupportFile.absolutePath)
        verify(busyboxExecutor).recursivelyDelete(nestedFilesystemAssetFile.absolutePath)
        Unit
    }
}