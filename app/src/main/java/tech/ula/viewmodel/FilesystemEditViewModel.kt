package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.experimental.launch
import tech.ula.model.AppDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.utils.async
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class FilesystemEditViewModel(application: Application) : AndroidViewModel(application) {
    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(application)
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
        launch { async {
            appDatabase.filesystemDao().updateFilesystem(filesystem)
            appDatabase.sessionDao().updateFilesystemNamesForAllSessions()
        } }
    }
}