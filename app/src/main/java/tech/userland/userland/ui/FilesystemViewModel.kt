package tech.userland.userland.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.os.AsyncTask
import tech.userland.userland.database.AppDatabase
import tech.userland.userland.database.models.Filesystem

class FilesystemViewModel(application: Application) : AndroidViewModel(application) {
    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(application)
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        appDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        //filesystems.value = appDatabase.filesystemDao().getAllFilesystems().value
        return filesystems
    }

    fun insertFilesystem(filesystem: Filesystem) {
        appDatabase.filesystemDao().insertFilesystem(filesystem)
    }

    class insertAsyncFilesystem(appDatabase: AppDatabase) : AsyncTask<Filesystem, Void, Void>() {
        private var appDatabase = appDatabase
        override fun doInBackground(vararg params: Filesystem): Void? {
            appDatabase.filesystemDao().insertFilesystem(params[0])
            return null
        }
    }
}