package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.launch
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus
import tech.ula.model.state.AppsStartupEvent
import tech.ula.model.state.AppsStartupFsm
import tech.ula.model.state.AppsStartupState
import tech.ula.utils.zipLiveData

class AppListViewModel(
        private val appsStartupFsm: AppsStartupFsm,
        private val appsRepository: AppsRepository,
        private val sessionDao: SessionDao) : ViewModel() {

    // TODO move session tracking into the state machine so state accurately reflects when an app can be started
    private val activeSessions: LiveData<List<Session>> by lazy {
        sessionDao.findActiveSessions()
    }

    private val apps: LiveData<List<App>> by lazy {
        appsRepository.getAllApps()
    }

    private val appStartupState: LiveData<AppsStartupState> by lazy {
        appsStartupFsm.getState()
    }

    // TODO decouple these and track activity as state appropriately
    fun getAppsAndActiveSessions(): LiveData<Pair<List<App>, List<Session>>> {
        return zipLiveData(apps, activeSessions)
    }

    fun refreshAppsList() {
        launch { appsRepository.refreshData() }
    }

    fun getRefreshStatus(): LiveData<RefreshStatus> {
        return appsRepository.getRefreshStatus()
    }

    fun getAppsStartupState(): LiveData<AppsStartupState> {
        return appStartupState
    }

    fun submitAppsStartupEvent(event: AppsStartupEvent) {
        appsStartupFsm.submitEvent(event)
    }
}

class AppListViewModelFactory(private val appsStartupFsm: AppsStartupFsm, private val appsRepository: AppsRepository, private val sessionDao: SessionDao) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppListViewModel(appsStartupFsm, appsRepository, sessionDao) as T
    }
}
