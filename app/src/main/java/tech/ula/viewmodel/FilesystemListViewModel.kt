package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.entities.Filesystem
import tech.ula.utils.FilesystemUtility
import java.io.File
import kotlin.coroutines.CoroutineContext

class FilesystemListViewModel(private val filesystemDao: FilesystemDao, private val filesystemUtility: FilesystemUtility) : ViewModel(), CoroutineScope {

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

    fun compressFilesystem(
            filesystem: Filesystem,
            externalStorageDirectory: File,
            listener: (String) -> Any,
            coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {
        filesystemUtility.compressFilesystem(filesystem, externalStorageDirectory)
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao, private val filesystemUtility: FilesystemUtility) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao, filesystemUtility) as T
    }
}