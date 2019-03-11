package tech.ula.viewmodel

import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Filesystem
import tech.ula.model.repositories.UlaDatabase
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class FilesystemEditViewModelTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockUlaDatabase: UlaDatabase

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockSessionDao: SessionDao

    lateinit var filesystemEditViewModel: FilesystemEditViewModel

    @Before
    fun setup() {
        whenever(mockUlaDatabase.filesystemDao()).thenReturn(mockFilesystemDao)
        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)

        filesystemEditViewModel = FilesystemEditViewModel(mockUlaDatabase)
    }

    @Test
    fun `insertFilesystem delegates to model`() {
        val filesystem = Filesystem(id = 0, name = "test")

        runBlocking {
            filesystemEditViewModel.insertFilesystem(filesystem, this)
        }

        verify(mockFilesystemDao).insertFilesystem(filesystem)
    }

    // TODO: Fix test
//    @Test
//    fun `insertFilesystemFromBackup sets filesystem isCreatedFromBackup property and moves backup file to correct location`() {
//        val filesystem = Filesystem(id = 0, name = "test")
//        whenever(mockFilesystemDao.insertFilesystem(filesystem)).thenReturn(1)
//
//        val filesDir = tempFolder.root
//
//        val backupText = "this is test text"
//        val backupSourceFile = tempFolder.newFile("backupFile")
//        backupSourceFile.writeText(backupText)
//
//        val filesystemSupportDir = tempFolder.newFolder("1", "support")
//        val expectedBackupTargetFile = File("${filesystemSupportDir.absolutePath}/rootfs.tar.gz")
//
//        runBlocking {
//            filesystemEditViewModel.insertFilesystemFromBackup(filesystem, backupSourceFile.absolutePath, filesDir, this)
//        }
//
//        filesystem.isCreatedFromBackup = true
//        verify(mockFilesystemDao).insertFilesystem(filesystem)
//
//        val readBackupText = expectedBackupTargetFile.readText()
//        assertTrue(expectedBackupTargetFile.exists())
//        assertEquals(backupText, readBackupText)
//    }

    @Test
    fun `updateFilesystem delegates to model and also delegates that sessionDao should update all session names`() {
        val filesystem = Filesystem(id = 0, name = "test")

        runBlocking {
            filesystemEditViewModel.updateFilesystem(filesystem, this)
        }

        verify(mockFilesystemDao).updateFilesystem(filesystem)
        verify(mockSessionDao).updateFilesystemNamesForAllSessions()
    }
}