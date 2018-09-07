package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.launch
import tech.ula.model.entities.App
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus

class AppListViewModel(private val appsRepository: AppsRepository) : ViewModel() {

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

class AppListViewModelFactory(private val appsRepository: AppsRepository) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppListViewModel(appsRepository) as T
    }
}
