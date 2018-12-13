package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.launch
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.SessionStartupEvent
import tech.ula.model.state.SessionStartupFsm
import tech.ula.model.state.SessionStartupState
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class SessionListViewModel(
        private val ulaDatabase: UlaDatabase) : ViewModel() {

    private val sessions: LiveData<List<Session>> by lazy {
        ulaDatabase.sessionDao().getAllSessions()
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        ulaDatabase.filesystemDao().getAllFilesystems()
    }

    fun getSessionsAndFilesystems(): LiveData<Pair<List<Session>, List<Filesystem>>> {
        return zipLiveData(sessions, filesystems)
    }

    fun deleteSessionById(id: Long) {
        launch { async { ulaDatabase.sessionDao().deleteSessionById(id) } }
    }
}

class SessionListViewModelFactory(private val ulaDatabase: UlaDatabase): ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SessionListViewModel(ulaDatabase) as T
    }
}