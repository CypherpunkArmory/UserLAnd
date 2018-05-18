package tech.userland.userland.ui

import android.app.Application
import android.arch.lifecycle.*
import org.jetbrains.anko.Android
import tech.userland.userland.database.AppDatabase
import tech.userland.userland.database.models.Session
import tech.userland.userland.database.repositories.SessionDao

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
        appDatabase.sessionDao().insertSession(session)
    }
}