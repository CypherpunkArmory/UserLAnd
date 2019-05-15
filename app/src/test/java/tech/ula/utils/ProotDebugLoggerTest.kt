package tech.ula.utils

import android.content.Context
import android.content.SharedPreferences
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

@RunWith(MockitoJUnitRunner::class)
class ProotDebugLoggerTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockDefaultSharedPreferences: SharedPreferences

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
}