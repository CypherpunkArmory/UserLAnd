package tech.ula.utils

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class FilesystemUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var execUtility: ExecUtility

    @Mock
    lateinit var fileUtility: FileUtility

    @Mock
    lateinit var buildUtility: BuildUtility

    val statelessListener: (line: String) -> Unit = { }

    lateinit var filesystemUtility: FilesystemUtility

    @Before
    fun setup() {
        `when`(fileUtility.getFilesDirPath()).thenReturn(tempFolder.root.path)
        filesystemUtility = FilesystemUtility(execUtility, fileUtility, buildUtility)
    }

    @Test
    fun extractFilesystemIsCalledWithCorrectArguments() {
        val targetDirectoryName = tempFolder.root.path
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"

        filesystemUtility.extractFilesystem(targetDirectoryName, statelessListener)
        verify(execUtility).wrapWithBusyboxAndExecute(targetDirectoryName, command, statelessListener)
    }

    @Test
    fun removesRootfsFilesFromFilesystem() {
        val filesystemName = "filesystem"
        val supportDirectory = tempFolder.newFolder(filesystemName, "support")
        val fsFiles = listOf("rootfs.tar.gz.part00", "rootfs.tar.gz.part01",
                "rootfs.tar.gz.part02", "rootfs.tar.gz.part03")
                .map { File("${supportDirectory.path}/$it") }

        filesystemUtility.removeRootfsFilesFromFilesystem(filesystemName)

        fsFiles.forEach { assertFalse(it.exists()) }
    }
}