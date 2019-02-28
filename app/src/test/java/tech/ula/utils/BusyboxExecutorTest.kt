package tech.ula.utils

import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(MockitoJUnitRunner::class)
class BusyboxExecutorTest {

    @get:Rule val tempFolder = TemporaryFolder()

    lateinit var mockFilesDir: File

    lateinit var mockExternalStorage: File

    lateinit var mockFilesystemDir: File

    @Mock lateinit var mockDefaultPreferences: DefaultPreferences

    @Mock lateinit var mockBusyboxWrapper: BusyboxWrapper

    private val testFilesystemDirName = "filesystem"
    private val testProotDebugLevel = "9"
    private val prootDebugFileName = "PRoot_Debug_Log"

    private val outputCollection = mutableListOf<String>()
    private val testListener: (String) -> Unit = { outputCollection.add(it) }

    private lateinit var busyboxExecutor: BusyboxExecutor

    // Normally, the addition of busybox allows us to send the 'actual' command as a complete
    // string. Testing (and therefore just using the JVM runtime as the command executor) requires
    // the commands to be complete space-delimited.
    private fun String.toExecutableList(): List<String> {
        return this.split(" ").toList()
    }

    @Before
    fun setup() {
        outputCollection.clear()

        mockFilesDir = tempFolder.newFolder("files")
        mockExternalStorage = tempFolder.newFolder("external")

        mockFilesystemDir = File("${mockFilesDir.absolutePath}/$testFilesystemDirName")
        mockFilesDir.mkdirs()

        busyboxExecutor = BusyboxExecutor(mockFilesDir, mockExternalStorage, mockDefaultPreferences, mockBusyboxWrapper)
    }

    private fun stubBusyboxCommand(command: String) {
        whenever(mockBusyboxWrapper.addBusybox(command)).thenReturn(command.toExecutableList())
    }

    private fun stubBusyboxEnv() {
        whenever(mockBusyboxWrapper.getBusyboxEnv(mockFilesDir)).thenReturn(hashMapOf())
    }

    private fun stubProotDebuggingEnabled(enabled: Boolean) {
        if (enabled) {
            whenever(mockDefaultPreferences.getProotDebuggingEnabled()).thenReturn(true)
        } else {
            whenever(mockDefaultPreferences.getProotDebuggingEnabled()).thenReturn(false)
        }
        // Stub these regardless to ensure unwanted writes don't occur
        whenever(mockDefaultPreferences.getProotDebuggingLevel()).thenReturn(testProotDebugLevel)
        whenever(mockDefaultPreferences.getProotDebugLogLocation()).thenReturn("${mockExternalStorage.absolutePath}/$prootDebugFileName")
    }

    private fun stubProotCommand(command: String) {
        whenever(mockBusyboxWrapper.addBusyboxAndProot(command)).thenReturn(command.toExecutableList())
    }

    private fun stubProotEnv() {
        whenever(mockBusyboxWrapper.getProotEnv(mockFilesDir, mockFilesystemDir, testProotDebugLevel, mockExternalStorage))
                .thenReturn(hashMapOf())
    }

    @Test
    fun `Successfully executes legal commands, 'adding' busybox`() {
        val testOutput = "hello"
        val testCommand = "echo $testOutput"
        stubBusyboxCommand(testCommand)
        stubBusyboxEnv()

        val result = busyboxExecutor.executeCommand(testCommand, testListener)

        assertEquals(1, outputCollection.size)
        assertEquals(testOutput, outputCollection[0])
        assertTrue(result)
    }

    @Test(expected = IOException::class)
    fun `Fails to execute illegal commands, 'adding' busybox`() {
        val testCommand = "badCommand"
        stubBusyboxCommand(testCommand)
        stubBusyboxEnv()

        busyboxExecutor.executeCommand(testCommand, testListener)
    }

    @Test
    fun `Successfully executes legal commands, 'adding' proot and busybox`() {
        val testOutput = "hello"
        val testCommand = "echo $testOutput"
        stubProotDebuggingEnabled(false)
        stubProotCommand(testCommand)
        stubProotEnv()

        val result = busyboxExecutor.executeProotCommand(testCommand, testFilesystemDirName, true, hashMapOf(), testListener)

        assertEquals(1, outputCollection.size)
        assertEquals(testOutput, outputCollection[0])
        assertTrue(result.waitFor() == 0)
    }

