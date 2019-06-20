package tech.ula.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase

@RunWith(MockitoJUnitRunner::class)
class SessionEditViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var mockUlaDatabase: UlaDatabase

    @Mock lateinit var mockFilesystemDao: FilesystemDao

    @Mock lateinit var mockSessionDao: SessionDao

    @Mock lateinit var mockObserver: Observer<List<Filesystem>>

    private lateinit var filesystemsLiveData: MutableLiveData<List<Filesystem>>

    private lateinit var sessionEditViewModel: SessionEditViewModel

    @Before
    fun setup() {
        filesystemsLiveData = MutableLiveData()
        whenever(mockUlaDatabase.filesystemDao()).thenReturn(mockFilesystemDao)
        whenever(mockFilesystemDao.getAllFilesystems()).thenReturn(filesystemsLiveData)
        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)

        sessionEditViewModel = SessionEditViewModel(mockUlaDatabase)
    }

    @Test
    fun `Filesystems can be observed through getAllFilesystems`() {
        val filesystemsList = listOf(Filesystem(0))
        filesystemsLiveData.postValue(filesystemsList)

        sessionEditViewModel.getAllFilesystems().observeForever(mockObserver)

        verify(mockObserver).onChanged(filesystemsList)
    }

    @Test
    fun `Inserting a session propagates to the model layer`() {
        val session = Session(0, filesystemId = 0)

        runBlocking { sessionEditViewModel.insertSession(session, this) }

        verify(mockSessionDao).insertSession(session)
    }

    @Test
    fun `Updating a session propagates to the model layer`() {
        val session = Session(0, filesystemId = 0)

        runBlocking { sessionEditViewModel.updateSession(session, this) }

        verify(mockSessionDao).updateSession(session)
    }
}