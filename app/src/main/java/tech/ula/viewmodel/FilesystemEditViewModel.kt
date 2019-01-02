package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

class FilesystemEditViewModel(application: Application) : AndroidViewModel(application) {
    private val ulaDatabase: UlaDatabase by lazy {
        UlaDatabase.getInstance(application)
    }

    suspend fun insertFilesystem(filesystem: Filesystem): Boolean {
        lateinit var result: Continuation<Boolean>
        GlobalScope.launch {
            try {
                ulaDatabase.filesystemDao().insertFilesystem(filesystem)
                result.resume(true)
            } catch (err: SQLiteConstraintException) {
                result.resume(false)
            }
        }
        return suspendCoroutine { continuation -> result = continuation }
    }

    fun updateFilesystem(filesystem: Filesystem) {
        GlobalScope.launch {
            ulaDatabase.filesystemDao().updateFilesystem(filesystem)
            ulaDatabase.sessionDao().updateFilesystemNamesForAllSessions()
        }
    }
}