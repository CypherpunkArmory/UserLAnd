package tech.ula.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import kotlin.text.Charsets.UTF_8

@RunWith(MockitoJUnitRunner::class)
class ExecUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var fileUtility: FileUtility

    @Mock
    lateinit var preferenceUtility: PreferenceUtility

    private val logCollector = ArrayList<String>()
    private val testLogger: (line: String) -> Unit = { logCollector.add(it) }

    lateinit var testDirectory: File

    lateinit var execUtility: ExecUtility

    @Before
    fun setup() {
        testDirectory = tempFolder.root
        logCollector.clear()

        MockitoAnnotations.initMocks(this)

        execUtility = ExecUtility(fileUtility, preferenceUtility)
    }

    @Test
    fun execLocalExecutesProcessAndCapturesOutput() {
        val command = arrayListOf("echo", "hello world")
        val doWait = true
        execUtility.execLocal(testDirectory, command, testLogger, doWait)
        assert(logCollector.size > 1)
        assert(logCollector[1] == "hello world")
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

        `when`(preferenceUtility.getProotDebuggingEnabled()).thenReturn(true)
        `when`(preferenceUtility.getProotDebuggingLevel()).thenReturn("9")
        `when`(preferenceUtility.getProotDebugLogLocation()).thenReturn(debugFile.path)

        val commandToRun = arrayListOf("echo", "execInProot")
        val doWait = true

        execUtility.execLocal(testDirectory, commandToRun, testLogger, doWait)
        Thread.sleep(100)
        assertTrue(debugFile.exists())
        assertEquals("execInProot", debugFile.readText(UTF_8).trim())
    }
}