package tech.ula.viewmodel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import android.content.ContentResolver
import android.net.Uri
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
import java.io.FileNotFoundException

@RunWith(MockitoJUnitRunner::class)
class FilesystemEditViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockUlaDatabase: UlaDatabase

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockSessionDao: SessionDao

    @Mock lateinit var mockObserver: Observer<FilesystemImportStatus>

    @Mock lateinit var mockContentResolver: ContentResolver

    @Mock lateinit var mockBackupUri: Uri

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

    @Test
    fun `insertFilesystemFromBackup posts UriUnselected if uri has not been set`() {
        filesystemEditViewModel.getImportStatusLiveData().observeForever(mockObserver)
        filesystemEditViewModel.backupUri = null

        val filesystem = Filesystem(0)

        runBlocking {
            filesystemEditViewModel.insertFilesystemFromBackup(mockContentResolver, filesystem, tempFolder.root, this)
        }

        verify(mockObserver).onChanged(UriUnselected)
    }

    @Test
    fun `insertFilesystemFromBackup removes db entry posts ImportFailure if input stream is null`() {
        val filesystem = Filesystem(0)
        filesystemEditViewModel.getImportStatusLiveData().observeForever(mockObserver)
        filesystemEditViewModel.backupUri = mockBackupUri
        whenever(mockFilesystemDao.insertFilesystem(filesystem))
                .thenReturn(0)
        whenever(mockContentResolver.openInputStream(mockBackupUri))
                .thenReturn(null)

        runBlocking {
            filesystemEditViewModel.insertFilesystemFromBackup(mockContentResolver, filesystem, tempFolder.root, this)
        }

        verify(mockObserver).onChanged(ImportFailure("Could not open input stream"))
        verify(mockFilesystemDao).deleteFilesystemById(0)
    }

    @Test
    fun `insertFilesystemFromBackup removes db entry and posts ImportFailure if an exception is caught`() {
        val filesystem = Filesystem(0)
        filesystemEditViewModel.getImportStatusLiveData().observeForever(mockObserver)
        filesystemEditViewModel.backupUri = mockBackupUri
        whenever(mockFilesystemDao.insertFilesystem(filesystem))
                .thenReturn(0)
        val exception = FileNotFoundException()
        whenever(mockContentResolver.openInputStream(mockBackupUri))
                .thenThrow(exception)

        runBlocking {
            filesystemEditViewModel.insertFilesystemFromBackup(mockContentResolver, filesystem, tempFolder.root, this)
        }

        verify(mockFilesystemDao).deleteFilesystemById(0)
        verify(mockObserver).onChanged(ImportFailure(exception.toString()))
    }

    @Test
    fun `insertFilesystemFromBackup sets filesystem isCreatedFromBackup property and moves backup file to correct location`() {
        val filesystem = Filesystem(id = 0, name = "test")
        whenever(mockFilesystemDao.insertFilesystem(filesystem)).thenReturn(1)

        val filesDir = tempFolder.root

        val backupText = "this is test text"
        val backupSourceFile = tempFolder.newFile("backupFile")
        backupSourceFile.writeText(backupText)

        val filesystemSupportDir = tempFolder.newFolder("1", "support")
        val expectedBackupTargetFile = File("${filesystemSupportDir.absolutePath}/rootfs.tar.gz")

        whenever(mockContentResolver.openInputStream(mockBackupUri))
                .thenReturn(backupSourceFile.inputStream())

        filesystemEditViewModel.backupUri = mockBackupUri
        filesystemEditViewModel.getImportStatusLiveData().observeForever(mockObserver)
        runBlocking {
            filesystemEditViewModel.insertFilesystemFromBackup(mockContentResolver, filesystem, filesDir, this)
        }

        filesystem.isCreatedFromBackup = true
        verify(mockFilesystemDao).insertFilesystem(filesystem)

        val readBackupText = expectedBackupTargetFile.readText()
        assertTrue(expectedBackupTargetFile.exists())
        assertEquals(backupText, readBackupText)
        verify(mockObserver).onChanged(ImportSuccess)
        assertEquals(null, filesystemEditViewModel.backupUri)
    }

    @Test
    fun `insertFilesystemFromBackup sets isAppsFilesystem if filesystem name is 'apps'`() {
        val filesystem = Filesystem(id = 0, name = "apps")
        whenever(mockFilesystemDao.insertFilesystem(filesystem)).thenReturn(1)

        val filesDir = tempFolder.root

        val backupText = "this is test text"
        val backupSourceFile = tempFolder.newFile("backupFile")
        backupSourceFile.writeText(backupText)

        val filesystemSupportDir = tempFolder.newFolder("1", "support")
        val expectedBackupTargetFile = File("${filesystemSupportDir.absolutePath}/rootfs.tar.gz")

        whenever(mockContentResolver.openInputStream(mockBackupUri))
                .thenReturn(backupSourceFile.inputStream())

        filesystemEditViewModel.backupUri = mockBackupUri
        filesystemEditViewModel.getImportStatusLiveData().observeForever(mockObserver)
        runBlocking {
            filesystemEditViewModel.insertFilesystemFromBackup(mockContentResolver, filesystem, filesDir, this)
        }

        filesystem.isAppsFilesystem = true
        filesystem.isCreatedFromBackup = true
        verify(mockFilesystemDao).insertFilesystem(filesystem)

        val readBackupText = expectedBackupTargetFile.readText()
        assertTrue(expectedBackupTargetFile.exists())
        assertEquals(backupText, readBackupText)
        verify(mockObserver).onChanged(ImportSuccess)
        assertEquals(null, filesystemEditViewModel.backupUri)
    }

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