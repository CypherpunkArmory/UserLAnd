package tech.userland.userland.ui

import android.app.Application
import android.arch.lifecycle.*
import tech.userland.userland.database.AppDatabase
import tech.userland.userland.database.models.Session

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

    fun getSessionByName(name: String): Session {
        return appDatabase.sessionDao().getSessionByName(name)
    }

    fun insertSession(session: Session) {
        appDatabase.sessionDao().insertSession(session)
    }

    fun deleteSessionById(id: Long) {
        appDatabase.sessionDao().deleteSessionById(id)
    }

    fun updateSession(session: Session) {
        appDatabase.sessionDao().updateSession(session)
    }
}