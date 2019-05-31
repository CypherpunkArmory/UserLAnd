package tech.ula.utils

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import java.io.File
import java.nio.file.Files

class UlaFilesTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockSymlinker: Symlinker

    lateinit var testFilesDir: File
    lateinit var testScopedDir: File
    lateinit var testLibDir: File
    lateinit var testLibLinkDir: File
    lateinit var testSupportDir: File

    lateinit var ulaFiles: UlaFiles

    @Before
    fun setup() {
        testFilesDir = tempFolder.newFolder("files")
        testScopedDir = tempFolder.newFolder("scoped")
        testLibDir = tempFolder.newFolder("execLib")
        testLibLinkDir = File(testFilesDir, "lib")
        testSupportDir = File(testFilesDir, "support")

        mockSymlinker = mock()

        ulaFiles = UlaFiles(testFilesDir, testScopedDir, testLibDir, mockSymlinker)
    }

    @Test
    fun `setupSupportDir will copy the right assets to the supportDir`() {
        val expectedText = "supportRequirement"
        ulaFiles.supportDirFileRequirements.forEach { filename ->
            val fileInLibDir = File(testLibDir, filename)
            fileInLibDir.createNewFile()
            fileInLibDir.writeText(expectedText)
        }
        ulaFiles.libDirectorySymlinkMapping.forEach { (_, filename) ->
            val fileInLibDir = File(testLibDir, filename)
            fileInLibDir.createNewFile()
            fileInLibDir.writeText("notSupportRequirement")
        }

        assertFalse(testSupportDir.exists())

        runBlocking {
            ulaFiles.setupSupportDir()
        }

        assertTrue(testSupportDir.exists())
        ulaFiles.supportDirFileRequirements.forEach { filename ->
            val fileInSupportDir = File(testSupportDir, filename)
            assertTrue(fileInSupportDir.exists())
            val actualText = fileInSupportDir.readText().trim()
            assertEquals(expectedText, actualText)
        }

        ulaFiles.libDirectorySymlinkMapping.forEach { (_, filename) ->
            val fileInSupportDir = File(testSupportDir, filename)
            assertFalse(fileInSupportDir.exists())
        }
    }

    @Test(expected = NoSuchFileException::class)
    fun `setupSupportDir throws NoSuchFileException if a file does not exist`() {
        runBlocking { ulaFiles.setupSupportDir() }
    }

    @Test
    fun `setupLinks creates the correct symlinks in the right place`() {
        ulaFiles.libDirectorySymlinkMapping.forEach { (linkname, filename) ->
            val fileInLibDir = File(testLibDir, filename)
            val fileInLibLinkDir = File(testLibLinkDir, linkname)
            fileInLibDir.createNewFile()
            // Create symlink for the mock, since Files#createSymbolicLink API is unavailable on
            // older SDKs
            whenever(mockSymlinker.createSymlink(fileInLibDir.path, fileInLibLinkDir.path))
                    .then {
                        Files.createSymbolicLink(fileInLibLinkDir.toPath(), fileInLibDir.toPath())
                    }
        }
        assertFalse(testLibLinkDir.exists())

        runBlocking { ulaFiles.setupLinks() }

        assertTrue(testLibLinkDir.exists())
        ulaFiles.libDirectorySymlinkMapping.forEach { (linkname, _) ->
            val linkFile = File(testLibLinkDir, linkname)
            assertTrue(linkFile.exists())
            assertTrue(Files.isSymbolicLink(linkFile.toPath()))
        }
    }

    @Test(expected = NoSuchFileException::class)
    fun `setupLinks throws NoSuchFileException if a lib file does not exist`() {
        runBlocking {
            ulaFiles.setupLinks()
        }
    }
}