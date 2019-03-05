package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.entities.Filesystem
import kotlin.coroutines.CoroutineContext

class FilesystemListViewModel(private val filesystemDao: FilesystemDao) : ViewModel(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        filesystemDao.getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun deleteFilesystemById(id: Long, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            filesystemDao.deleteFilesystemById(id)
        }
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao) as T
    }
}