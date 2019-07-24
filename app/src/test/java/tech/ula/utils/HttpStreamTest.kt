package tech.ula.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

// Note: These tests are pretty brittle as they are meant to be proper integration tests.
// Any change to our remote sources will break them.

class HttpStreamTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val baseAppsUrls = "https://github.com/CypherpunkArmory/UserLAnd-Assets-Support/raw/master/apps"

    private val httpStream = HttpStream()

    @Test
    fun `toLines maps a url to a list of strings`() {
        val url = "$baseAppsUrls/apps.txt"

        val result = runBlocking {
            httpStream.toLines(url)
        }

        val expectedFirstLine = "App Name, Category, Filesystem Type Required, Supports CLI, Supports GUI, isPaidApp, Version"
        assertTrue(result.isNotEmpty())
        assertEquals(expectedFirstLine, result[0])
    }

    @Test
    fun `toFile maps a url to a local file`() {
        val url = "$baseAppsUrls/debian/debian.png"
        val file = tempFolder.newFile("pngTest")

        runBlocking {
            httpStream.toFile(url, file)
        }

        assertTrue(file.exists())
        assertTrue(file.length() > 500) // Any PNG should be larger than this
    }

    @Test
    fun `toTextFile maps a url to a text file`() {
        val url = "$baseAppsUrls/alpine/alpine.txt"
        val file = tempFolder.newFile("textTest")

        runBlocking {
            httpStream.toTextFile(url, file)
        }

        val expectedText = "Alpine Linux is a security-oriented, lightweight Linux distribution based on musl libc and busybox."
        assertTrue(file.exists())
        assertEquals(expectedText, file.readText().trim())
    }
}