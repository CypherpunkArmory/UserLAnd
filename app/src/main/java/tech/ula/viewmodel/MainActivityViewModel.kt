package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import tech.ula.model.state.SessionStartupEvent
import tech.ula.model.state.SessionStartupFsm
import tech.ula.model.state.SessionStartupState

class MainActivityViewModel(private val sessionStartupFsm: SessionStartupFsm) : ViewModel() {
    private val sessionState: LiveData<SessionStartupState> by lazy {
        sessionStartupFsm.getState()
    }

    fun getSessionStartupState(): LiveData<SessionStartupState> {
        return sessionState
    }

    fun submitSessionStartupEvent(event: SessionStartupEvent) {
        sessionStartupFsm.submitEvent(event)
    }
}

class MainActivityViewModelFactory(private val sessionStartupFsm: SessionStartupFsm) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainActivityViewModel(sessionStartupFsm) as T
    }
}