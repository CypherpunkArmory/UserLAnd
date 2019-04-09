package tech.ula.utils

import com.nhaarman.mockitokotlin2.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.runBlocking
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import java.io.File
import java.io.IOException

@RunWith(MockitoJUnitRunner::class)
class FilesystemUtilityTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var applicationFilesDirPath: String

    @Mock lateinit var mockBusyboxExecutor: BusyboxExecutor

    @Mock lateinit var mockAcraWrapper: AcraWrapper

    private val statelessListener: (line: String) -> Unit = { }

    private lateinit var filesystemUtility: FilesystemUtility

    private val filesystemExtractionSuccess = ".success_filesystem_extraction"
    private val filesystemExtractionFailure = ".failure_filesystem_extraction"

    @Before
    fun setup() {
        applicationFilesDirPath = tempFolder.root.path
        filesystemUtility = FilesystemUtility(applicationFilesDirPath, mockBusyboxExecutor, mockAcraWrapper)
    }

    @Test
    fun `Calling extract filesystem uses the appropriate command`() {
        val command = "/support/common/extractFilesystem.sh"

        val requiredFilesystemType = "testDist"
        val fakeArchitecture = "testArch"
        val filesystem = Filesystem(0, "apps",
                archType = fakeArchitecture, distributionType = requiredFilesystemType, isAppsFilesystem = true,
                defaultUsername = "username", defaultPassword = "password", defaultVncPassword = "vncpass")
        val filesystemDirName = "${filesystem.id}"

        val defaultEnvironmentalVariables = hashMapOf("INITIAL_USERNAME" to "username",
                "INITIAL_PASSWORD" to "password", "INITIAL_VNC_PASSWORD" to "vncpass")
        whenever(mockBusyboxExecutor.executeProotCommand(
                eq(command),
                eq(filesystemDirName),
                eq(true),
                eq(defaultEnvironmentalVariables),
                eq(statelessListener),
                anyOrNull()
        ))
                .thenReturn(SuccessfulExecution)

        val result = runBlocking {
            filesystemUtility.extractFilesystem(filesystem, statelessListener)
        }
        verify(mockBusyboxExecutor).executeProotCommand(
                eq(command),
                eq(filesystemDirName),
                eq(true),
                eq(defaultEnvironmentalVariables),
                eq(statelessListener),
                anyOrNull()
        )
        assertTrue(result is SuccessfulExecution)
    }

    @Test
    fun `extractFilesystem logs errors`() {
        val command = "/support/common/extractFilesystem.sh"

        val requiredFilesystemType = "testDist"
        val fakeArchitecture = "testArch"
        val filesystem = Filesystem(0, "apps",
                archType = fakeArchitecture, distributionType = requiredFilesystemType, isAppsFilesystem = true,
                defaultUsername = "username", defaultPassword = "password", defaultVncPassword = "vncpass")
        val filesystemDirName = "${filesystem.id}"

        val defaultEnvironmentalVariables = hashMapOf("INITIAL_USERNAME" to "username",
                "INITIAL_PASSWORD" to "password", "INITIAL_VNC_PASSWORD" to "vncpass")

        val failureReason = "reason"
        whenever(mockBusyboxExecutor.executeProotCommand(
                eq(command),
                eq(filesystemDirName),
                eq(true),
                eq(defaultEnvironmentalVariables),
                eq(statelessListener),
                anyOrNull()
        ))
                .thenReturn(FailedExecution(failureReason))

        val result = runBlocking { filesystemUtility.extractFilesystem(filesystem, statelessListener) }

        assertEquals(FailedExecution(failureReason), result)
    }

    @Test
    fun `compressFilesystem uses correct command and environment`() {
        val command = "/support/common/compressFilesystem.sh"
        val filesystem = Filesystem(id = 0, name = "backup", distributionType = "distType")
        val externalStorageDirectory = tempFolder.root

        val destinationName = "rootfs.tar.gz"
        val destinationPath = "${externalStorageDirectory.absolutePath}/$destinationName"
        val expectedEnv = hashMapOf("TAR_PATH" to destinationPath)

        whenever(mockBusyboxExecutor.executeProotCommand(
                eq(command),
                eq("${filesystem.id}"),
                eq(true),
                eq(expectedEnv),
                eq(statelessListener),
                anyOrNull()
        ))
                .thenReturn(SuccessfulExecution)

        val result = runBlocking {
            filesystemUtility.compressFilesystem(filesystem, File(destinationPath), statelessListener)
        }
        verify(mockBusyboxExecutor).executeProotCommand(
                eq(command),
                eq("${filesystem.id}"),
                eq(true),
                eq(expectedEnv),
                eq(statelessListener),
                anyOrNull()
        )
        assertTrue(result is SuccessfulExecution)
    }

    @Test
    fun `compressFilesystem logs failures`() {
        val command = "/support/common/compressFilesystem.sh"
        val filesystem = Filesystem(id = 0, name = "backup", distributionType = "distType")
        val externalStorageDirectory = tempFolder.root

        val destinationName = "rootfs.tar.gz"
        val destinationPath = "${externalStorageDirectory.absolutePath}/$destinationName"
        val expectedEnv = hashMapOf("TAR_PATH" to destinationPath)

        val failureReason = "reason"
        whenever(mockBusyboxExecutor.executeProotCommand(
                eq(command),
                eq("${filesystem.id}"),
                eq(true),
                eq(expectedEnv),
                eq(statelessListener),
                anyOrNull()
        ))
                .thenReturn(FailedExecution(failureReason))

        val result = runBlocking {
            filesystemUtility.compressFilesystem(filesystem, File(destinationPath), statelessListener)
        }
        assertEquals(FailedExecution(failureReason), result)
    }

    @Test
    fun `copyAssetsToFilesystem copies to the right directory, including rootfs files if filesystem is not from backup`() {
        val filesystem = Filesystem(id = 0, distributionType = "dist", isCreatedFromBackup = false)
        val targetDirectoryName = "${filesystem.id}"
        val distType = filesystem.distributionType
        val filenames = listOf("asset1", "asset2", "asset3", "asset4", "rootfs.tar.gz")

        val targetDirectory = File("${tempFolder.root.path}/$targetDirectoryName/support")
        val targetFiles = filenames.map { File("${targetDirectory.path}/$it") }

        val sharedDirectory = tempFolder.newFolder(distType)
        val sharedFiles = filenames.map { File("${sharedDirectory.path}/$it") }
        sharedFiles.forEach { it.createNewFile() }

        assertFalse(targetDirectory.exists())
        targetFiles.forEach { assertFalse(it.exists()) }
        sharedFiles.forEach { assertTrue(it.exists()) }

        filesystemUtility.copyAssetsToFilesystem(filesystem)

        assertTrue(targetDirectory.exists())
        targetFiles.forEach { file ->
            assertTrue(file.exists())
            var output = ""
            val proc = Runtime.getRuntime().exec("ls -l ${file.path}")

            proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { output += it }
            val permissions = output.substring(0, 10)
            assertTrue(permissions == "-rwxrwxrwx")
        }
    }

    @Test
    fun `copyAssetsToFilesystem copies to the right directory, excluding rootfs files if filesystem is from backup`() {
        val filesystem = Filesystem(id = 0, distributionType = "dist", isCreatedFromBackup = true)
        val targetDirectoryName = "${filesystem.id}"
        val distType = filesystem.distributionType
        val filenames = listOf("asset1", "asset2", "asset3", "asset4", "rootfs.tar.gz")

        val targetDirectory = File("${tempFolder.root.path}/$targetDirectoryName/support")
        val targetFiles = filenames.map { File("${targetDirectory.path}/$it") }

        val sharedDirectory = tempFolder.newFolder(distType)
        val sharedFiles = filenames.map { File("${sharedDirectory.path}/$it") }
        sharedFiles.forEach { it.createNewFile() }

        assertFalse(targetDirectory.exists())
        targetFiles.forEach { assertFalse(it.exists()) }
        sharedFiles.forEach { assertTrue(it.exists()) }

        filesystemUtility.copyAssetsToFilesystem(filesystem)

        assertTrue(targetDirectory.exists())
        targetFiles.forEach { file ->
            if (file.name == "rootfs.tar.gz") {
                assertFalse(file.exists())
                return@forEach
            }
            assertTrue(file.exists())
            var output = ""
            val proc = Runtime.getRuntime().exec("ls -l ${file.path}")

            proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { output += it }
            val permissions = output.substring(0, 10)
            assertTrue(permissions == "-rwxrwxrwx")
        }
    }

    @Test
    fun extractionIsIncompleteIfNeitherStatusFileExists() {
        val supportDirectory = tempFolder.newFolder("target", "support")

        assertFalse(File("${supportDirectory.path}/$filesystemExtractionSuccess").exists())
        assertFalse(File("${supportDirectory.path}/$filesystemExtractionFailure").exists())

        assertFalse(filesystemUtility.isExtractionComplete("target"))
    }

    @Test
    fun extractionIsCompleteIfEitherStatusFileExists() {
        val supportDirectory = tempFolder.newFolder("target", "support")
        val successFile = File("${supportDirectory.path}/$filesystemExtractionSuccess")
        val failureFile = File("${supportDirectory.path}/$filesystemExtractionFailure")

        assertFalse(successFile.exists())
        assertFalse(failureFile.exists())

        assertFalse(filesystemUtility.isExtractionComplete("target"))

        successFile.createNewFile()
        assertTrue(filesystemUtility.isExtractionComplete("target"))
        successFile.delete()

        assertFalse(filesystemUtility.isExtractionComplete("target"))

        failureFile.createNewFile()
        assertTrue(filesystemUtility.isExtractionComplete("target"))
    }

    @Test
    fun filesystemHasOnlyBeenSuccessfullyExtractedIfSuccessStatusFileExists() {
        val supportDirectory = tempFolder.newFolder("target", "support")
        val successFile = File("${supportDirectory.path}/$filesystemExtractionSuccess")
        val failureFile = File("${supportDirectory.path}/$filesystemExtractionFailure")

        failureFile.createNewFile()
        assertFalse(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("target"))

        successFile.createNewFile()
        assertTrue(filesystemUtility.hasFilesystemBeenSuccessfullyExtracted("target"))
    }

    @Test
    fun onlySucceedsIfAllRequiredAssetsArePresent() {
        val assets = listOf(
                Asset("name1", "dist1"),
                Asset("name2", "dist2"))

        // Should fail if the directory doesn't exist
        assertFalse(filesystemUtility.areAllRequiredAssetsPresent("target", assets))

        val supportDirectory = tempFolder.newFolder("target", "support")
        assertFalse(filesystemUtility.areAllRequiredAssetsPresent("target", assets))

        File("${supportDirectory.path}/name1").createNewFile()
        assertFalse(filesystemUtility.areAllRequiredAssetsPresent("target", assets))

        File("${supportDirectory.path}/name2").createNewFile()
        assertTrue(filesystemUtility.areAllRequiredAssetsPresent("target", assets))
    }

    @Test
    fun removesRootfsFilesFromFilesystem() {
        val filesystemName = "filesystem"
        val supportDirectory = tempFolder.newFolder(filesystemName, "support")
        val fsFiles = listOf("rootfs.tar.gz.part00", "rootfs.tar.gz.part01",
                "rootfs.tar.gz.part02", "rootfs.tar.gz.part03")
                .map { File("${supportDirectory.path}/$it") }

        fsFiles.forEach { it.createNewFile() }
        fsFiles.forEach { assertTrue(it.exists()) }

        filesystemUtility.removeRootfsFilesFromFilesystem(filesystemName)

        fsFiles.forEach { assertFalse(it.exists()) }
    }

    @Test
    fun `Exits early if deleteFilesystem called on a path that does not exist`() {
        val testFile = File("${tempFolder.root.path}/100")
        assertFalse(testFile.exists())

        runBlocking {
            filesystemUtility.deleteFilesystem(100)
            verify(mockBusyboxExecutor, never()).recursivelyDelete(any())
        }
    }

    @Test
    fun `Exits early if deleteFilesystem called on a path that is not a directory`() {
        val testFile = File("${tempFolder.root.path}/100")
        testFile.createNewFile()
        assertTrue(testFile.exists())

        runBlocking {
            filesystemUtility.deleteFilesystem(100)
            verify(mockBusyboxExecutor, never()).recursivelyDelete(any())
        }
    }

    @Test
    fun `Calling deleteFilesystem issues recursivelyDelete to busyboxExecutor`() {
        val testDir = File("${tempFolder.root.path}/100")
        testDir.mkdirs()
        assertTrue(testDir.exists() && testDir.isDirectory)

        runBlocking {
            whenever(mockBusyboxExecutor.recursivelyDelete(testDir.absolutePath))
                    .thenReturn(SuccessfulExecution)
            filesystemUtility.deleteFilesystem(100)
            verify(mockBusyboxExecutor).recursivelyDelete(testDir.absolutePath)
        }
    }

    @Test(expected = IOException::class)
    fun `Log errors when deleteFilesystem fails`() {
        val testDir = File("${tempFolder.root.path}/100")
        testDir.mkdirs()
        assertTrue(testDir.exists() && testDir.isDirectory)

        runBlocking {
            whenever(mockBusyboxExecutor.recursivelyDelete(testDir.absolutePath))
                    .thenReturn(FailedExecution(""))
            filesystemUtility.deleteFilesystem(100)
        }
        verifyBlocking(mockBusyboxExecutor) { recursivelyDelete(testDir.absolutePath) }
        verify(mockAcraWrapper).logException(IOException())
    }

    @Test
    fun `moveAppScript moves the script to right location`() {
        val sourceAppName = "firefox"
        val sourceAppContents = "hello there"
        val sourceAppScriptDir = File("${tempFolder.root.absolutePath}/apps/$sourceAppName")
        val sourceAppScriptFile = File("${sourceAppScriptDir.absolutePath}/$sourceAppName.sh")
        sourceAppScriptDir.mkdirs()
        sourceAppScriptFile.createNewFile()
        sourceAppScriptFile.writeText(sourceAppContents)

        val targetAppScriptName = "zzzzzzzzzzzzzzzz.sh"
        val filesystem = Filesystem(id = 5, name = "apps")
        val filesystemProfileDDir = File("${tempFolder.root.absolutePath}/${filesystem.id}/etc/profile.d")
        val targetAppLocation = File("${filesystemProfileDDir.absolutePath}/$targetAppScriptName")
        assertFalse(filesystemProfileDDir.exists())

        filesystemUtility.moveAppScriptToRequiredLocation(sourceAppName, filesystem)

        assertTrue(targetAppLocation.exists())
        assertEquals(sourceAppContents, targetAppLocation.readText())
    }

    @Test(expected = IOException::class)
    fun `moveAppScript logs and throws exception on error`() {
        val filesystem = Filesystem(id = 5, name = "apps")
        val sourceAppName = "firefox"
        val sourceAppScriptDir = File("${tempFolder.root.absolutePath}/apps/$sourceAppName")
        sourceAppScriptDir.mkdirs()

        filesystemUtility.moveAppScriptToRequiredLocation(sourceAppName, filesystem)

        verify(mockAcraWrapper).logException(IOException())
    }
}