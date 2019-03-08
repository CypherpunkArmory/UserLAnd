package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import kotlin.coroutines.CoroutineContext

class SessionEditViewModel(private val ulaDatabase: UlaDatabase) : ViewModel(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        ulaDatabase.filesystemDao().getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun insertSession(session: Session, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            ulaDatabase.sessionDao().insertSession(session)
        }
    }

    fun updateSession(session: Session, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        ulaDatabase.sessionDao().updateSession(session)
    }
}

class SessionEditViewmodelFactory(private val ulaDatabase: UlaDatabase) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return SessionEditViewModel(ulaDatabase) as T
    }
}