package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem

class FilesystemListViewModel(application: Application) : AndroidViewModel(application) {
    private val ulaDatabase: UlaDatabase by lazy {
        UlaDatabase.getInstance(application)
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        ulaDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun deleteFilesystemById(id: Long) {
        GlobalScope.launch { ulaDatabase.filesystemDao().deleteFilesystemById(id) }
    }
}