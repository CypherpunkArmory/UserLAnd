package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.ula.model.entities.App
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus
import tech.ula.model.state.AppsStartupEvent
import tech.ula.model.state.AppsStartupFsm
import tech.ula.model.state.AppsStartupState

class AppListViewModel(
    private val appsRepository: AppsRepository
) : ViewModel() {

    private val apps: LiveData<List<App>> by lazy {
        appsRepository.getAllApps()
    }

    fun getAppsList(): LiveData<List<App>> {
        return apps
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
