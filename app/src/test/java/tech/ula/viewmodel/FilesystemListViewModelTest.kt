package tech.ula.viewmodel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.entities.Filesystem
import tech.ula.utils.FilesystemUtility
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class FilesystemListViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockFilesystemUtility: FilesystemUtility

    @Mock lateinit var mockFsListObserver: Observer<List<Filesystem>>

    @Mock lateinit var mockExportObserver: Observer<FilesystemExportStatus>

    private lateinit var filesystemsLiveData: MutableLiveData<List<Filesystem>>

    private val filesystemName = "fsname"
    private val filesystemType = "fstype"
    private val rootfsString = "rootfs.tar.gz"
    private val expectedBackupName = "$filesystemName-$filesystemType-$rootfsString"

    private lateinit var filesystemListViewModel: FilesystemListViewModel

    @Before
    fun setup() {
        filesystemsLiveData = MutableLiveData()
        whenever(mockFilesystemDao.getAllFilesystems()).thenReturn(filesystemsLiveData)

        filesystemListViewModel = FilesystemListViewModel(mockFilesystemDao, mockFilesystemUtility)
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
    fun `compressFilesystem copies backup to external and posts ExportSuccess`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val externalDir = tempFolder.newFolder("external")

        val expectedLocalBackupFile = File("${filesDir.absolutePath}/$rootfsString")
        expectedLocalBackupFile.createNewFile()

        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, externalDir, this)
        }

        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportSuccess)
    }

    @Test
    fun `compressFilesystem posts ExportFailure if the local backup is not created`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val externalDir = tempFolder.newFolder("external")
        val expectedLocalBackupFile = File("${filesDir.absolutePath}/$rootfsString")

        assertFalse(expectedLocalBackupFile.exists())
        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, externalDir, this)
        }

        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportFailure("Exporting to local directory failed"))
    }

    @Test
    fun `compressFilesystem posts ExportFailure if a backup of the same name already exists externally`() {
        val filesystem = Filesystem(id = 0, name = filesystemName, distributionType = filesystemType)
        val filesDir = tempFolder.newFolder("files")
        val externalDir = tempFolder.newFolder("external")

        val expectedLocalBackupFile = File("${filesDir.absolutePath}/$rootfsString")
        expectedLocalBackupFile.createNewFile()

        val expectedExternalBackupFile = File("${externalDir.absolutePath}/$expectedBackupName")
        expectedExternalBackupFile.createNewFile()

        filesystemListViewModel.getExportStatusLiveData().observeForever(mockExportObserver)
        runBlocking {
            filesystemListViewModel.compressFilesystemAndExportToStorage(filesystem, filesDir, externalDir, this)
        }

        assertFalse(expectedLocalBackupFile.exists())
        verifyBlocking(mockFilesystemUtility) { compressFilesystem(eq(filesystem), eq(expectedLocalBackupFile), anyOrNull()) }
        verify(mockExportObserver).onChanged(ExportFailure("Exporting to external directory failed"))
    }
}