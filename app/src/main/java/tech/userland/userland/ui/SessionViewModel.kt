package tech.userland.userland.ui

import android.app.Application
import android.arch.lifecycle.*
import kotlinx.coroutines.experimental.launch
import tech.userland.userland.database.AppDatabase
import tech.userland.userland.database.models.Session
import tech.userland.userland.utils.async

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

    fun insertSession(session: Session) {
        launch { async { appDatabase.sessionDao().insertSession(session) } }
    }

    fun deleteSessionById(id: Long) {
        launch { async { appDatabase.sessionDao().deleteSessionById(id) } }
    }

    fun updateSession(session: Session) {
        launch { async { appDatabase.sessionDao().updateSession(session) } }
    }
}