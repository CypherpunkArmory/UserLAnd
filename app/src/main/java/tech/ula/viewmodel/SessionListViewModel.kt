package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Transformations
import android.os.Environment
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.defaultSharedPreferences
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class SessionListViewModel(application: Application) : AndroidViewModel(application) {

    private val execUtility: ExecUtility by lazy {
        val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        ExecUtility(application.filesDir.path, externalStoragePath, DefaultPreferences(application.defaultSharedPreferences))
    }

    private val serverUtility: ServerUtility by lazy {
        ServerUtility(application.filesDir.path, execUtility)
    }

    private val ulaDatabase: UlaDatabase by lazy {
        UlaDatabase.getInstance(application)
    }

    private val internalSessions: LiveData<List<Session>> by lazy {
        ulaDatabase.sessionDao().getAllSessions()
    }

    var activeSessions: Boolean = false

    private val sessions: LiveData<List<Session>> =
            Transformations.map(internalSessions) { sessions ->
        for (session in sessions) {
            if (session.active) session.active = serverUtility.isServerRunning(session)
        }
        activeSessions = sessions.any { it.active }
        sessions
    }

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