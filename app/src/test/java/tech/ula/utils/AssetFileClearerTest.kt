package tech.ula.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.io.FileNotFoundException

@RunWith(MockitoJUnitRunner::class)
class AssetFileClearerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

        assetFileClearer = AssetFileClearer(filesDir, assetDirectoryNames)
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

        assetFileClearer.clearAllSupportAssets()
    }

    @Test
    fun `Clears all assets and leaves filesystem structure intact`() {
        assetFileClearer.clearAllSupportAssets()

        assertTrue(filesDir.exists())
        assertTrue(randomTopLevelFile.exists())
        assertTrue(randomTopLevelDir.exists())

        assertFalse(debianDir.exists())
        assertFalse(supportDir.exists())
        assertFalse(topLevelDebianAssetFile.exists())
        assertFalse(topLevelSupportAssetFile.exists())

        assertTrue(filesystemDir.exists())
        assertTrue(filesystemSupportDir.exists())
        assertTrue(hiddenFilesystemSupportFile.exists())
        assertFalse(nestedFilesystemAssetFile.exists())
    }
}