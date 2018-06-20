package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.*
import kotlinx.coroutines.experimental.launch
import tech.ula.model.AppDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.async

class SessionListViewModel(application: Application) : AndroidViewModel(application) {
    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(application)
    }

    private val sessions: LiveData<List<Session>> by lazy {
        appDatabase.sessionDao().getAllSessions()
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        appDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllSessions(): LiveData<List<Session>> {
        return sessions
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun deleteSessionById(id: Long) {
        launch { async { appDatabase.sessionDao().deleteSessionById(id) } }
    }

    fun updateSession(session: Session) {
        launch { async { appDatabase.sessionDao().updateSession(session) } }
    }
}