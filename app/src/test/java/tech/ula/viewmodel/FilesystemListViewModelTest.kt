package tech.ula.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@RunWith(MockitoJUnitRunner::class)
class FilesystemListViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockSessionDao: SessionDao

    @Mock lateinit var mockFilesystemUtility: FilesystemUtility

    @Mock lateinit var mockFsListObserver: Observer<List<Filesystem>>

    @Mock lateinit var mockExportObserver: Observer<FilesystemExportStatus>

    private lateinit var filesystemsLiveData: MutableLiveData<List<Filesystem>>

    private lateinit var exportStatusLiveData: MutableLiveData<FilesystemExportStatus>

    private val filesystemName = "fsname"
    private val filesystemType = "fstype"
    private val rootfsString = "rootfs.tar.gz"
    private val expectedBackupName = "$filesystemName-$filesystemType-$rootfsString"

    private lateinit var filesystemListViewModel: FilesystemListViewModel

    @Before
    fun setup() {
        filesystemsLiveData = MutableLiveData()
        whenever(mockFilesystemDao.getAllFilesystems()).thenReturn(filesystemsLiveData)

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
    fun `compressFilesystem posts ExportFailure attempting to export when there is an active session`() {
        val activeSession = Session(id = -1, name = "active", filesystemId = -1, active = true)
        val activeSessions = listOf(activeSession)

        val activeSessionsLiveData = MutableLiveData<List<Session>>()
        val mockActiveSessionObserver = mock<Observer<List<Session>>>()
        whenever(mockSessionDao.findActiveSessions()).thenReturn(activeSessionsLiveData)
        filesystemListViewModel.getAllActiveSessions().observeForever(mockActiveSessionObserver)
        activeSessionsLiveData.postValue(activeSessions)

        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val externalDir = tempFolder.newFolder("external")

        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.startExport(filesystem, filesDir, externalDir)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.deactivate_sessions, ""))
    }

    @Test
    fun `compressFilesystem copies backup to external scoped storage and posts ExportSuccess`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val scopedDir = tempFolder.newFolder("scoped")

        val expectedText = "test"
        val expectedLocalBackupFile = File(filesDir, expectedBackupName)
        expectedLocalBackupFile.createNewFile()
        expectedLocalBackupFile.writeText(expectedText)

        val expectedScopedBackupFile = File(scopedDir, expectedBackupName)
        assertFalse(expectedScopedBackupFile.exists())

        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, scopedDir, this)
        }

        verify(mockExportObserver).onChanged(ExportStarted)
        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportSuccess(expectedBackupName))
        assertFalse(expectedLocalBackupFile.exists())
        assertEquals(expectedText, expectedScopedBackupFile.readText().trim())
    }

    @Test
    fun `compressFilesystem copies backup to external scoped storage and overwrites existing backup`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val scopedDir = tempFolder.newFolder("scoped")

        val expectedText = "test"
        val expectedLocalBackupFile = File(filesDir, expectedBackupName)
        expectedLocalBackupFile.createNewFile()
        expectedLocalBackupFile.writeText(expectedText)

        val expectedScopedBackupFile = File(scopedDir, expectedBackupName)
        expectedScopedBackupFile.writeText("hello world")

        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, scopedDir, this)
        }

        verify(mockExportObserver).onChanged(ExportStarted)
        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportSuccess(expectedBackupName))
        assertEquals(expectedText, expectedScopedBackupFile.readText().trim())
    }

    @Test
    fun `compressFilesystem posts ExportFailure if there is an error in execution`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val externalDir = tempFolder.newFolder("external")
        val expectedLocalBackupFile = File("${filesDir.absolutePath}/$expectedBackupName")

        assertFalse(expectedLocalBackupFile.exists())
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        val failureDetails = "details"
        runBlocking {
            whenever(mockFilesystemUtility.compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()))
                    .thenReturn(FailedExecution(failureDetails))
        }

        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, externalDir, this)
        }

        verify(mockExportObserver).onChanged(ExportStarted)
        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_execution_failure, failureDetails))
    }

    @Test
    fun `compressFilesystem posts ExportFailure if the local backup is not created`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val externalDir = tempFolder.newFolder("external")
        val expectedLocalBackupFile = File(filesDir, expectedBackupName)

        assertFalse(expectedLocalBackupFile.exists())
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            whenever(mockFilesystemUtility.compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()))
                    .thenReturn(SuccessfulExecution)
        }

        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, externalDir, this)
        }

        verify(mockExportObserver).onChanged(ExportStarted)
        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_local_failure, ""))
    }

    // TODO test 'local backup copy to scoped fails' '0 size file failure' 'copyExportToExternal`
}