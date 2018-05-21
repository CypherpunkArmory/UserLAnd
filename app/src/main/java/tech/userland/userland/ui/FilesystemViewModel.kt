package tech.userland.userland.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import kotlinx.coroutines.experimental.launch
import tech.userland.userland.database.AppDatabase
import tech.userland.userland.database.models.Filesystem
import tech.userland.userland.utils.async

class FilesystemViewModel(application: Application) : AndroidViewModel(application) {
    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(application)
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        appDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun insertFilesystem(filesystem: Filesystem) {
        launch { async { appDatabase.filesystemDao().insertFilesystem(filesystem) } }
    }

    fun deleteFilesystemById(id: Long) {
        appDatabase.filesystemDao().deleteFilesystemById(id)
    }
}