package tech.ula.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.ula.model.entities.App
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus
import kotlin.coroutines.CoroutineContext

class AppsListViewModel(
    private val appsRepository: AppsRepository
) : ViewModel(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

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
        this.launch { appsRepository.refreshData(this) }
    }

    fun getRefreshStatus(): LiveData<RefreshStatus> {
        return appsRepository.getRefreshStatus()
    }
}

class AppsListViewModelFactory(private val appsRepository: AppsRepository) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppsListViewModel(appsRepository) as T
    }
}
