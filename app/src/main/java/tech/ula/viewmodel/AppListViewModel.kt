package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.launch
import tech.ula.model.entities.App
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus
import tech.ula.utils.* // ktlint-disable no-wildcard-imports

class AppListViewModel(private val appsRepository: AppsRepository) : ViewModel() {

    private val refreshStatus = MutableLiveData<RefreshStatus>()

    fun getAllApps(): LiveData<List<App>> {
        launch { appsRepository.refreshData() }
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
