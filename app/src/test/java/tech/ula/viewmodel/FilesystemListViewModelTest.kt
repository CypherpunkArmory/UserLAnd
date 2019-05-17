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

    @Test
    fun `compressFilesystem posts ExportFailure if an empty backup is copied`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val scopedDir = tempFolder.newFolder("scoped")

        val expectedLocalBackupFile = File(filesDir, expectedBackupName)
        expectedLocalBackupFile.createNewFile()

        val expectedScopedBackupFile = File(scopedDir, expectedBackupName)
        assertFalse(expectedScopedBackupFile.exists())

        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, scopedDir, this)
        }

        verify(mockExportObserver).onChanged(ExportStarted)
        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_scoped_failure_no_data))
    }

    @Test
    fun `copyBackupToExternal posts ExportFailure if the currentBackupName is not set`() {
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)

        val scopedExternal = tempFolder.newFolder("scoped")
        val mockUri = mock<Uri>()
        val mockContentResolver = mock<ContentResolver>()

        runBlocking {
            filesystemListViewModel.copyBackupToExternal(scopedExternal, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_name_not_found))
    }

    @Test
    fun `copyBackupToExternal posts ExportFailure if copying fails`() {
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val scopedDir = tempFolder.newFolder("scoped")

        val expectedText = "test"
        val expectedLocalBackupFile = File(filesDir, expectedBackupName)
        expectedLocalBackupFile.createNewFile()
        expectedLocalBackupFile.writeText(expectedText)

        val expectedScopedBackupFile = File(scopedDir, expectedBackupName)
        assertFalse(expectedScopedBackupFile.exists())

        val mockUri = mock<Uri>()
        val mockContentResolver = mock<ContentResolver>()

        whenever(mockContentResolver.openOutputStream(mockUri, "w"))
                .thenThrow(FileNotFoundException())

        // These must be run in separate blocks to force the coroutines to complete
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, scopedDir, this)
        }
        runBlocking {
            filesystemListViewModel.copyBackupToExternal(scopedDir, mockUri, mockContentResolver, this)
        }

        verify(mockExportObserver).onChanged(ExportFailure(R.string.error_export_copy_public_external_failure))
    }

    @Test
    fun `copyBackupToExternal successfully copies backup to external`() {
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val scopedDir = tempFolder.newFolder("scoped")
        val externalDir = tempFolder.newFolder("external")

        val expectedText = "test"
        val expectedLocalBackupFile = File(filesDir, expectedBackupName)
        expectedLocalBackupFile.createNewFile()
        expectedLocalBackupFile.writeText(expectedText)

        val expectedExternalFile = File(externalDir, expectedBackupName)

        val mockUri = mock<Uri>()
        val mockContentResolver = mock<ContentResolver>()

        whenever(mockContentResolver.openOutputStream(mockUri, "w"))
                .thenReturn(expectedExternalFile.outputStream())

        // These must be run in separate blocks to force the coroutines to complete
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, scopedDir, this)
        }
        runBlocking {
            filesystemListViewModel.copyBackupToExternal(scopedDir, mockUri, mockContentResolver, this)
        }

        assertTrue(expectedExternalFile.exists())
        assertEquals(expectedText, expectedExternalFile.readText().trim())
    }
}