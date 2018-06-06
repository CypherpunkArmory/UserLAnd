package tech.ula.ui

import android.app.Application
import android.arch.lifecycle.*
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.experimental.launch
import tech.ula.database.AppDatabase
import tech.ula.database.models.Session
import tech.ula.utils.async
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(application)
    }

    private val sessions: LiveData<List<Session>> by lazy {
        appDatabase.sessionDao().getAllSessions()
    }

    fun getAllSessions(): LiveData<List<Session>> {
        return sessions
    }

    suspend fun insertSession(session: Session): Boolean {
        lateinit var result: Continuation<Boolean>
        launch {
            async {
                try {
                    appDatabase.sessionDao().insertSession(session)
                    result.resume(true)
                } catch (err: SQLiteConstraintException) {
                    result.resume(false)
                }
            }
        }
        return suspendCoroutine { continuation -> result = continuation }
    }

    fun deleteSessionById(id: Long) {
        launch { async { appDatabase.sessionDao().deleteSessionById(id) } }
    }

    fun updateSession(session: Session) {
        launch { async { appDatabase.sessionDao().updateSession(session) } }
    }
}