package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.experimental.launch
import tech.ula.model.entities.App
import tech.ula.model.remote.GithubAppsFetcher
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class AppListViewModel(private val appsRepository: AppsRepository) : ViewModel() {
    fun getAllApps(): LiveData<List<App>> {
        appsRepository.refreshData()
        return appsRepository.getAllApps()
    }

    fun getAppsByName(name: String): App {
        return appsRepository.getAppByName(name)
    }
}

class AppListViewModelFactory(private val appsRepository: AppsRepository) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppListViewModel(appsRepository) as T
    }
}