    @Test
    fun `Overwrites PRoot debug logs with redirected output if logging is enabled`() {
        val testOutput = "hello"
        val testCommand = "echo $testOutput"
        val debugFile = File("${mockExternalStorage.absolutePath}/$prootDebugFileName")
        debugFile.createNewFile()
        debugFile.writeText("original text")
        stubProotDebuggingEnabled(true)
        stubProotCommand(testCommand)
        stubProotEnv()

        val resultProcess = runBlocking {
            busyboxExecutor.executeProotCommand(
                    testCommand,
                    testFilesystemDirName,
                    true,
                    hashMapOf(),
                    testListener,
                    this)
        }
        val resultExitValue = resultProcess.waitFor()
        val debugText = debugFile.readText()

        assertEquals(0, outputCollection.size)
        assertEquals(0, resultExitValue)

        assertEquals(testOutput, debugText.trim())
    }

    @Test(expected = IOException::class)
    fun `Fails to execute illegal commands, 'adding' proot and busybox`() {
        val testCommand = "badCommand"
        stubProotDebuggingEnabled(false)
        stubProotCommand(testCommand)
        stubProotEnv()

        busyboxExecutor.executeProotCommand(testCommand, testFilesystemDirName, true)
    }

    @Test
    fun `Recursively deletes files`() = runBlocking {
        val testDirName = "testDir"
        val testDir = File("${mockFilesDir.absolutePath}/$testDirName")
        val testFileName = "testFile"
        val testFile = File("${testDir.absolutePath}/$testFileName")

        testDir.mkdirs()
        assertTrue(testDir.exists() && testDir.isDirectory)
        testFile.createNewFile()
        assertTrue(testFile.exists())

        val command = "rm -rf ${testDir.absolutePath}"
        stubBusyboxCommand(command)
        stubBusyboxEnv()

        busyboxExecutor.recursivelyDelete(testDir.absolutePath)

        assertTrue(mockFilesDir.exists())
        assertFalse(testDir.exists())
        assertFalse(testFile.exists())
    }

    @Test
    fun `Calling recursivelyDelete on a single file also works`() = runBlocking {
        val testFileName = "testFile"
        val testFile = File("${mockFilesDir.absolutePath}/$testFileName")

        testFile.createNewFile()
        assertTrue(testFile.exists())

        val command = "rm -rf ${testFile.absolutePath}"
        stubBusyboxCommand(command)
        stubBusyboxEnv()

        busyboxExecutor.recursivelyDelete(testFile.absolutePath)

        assertTrue(mockFilesDir.exists())
        assertFalse(testFile.exists())
    }

    @Test
    fun `recursivelyDelete does not follow symbolic links`() = runBlocking {
        val originalTestDirName = "testDir"
        val originalTestDir = File("${mockExternalStorage.absolutePath}/$originalTestDirName")
        val originalTestFileName = "testFile"
        val originalTestFile = File("${originalTestDir.absolutePath}/$originalTestFileName")

        originalTestDir.mkdirs()
        assertTrue(originalTestDir.exists() && originalTestDir.isDirectory)
        originalTestFile.createNewFile()
        assertTrue(originalTestFile.exists())

        val symbolicDirLinkFile = File("$mockFilesDir/testSymDir")
        val symbolicDirLinkPath = symbolicDirLinkFile.toPath()
        assertFalse(symbolicDirLinkFile.exists())

        Files.createSymbolicLink(symbolicDirLinkPath, originalTestDir.toPath())
        assertTrue(symbolicDirLinkFile.exists())
        assertTrue(Files.isSymbolicLink(symbolicDirLinkPath))

        val linkedFile = File("${symbolicDirLinkFile.absolutePath}/$originalTestFileName")
        assertTrue(linkedFile.exists())

        val command = "rm -rf ${symbolicDirLinkFile.absolutePath}"
        stubBusyboxCommand(command)
        stubBusyboxEnv()

        busyboxExecutor.recursivelyDelete(symbolicDirLinkFile.absolutePath)

        assertFalse(symbolicDirLinkFile.exists())
        assertFalse(linkedFile.exists())
        assertTrue(originalTestDir.exists())
        assertTrue(originalTestFile.exists())
    }
}