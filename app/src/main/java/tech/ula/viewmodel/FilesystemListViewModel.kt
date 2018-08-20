package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import tech.ula.model.repositories.AppDatabase
import tech.ula.model.entities.Filesystem

class FilesystemListViewModel(application: Application) : AndroidViewModel(application) {
    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(application)
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        appDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun deleteFilesystemById(id: Long) {
        launch { async { appDatabase.filesystemDao().deleteFilesystemById(id) } }
    }
}