package tech.ula.utils

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import java.io.File
import java.nio.file.Files

class UlaFilesTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockSymlinker: Symlinker

    private lateinit var testFilesDir: File
    private lateinit var testScopedDir: File
    private lateinit var testLibDir: File
    private lateinit var testSupportDir: File

    private lateinit var ulaFiles: UlaFiles

    @Test
    fun `Initialization links every file in the lib directory to support, stripping unnecessary name parts`() {
        testFilesDir = tempFolder.newFolder("files")
        testScopedDir = tempFolder.newFolder("scoped")
        testLibDir = tempFolder.newFolder("execLib")
        testSupportDir = File(testFilesDir, "support")

        mockSymlinker = mock()

        val expectedText1 = "text1"
        val libFile1 = File(testLibDir, "lib_1.so")
        libFile1.writeText(expectedText1)

        val expectedText2 = "text2"
        val libFile2 = File(testLibDir, "lib_2.so")
        libFile2.writeText(expectedText2)

        val expectedSupportFile1 = File(testSupportDir, "1")
        val expectedSupportFile2 = File(testSupportDir, "2")

        // Create files for the symlinker mock to verify the calls are done.
        whenever(mockSymlinker.createSymlink(libFile1.path, expectedSupportFile1.path))
                .then {
                    Files.createSymbolicLink(expectedSupportFile1.toPath(), libFile1.toPath())
                }
        whenever(mockSymlinker.createSymlink(libFile2.path, expectedSupportFile2.path))
                .then {
                    Files.createSymbolicLink(expectedSupportFile2.toPath(), libFile2.toPath())
                }

        ulaFiles = UlaFiles(testFilesDir, testScopedDir, testLibDir, mockSymlinker)

        assertTrue(expectedSupportFile1.exists())
        assertTrue(expectedSupportFile2.exists())
        assertEquals(expectedText1, expectedSupportFile1.readText().trim())
        assertEquals(expectedText2, expectedSupportFile2.readText().trim())
    }
}