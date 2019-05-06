package tech.ula.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.ula.model.entities.App
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus

class AppListViewModel(
    private val appsRepository: AppsRepository
) : ViewModel() {

    private val apps: LiveData<List<App>> by lazy {
        appsRepository.getAllApps()
    }

    private val activeAppsLiveData: LiveData<List<App>> by lazy {
        appsRepository.getActiveApps()
    }

    fun getAppsList(): LiveData<List<App>> {
        return apps
    }

    fun getActiveApps(): LiveData<List<App>> {
        return activeAppsLiveData
    }

    fun refreshAppsList() {
        CoroutineScope(Dispatchers.Default).launch { appsRepository.refreshData() }
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
