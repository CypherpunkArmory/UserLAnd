package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import tech.ula.model.AppDatabase
import tech.ula.model.entities.Filesystem
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

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

    suspend fun insertFilesystem(filesystem: Filesystem): Boolean {
        lateinit var result: Continuation<Boolean>
        launch { async {
            try {
                appDatabase.filesystemDao().insertFilesystem(filesystem)
                result.resume(true)
            }
            catch(err: SQLiteConstraintException) {
                result.resume(false)
            }
        } }
        return suspendCoroutine { continuation -> result = continuation }
    }

    fun updateFilesystem(filesystem: Filesystem) {
        launch { tech.ula.utils.async { appDatabase.filesystemDao().updateFilesystem(filesystem) } }
    }

    fun deleteFilesystemById(id: Long) {
        launch { async { appDatabase.filesystemDao().deleteFilesystemById(id) } }
    }
}