package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import kotlinx.coroutines.experimental.launch
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class SessionListViewModel(application: Application) : AndroidViewModel(application) {

    private val ulaDatabase: UlaDatabase by lazy {
        UlaDatabase.getInstance(application)
    }

    private val sessions: LiveData<List<Session>> by lazy {
        ulaDatabase.sessionDao().getAllSessions()
    }

    var activeSessions: Boolean = false

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        ulaDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllSessions(): LiveData<List<Session>> {
        return sessions
    }

    fun deleteSessionById(id: Long) {
        launch { async { ulaDatabase.sessionDao().deleteSessionById(id) } }
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }
}