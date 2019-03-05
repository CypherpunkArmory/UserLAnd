package tech.ula.viewmodel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyBlocking
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockFilesystemUtility: FilesystemUtility

    @Mock lateinit var mockObserver: Observer<List<Filesystem>>

    private lateinit var filesystemsLiveData: MutableLiveData<List<Filesystem>>

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

        filesystemListViewModel.getAllFilesystems().observeForever(mockObserver)
        filesystemsLiveData.postValue(testFilesystemList)

        verify(mockObserver).onChanged(testFilesystemList)
    }

    @Test
    fun `deleteFilesystemById delegates to the model layer properly`() {
        val id = 0L

        runBlocking { filesystemListViewModel.deleteFilesystemById(id, this) }

        verify(mockFilesystemDao).deleteFilesystemById(id)
    }

    @Test
    fun `compressFilesystem delegates to the model layer properly`() {
        val filesystem = Filesystem(id = 0)
        val testFile = File("fake")
        val testListener: (String) -> Unit = {}

        runBlocking {
            filesystemListViewModel.compressFilesystem(filesystem, testFile, testListener, this)
        }
        verifyBlocking(mockFilesystemUtility) { compressFilesystem(filesystem, testFile, testListener) }
    }
}