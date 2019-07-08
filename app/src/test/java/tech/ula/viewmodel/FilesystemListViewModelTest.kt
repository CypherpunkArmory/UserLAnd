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
import java.io.IOException

@RunWith(MockitoJUnitRunner::class)
class FilesystemListViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockSessionDao: SessionDao

    @Mock lateinit var mockFilesystemUtility: FilesystemUtility

    @Mock lateinit var mockFsListObserver: Observer<List<Filesystem>>

    @Mock lateinit var mockActiveSessionObserver: Observer<List<Session>>

    @Mock lateinit var mockViewStateObserver: Observer<FilesystemListViewState>

    @Mock lateinit var mockUri: Uri

    @Mock lateinit var mockContentResolver: ContentResolver

    private lateinit var filesystemsLiveData: MutableLiveData<List<Filesystem>>

    private lateinit var activeSessionsLiveData: MutableLiveData<List<Session>>

    private lateinit var viewStateLiveData: MutableLiveData<FilesystemListViewState>

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

        viewStateLiveData = MutableLiveData()

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
    fun `deleteFilesystemById delegates to the model layer properly and posts appropriate view states`() {
        val id = 0L

        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)
        runBlocking { filesystemListViewModel.deleteFilesystemById(id, this) }

        verifyBlocking(mockFilesystemUtility) { deleteFilesystem(id) }
        verify(mockFilesystemDao).deleteFilesystemById(id)
        verify(mockViewStateObserver).onChanged(FilesystemDeleteState.InProgress)
        verify(mockViewStateObserver).onChanged(FilesystemDeleteState.Success)
    }

    @Test
    fun `deleteFilesystemById does not delete entity and posts FilesystemDeleteState Failure if execution fails`() {
        val id = 0L

        runBlocking {
            whenever(mockFilesystemUtility.deleteFilesystem(id)).thenThrow(IOException())
        }

        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)
        runBlocking { filesystemListViewModel.deleteFilesystemById(id, this) }

        verify(mockFilesystemDao, never()).deleteFilesystemById(id)
        verify(mockViewStateObserver).onChanged(FilesystemDeleteState.Failure)
    }

    @Test
    fun `startExport posts FilesystemExportState Failure if attempting to export when there is an active session`() {
        val activeSession = Session(id = -1, name = "active", filesystemId = -1, active = true)
        val activeSessions = listOf(activeSession)

        activeSessionsLiveData.postValue(activeSessions)

        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)
        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockViewStateObserver).onChanged(FilesystemExportState.Failure(R.string.deactivate_sessions, ""))
    }

    @Test
    fun `startExport posts FilesystemExportState Failure if filesystemToBackup has not been set`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)
        runBlocking {
            filesystemListViewModel.startExport(filesDir, mockUri, mockContentResolver, this)
        }

        verify(mockViewStateObserver).onChanged(FilesystemExportState.Failure(R.string.error_export_filesystem_not_found))
    }

    @Test
    fun `startExport posts FilesystemExportState Failure if FilesystemUtility#compressFilesystem fails`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)

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

        verify(mockViewStateObserver).onChanged(FilesystemExportState.Failure(R.string.error_export_execution_failure, failureReason))
    }

    @Test
    fun `startExport posts FilesystemExportState Failure if an intermediate local backup is not created`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)

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

        verify(mockViewStateObserver).onChanged(FilesystemExportState.Failure(R.string.error_export_local_failure))
    }

    @Test
    fun `startExport posts FilesystemExportState Failure if an intermediate local backup has has no data`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)

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

        verify(mockViewStateObserver).onChanged(FilesystemExportState.Failure(R.string.error_export_local_failure))
    }

    @Test
    fun `startExport posts FilesystemExportState Failure if copying to external Uri fails`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)

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

        verify(mockViewStateObserver).onChanged(FilesystemExportState.Failure(R.string.error_export_copy_public_external_failure))
    }

    @Test
    fun `startExport posts FilesystemExportState Success if execution succeed, and deletes local intermediate`() {
        val filesDir = tempFolder.newFolder("files")

        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        filesystemListViewModel.getViewState().observeForever(mockViewStateObserver)

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

        assertFalse(localBackupFile.exists())
        verify(mockViewStateObserver).onChanged(FilesystemExportState.Success)
        assertEquals(expectedText, externalCopy.readText().trim())
    }
}