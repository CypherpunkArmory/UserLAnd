package tech.ula.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
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
class ProotDebugLoggerTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockDefaultSharedPreferences: SharedPreferences

    @Mock lateinit var mockContentResolver: ContentResolver

    @Mock lateinit var mockUri: Uri

    private val logName = "Proot_Debug_Log.txt"

    private lateinit var prootDebugLogger: ProotDebugLogger

    @Before
    fun setup() {
        prootDebugLogger = ProotDebugLogger(mockDefaultSharedPreferences, tempFolder.root.path)
    }

    @Test
    fun `Property isEnabled fetches from cache`() {
        whenever(mockDefaultSharedPreferences.getBoolean("pref_proot_debug_enabled", false))
                .thenReturn(true)

        assertTrue(prootDebugLogger.isEnabled)
    }

    @Test
    fun `Property verbosityLevel fetches from cache`() {
        val level = "500"
        whenever(mockDefaultSharedPreferences.getString("pref_proot_debug_level", "-1"))
                .thenReturn(level)

        assertEquals(level, prootDebugLogger.verbosityLevel)
    }

    @Test
    fun `Creates new log`() {
        val originalText = "hello world"
        val outputFile = File(tempFolder.root, "test")
        val logFile = File(tempFolder.root, logName)

        outputFile.writeText(originalText)
        assertFalse(logFile.exists())

        runBlocking { prootDebugLogger.logStream(outputFile.inputStream(), this) }

        val logText = logFile.readText().trim()
        assertEquals(originalText, logText)
    }

    @Test
    fun `Overwrites original log`() {
        val originalText = "hello world"
        val outputFile = File(tempFolder.root, "test")
        val logFile = File(tempFolder.root, logName)

        outputFile.writeText(originalText)
        logFile.writeText("world hello")

        runBlocking { prootDebugLogger.logStream(outputFile.inputStream(), this) }

        val logText = logFile.readText().trim()
        assertEquals(originalText, logText)
    }

    @Test
    fun `copyLogToDestination copies log to a URI-specified destination and returns true on success`() {
        val originalText = "copy world"
        val logFile = File(tempFolder.root, logName)
        logFile.writeText(originalText)

        val destinationFile = File(tempFolder.root, "destination")

        whenever(mockContentResolver.openOutputStream(mockUri, "w"))
                .thenReturn(destinationFile.outputStream())

        val result = runBlocking { prootDebugLogger.copyLogToDestination(mockUri, mockContentResolver) }

        assertEquals(originalText, destinationFile.readText().trim())
        assertTrue(result)
    }

    @Test
    fun `copyLogToDestination returns false on failure`() {
        val destinationFile = File(tempFolder.root, "destination")

        whenever(mockContentResolver.openOutputStream(mockUri, "w"))
                .thenThrow(FileNotFoundException())

        val result = runBlocking { prootDebugLogger.copyLogToDestination(mockUri, mockContentResolver) }

        assertFalse(destinationFile.exists())
        assertFalse(result)
    }
}