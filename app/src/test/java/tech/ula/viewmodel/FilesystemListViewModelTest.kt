package tech.ula.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
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
import tech.ula.R
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.FailedExecution
import tech.ula.utils.FilesystemUtility
import tech.ula.utils.SuccessfulExecution
import java.io.File
import java.io.FileNotFoundException

@RunWith(MockitoJUnitRunner::class)
class FilesystemListViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockSessionDao: SessionDao

    @Mock lateinit var mockFilesystemUtility: FilesystemUtility

    @Mock lateinit var mockFsListObserver: Observer<List<Filesystem>>

    @Mock lateinit var mockActiveSessionObserver: Observer<List<Session>>

    @Mock lateinit var mockExportObserver: Observer<FilesystemExportStatus>

    @Mock lateinit var mockUri: Uri

    @Mock lateinit var mockContentResolver: ContentResolver

    private lateinit var filesystemsLiveData: MutableLiveData<List<Filesystem>>

    private lateinit var activeSessionsLiveData: MutableLiveData<List<Session>>

    private lateinit var exportStatusLiveData: MutableLiveData<FilesystemExportStatus>

    private val filesystemName = "fsname"
    private val filesystemType = "fstype"
    private val rootfsString = "rootfs.tar.gz"
    private val expectedBackupName = "$filesystemName-$filesystemType-$rootfsString"

    private lateinit var filesystemListViewModel: FilesystemListViewModel

    @Before
    fun setup() {
        filesystemsLiveData = MutableLiveData()
        activeSessionsLiveData = MutableLiveData()
        whenever(mockFilesystemDao.getAllFilesystems()).thenReturn(filesystemsLiveData)
        whenever(mockSessionDao.findActiveSessions()).thenReturn(activeSessionsLiveData)
        activeSessionsLiveData.postValue(listOf())

        exportStatusLiveData = MutableLiveData()

        filesystemListViewModel = FilesystemListViewModel(mockFilesystemDao, mockSessionDao, mockFilesystemUtility)
    }

    @Test
    fun `getAllFilesystems returns LiveData acquired from model layer`() {
        val testFilesystemName = "test"
        val testFilesystemList = listOf(Filesystem(id = 0, name = testFilesystemName))

        filesystemListViewModel.getAllFilesystems().observeForever(mockFsListObserver)
        filesystemsLiveData.postValue(testFilesystemList)

        verify(mockFsListObserver).onChanged(testFilesystemList)
    }

    @Test
    fun `deleteFilesystemById delegates to the model layer properly`() {
        val id = 0L

        runBlocking { filesystemListViewModel.deleteFilesystemById(id, this) }

        verify(mockFilesystemDao).deleteFilesystemById(id)
    }

    @Test
    fun `startExport posts ExportFailure if attempting to export when there is an active session`() {
        val activeSession = Session(id = -1, name = "active", filesystemId = -1, active = true)
        val activeSessions = listOf(activeSession)

        activeSessionsLiveData.postValue(activeSessions)

        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.deactivate_sessions, ""))
    }

    @Test
    fun `startExport posts ExportFailure if filesystemToBackup has not been set`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_filesystem_not_found))
    }

    @Test
    fun `startExport posts ExportFailure if FilesystemUtility#compressFilesystem fails`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)

        val localBackupFile = File(filesDir, expectedBackupName)
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        filesystemListViewModel.setFilesystemToBackup(filesystem)

        val failureReason = "reason"
        runBlocking {
            whenever(mockFilesystemUtility.compressFilesystem(eq(filesystem), eq(localBackupFile), anyOrNull()))
                    .thenReturn(FailedExecution(failureReason))
        }

        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_execution_failure, failureReason))
    }

    @Test
    fun `startExport posts ExportFailure if an intermediate local backup is not created`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)

        val localBackupFile = File(filesDir, expectedBackupName)
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        filesystemListViewModel.setFilesystemToBackup(filesystem)
        runBlocking {
            whenever(mockFilesystemUtility.compressFilesystem(eq(filesystem), eq(localBackupFile), anyOrNull()))
                    .thenReturn(SuccessfulExecution)
        }

        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_local_failure))
    }

    @Test
    fun `startExport posts ExportFailure if an intermediate local backup has has no data`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)

        val localBackupFile = File(filesDir, expectedBackupName)
        localBackupFile.createNewFile()
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        filesystemListViewModel.setFilesystemToBackup(filesystem)
        runBlocking {
            whenever(mockFilesystemUtility.compressFilesystem(eq(filesystem), eq(localBackupFile), anyOrNull()))
                    .thenReturn(SuccessfulExecution)
        }

        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_local_failure))
    }

    @Test
    fun `startExport posts ExportFailure if copying to external Uri fails`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)

        val localBackupFile = File(filesDir, expectedBackupName)
        localBackupFile.writeText("test")
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        filesystemListViewModel.setFilesystemToBackup(filesystem)
        runBlocking {
            whenever(mockFilesystemUtility.compressFilesystem(eq(filesystem), eq(localBackupFile), anyOrNull()))
                    .thenReturn(SuccessfulExecution)
        }

        whenever(mockContentResolver.openOutputStream(mockUri, "w"))
                .thenThrow(FileNotFoundException())

        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_copy_public_external_failure))
    }

    @Test
    fun `startExport posts ExportSuccess if execution succeeds`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)

        val expectedText = "test"
        val localBackupFile = File(filesDir, expectedBackupName)
        localBackupFile.writeText(expectedText)

        val externalDir = tempFolder.newFolder("external")
        val externalCopy = File(externalDir, "copy")

        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        filesystemListViewModel.setFilesystemToBackup(filesystem)
        runBlocking {
            whenever(mockFilesystemUtility.compressFilesystem(eq(filesystem), eq(localBackupFile), anyOrNull()))
                    .thenReturn(SuccessfulExecution)
        }

        whenever(mockContentResolver.openOutputStream(mockUri, "w"))
                .thenReturn(externalCopy.outputStream())

        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportSuccess)
        assertEquals(expectedText, externalCopy.readText().trim())
    }
}