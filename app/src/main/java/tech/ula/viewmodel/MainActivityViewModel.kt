package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.SessionStartupEvent
import tech.ula.model.state.SessionStartupFsm
import tech.ula.model.state.SessionStartupState

class MainActivityViewModel(private val sessionStartupFsm: SessionStartupFsm) : ViewModel() {

    var appsAreWaitingForSelection = true // TODO default to false once apps fsm is wired up
    var sessionsAreWaitingForSelection = false

    private val sessionState: LiveData<SessionStartupState> by lazy {
        sessionStartupFsm.getState()
    }

    private val unselectedApp = App(name = "UNSELECTED")
    var lastSelectedApp = unselectedApp

    private val unselectedSession = Session(id = -1, name = "UNSELECTED", filesystemId = -1)
    var lastSelectedSession = unselectedSession

    private val unselectedFilesystem = Filesystem(id = -1, name = "UNSELECTED")
    var lastSelectedFilesystem = unselectedFilesystem

    fun selectionsCanBeMade(): Boolean {
        return appsAreWaitingForSelection && sessionsAreWaitingForSelection
    }

    fun sessionPreparationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedSession != unselectedSession && lastSelectedFilesystem != unselectedFilesystem
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