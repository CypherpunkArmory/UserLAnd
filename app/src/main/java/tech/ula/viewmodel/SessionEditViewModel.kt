package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.experimental.launch
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.async
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class SessionEditViewModel(application: Application) : AndroidViewModel(application) {
    private val ulaDatabase: UlaDatabase by lazy {
        UlaDatabase.getInstance(application)
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        ulaDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    suspend fun insertSession(session: Session): Boolean {
        lateinit var result: Continuation<Boolean>
        launch {
            async {
                try {
                    ulaDatabase.sessionDao().insertSession(session)
                    result.resume(true)
                } catch (err: SQLiteConstraintException) {
                    result.resume(false)
                }
            }
        }
        return suspendCoroutine { continuation -> result = continuation }
    }

    fun updateSession(session: Session) {
        launch { async { ulaDatabase.sessionDao().updateSession(session) } }
    }
}