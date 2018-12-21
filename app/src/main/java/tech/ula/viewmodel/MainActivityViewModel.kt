package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.*

class MainActivityViewModel(private val appsStartupFsm: AppsStartupFsm, private val sessionStartupFsm: SessionStartupFsm) : ViewModel() {

    var appsAreWaitingForSelection = false
    var sessionsAreWaitingForSelection = false

    private val unselectedApp = App(name = "UNSELECTED")
    var lastSelectedApp = unselectedApp

    private val unselectedSession = Session(id = -1, name = "UNSELECTED", filesystemId = -1)
    var lastSelectedSession = unselectedSession

    private val unselectedFilesystem = Filesystem(id = -1, name = "UNSELECTED")
    var lastSelectedFilesystem = unselectedFilesystem

    private val appsState: LiveData<AppsStartupState> by lazy {
        appsStartupFsm.getState()
    }

    private val sessionState: LiveData<SessionStartupState> by lazy {
        sessionStartupFsm.getState()
    }

    fun selectionsCanBeMade(): Boolean {
        return appsAreWaitingForSelection && sessionsAreWaitingForSelection
    }

    // TODO this should probably check that session and filesystem selections are for app an app
    fun appsPreprationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedApp != unselectedApp && sessionPreparationRequirementsHaveBeenSelected()
    }

    fun sessionPreparationRequirementsHaveBeenSelected(): Boolean {
        return lastSelectedSession != unselectedSession && lastSelectedFilesystem != unselectedFilesystem
    }

    fun getAppsStartupState(): LiveData<AppsStartupState> {
        return appsState
    }

    fun submitAppsStartupEvent(event: AppsStartupEvent) {
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch { appsStartupFsm.submitEvent(event) }
    }

    fun getSessionStartupState(): LiveData<SessionStartupState> {
        return sessionState
    }

    fun submitSessionStartupEvent(event: SessionStartupEvent) {
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch { sessionStartupFsm.submitEvent(event) }
    }
}

class MainActivityViewModelFactory(private val appsStartupFsm: AppsStartupFsm, private val sessionStartupFsm: SessionStartupFsm) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainActivityViewModel(appsStartupFsm, sessionStartupFsm) as T
    }
}