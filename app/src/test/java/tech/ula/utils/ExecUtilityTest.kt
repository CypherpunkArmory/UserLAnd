package tech.ula.utils

import android.content.Context
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class ExecUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var fileUtility: FileUtility

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var preferenceUtility: PreferenceUtility

    private val logCollector = ArrayList<String>()
    private val testLogger: (line: String) -> Unit = { logCollector.add(it) }

    lateinit var testDirectory: File

//    @InjectMocks
    lateinit var execUtility: ExecUtility

    @Before
    fun setup() {
        testDirectory = tempFolder.root
        logCollector.clear()

        MockitoAnnotations.initMocks(this)

        `when`(fileUtility.getSupportDirPath()).thenReturn(testDirectory.path)
        `when`(fileUtility.getFilesDirPath()).thenReturn(testDirectory.path)

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
        `when`(preferenceUtility.getProotDebuggingEnabled()).thenReturn(true)
        `when`(preferenceUtility.getProotDebuggingLevel()).thenReturn("9")
        `when`(preferenceUtility.getProotDebugLogLocation()).thenReturn(testDirectory.path)

        val commandToRun = arrayListOf("echo", "hello", "world")
        val doWait = true

        execUtility.execLocal(testDirectory, commandToRun, testLogger, doWait)
    }
}