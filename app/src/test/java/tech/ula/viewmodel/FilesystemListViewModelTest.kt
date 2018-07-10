package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import tech.ula.model.AppDatabase
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.entities.Filesystem

class FilesystemListViewModelTest {

    lateinit var filesystemListViewModel: FilesystemListViewModel

    @Mock
    lateinit var context: Application

    @Mock
    lateinit var appDatabase: AppDatabase

    @Mock
    lateinit var filesystemDao: FilesystemDao

    @Mock
    lateinit var filesystems: LiveData<List<Filesystem>>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        setupMocks()
        filesystemListViewModel = FilesystemListViewModel(context)
    }

    fun setupMocks() {
        `when`<Context>(context.applicationContext).thenReturn(context)
        `when`(appDatabase.filesystemDao().getAllFilesystems())
                .thenReturn(filesystemDao.getAllFilesystems())
    }

    @Test
    fun getAllFilesystems() {
        filesystemListViewModel.getAllFilesystems()
        verify(appDatabase).filesystemDao().getAllFilesystems()
    }

    @Test
    fun deleteFilesystem() {
        filesystemListViewModel.deleteFilesystemById(0)
        verify(appDatabase).filesystemDao().deleteFilesystemById(0)
    }
}