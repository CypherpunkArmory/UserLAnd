package tech.ula.utils

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import kotlin.text.Charsets.UTF_8

@RunWith(MockitoJUnitRunner::class)
class ExecUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    lateinit var externalStoragePath: String

    @Mock
    lateinit var defaultPreferences: DefaultPreferences

    private val logCollector = ArrayList<String>()
    private val testLogger: (line: String) -> Unit = { logCollector.add(it) }

    lateinit var testDirectory: File

    lateinit var execUtility: ExecUtility

    @Before
    fun setup() {
        testDirectory = tempFolder.root
        externalStoragePath = tempFolder.newFolder("external").path
        logCollector.clear()

        execUtility = ExecUtility(testDirectory.path, externalStoragePath, defaultPreferences)
    }

    @Test
    fun execLocalExecutesProcessAndCapturesOutput() {
        val command = arrayListOf("echo", "hello world")
        val doWait = true
        execUtility.execLocal(testDirectory, command, testLogger, doWait)
        assert(logCollector.size == 1)
        assert(logCollector[0] == "hello world")
    }

    @Test(expected = RuntimeException::class)
    fun execLocalThrowsExceptionOnBadCommand() {
        val command = arrayListOf("thisIsAFakeCommand")
        val doWait = true
        execUtility.execLocal(testDirectory, command, testLogger, doWait)
        assert(logCollector.size == 1)
    }

    @Test
    fun loggingIsCapturedIfSettingIsEnabled() {
        val debugFile = File("${testDirectory.path}/debugLog.txt")

        `when`(defaultPreferences.getProotDebuggingEnabled()).thenReturn(true)
        `when`(defaultPreferences.getProotDebuggingLevel()).thenReturn("9")
        `when`(defaultPreferences.getProotDebugLogLocation()).thenReturn(debugFile.path)

        val commandToRun = arrayListOf("echo", "execInProot")
        val doWait = true

        execUtility.execLocal(testDirectory, commandToRun, testLogger, doWait)
        Thread.sleep(100) // To wait for coroutine
        assertTrue(debugFile.exists())
        assertEquals("execInProot", debugFile.readText(UTF_8).trim())
    }

//    @Test
//    fun `Recursively deletes when file is a directory`() {
//        val subDirectory = File("${testDirectory.absolutePath}/subDirectory")
//        subDirectory.mkdirs()
//        assertTrue(subDirectory.exists() && subDirectory.isDirectory)
//        val subFile = File("${subDirectory.absolutePath}/subFile")
//        subFile.createNewFile()
//        assertTrue(subFile.exists())
//
//        val result = runBlocking {
//            execUtility.recursivelyDelete(subDirectory.path)
//        }
//
//        assertTrue(result)
//        assertFalse(subDirectory.exists())
//        assertFalse(subFile.exists())
//    }
//
//    @Test
//    fun `Deletes a single file if recursivelyDelete is called on it`() {
//
//    }
//
//    @Test
//    fun `Returns false if recursivelyDelete fails`() {
//
//    }
}