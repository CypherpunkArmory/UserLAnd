package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.LiveData
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import tech.ula.model.entities.Filesystem

class FilesystemListViewModelTest {

    lateinit var filesystemListViewModel: FilesystemListViewModel

    @Mock
    lateinit var context: Application

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`<Context>(context.applicationContext).thenReturn(context)
        filesystemListViewModel = FilesystemListViewModel(context)
    }

    @Test
    fun test() {
        assert(filesystemListViewModel.getAllFilesystems() is LiveData<List<Filesystem>>)
    }

}