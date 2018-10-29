package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.launch
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AppsRepository
import tech.ula.model.repositories.RefreshStatus
import tech.ula.utils.zipLiveData

class AppListViewModel(private val appsRepository: AppsRepository, private val sessionDao: SessionDao, private val filesystemDao: FilesystemDao) : ViewModel() {

    private val activeSessions: LiveData<List<Session>> by lazy {
        sessionDao.findActiveSessions()
    }

    private val apps: LiveData<List<App>> by lazy {
        appsRepository.getAllApps()
    }

    fun getAppsAndActiveSessions(): LiveData<Pair<List<App>, List<Session>>> {
        return zipLiveData(apps, activeSessions)
    }

//    fun getAppServiceTypePreference(app: App): String { TODO
//        return appsRepository.getAppServiceTypePreference(app)
//    }
//
//    fun setAppServiceTypePreference(app: App, preferredClient: String) {
//        appsRepository.setAppServiceTypePreference(app, preferredClient)
//    }

    fun refreshAppsList() {
        launch { appsRepository.refreshData() }
    }

    fun getRefreshStatus(): LiveData<RefreshStatus> {
        return appsRepository.getRefreshStatus()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystemDao.getAllFilesystems()
    }
}

class AppListViewModelFactory(private val appsRepository: AppsRepository, private val sessionDao: SessionDao, private val filesystemDao: FilesystemDao) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppListViewModel(appsRepository, sessionDao, filesystemDao) as T
    }
}
