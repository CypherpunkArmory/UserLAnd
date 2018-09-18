package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Transformations
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.launch
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus

class AppListViewModel(private val appsRepository: AppsRepository, private val sessionDao: SessionDao) : ViewModel() {

    private val activeSessions: LiveData<List<Session>> by lazy {
        sessionDao.findActiveSessions()
    }

    fun getAllActiveSessions(): LiveData<List<Session>> {
        return activeSessions
    }

    fun getAllApps(): LiveData<List<App>> {
        return appsRepository.getAllApps()
    }

    fun getAppsByName(name: String): App {
        return appsRepository.getAppByName(name)
    }

    fun refreshAppsList() {
        launch { appsRepository.refreshData() }
    }

    fun getRefreshStatus(): LiveData<RefreshStatus> {
        return appsRepository.getRefreshStatus()
    }
}

class AppListViewModelFactory(private val appsRepository: AppsRepository, private val sessionDao: SessionDao) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppListViewModel(appsRepository, sessionDao) as T
    }
}
