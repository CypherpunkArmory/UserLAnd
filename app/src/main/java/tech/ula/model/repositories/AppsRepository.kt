package tech.ula.model.repositories

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.App
import tech.ula.model.remote.RemoteAppsSource
import tech.ula.utils.AppsPreferences
import tech.ula.utils.ConnectionUtility

import tech.ula.utils.asyncAwait

class AppsRepository(
    private val appsDao: AppsDao,
    private val remoteAppsSource: RemoteAppsSource,
    private val appsPreferences: AppsPreferences
) {
    private val refreshStatus = MutableLiveData<RefreshStatus>()

    fun getAllApps(): LiveData<List<App>> {
        return appsDao.getAllApps()
    }

//    fun getAppServiceTypePreference(app: App): String { TODO
//        return appsPreferences.getAppServiceTypePreference(app.name)
//    }

//    fun setAppServiceTypePreference(app: App, preferredServiceType: String) {
//        appsPreferences.setAppServiceTypePreference(app.name, preferredServiceType)
//    }

    suspend fun refreshData() {
        val appsList = mutableSetOf<String>()
        val distributionsList = mutableSetOf<String>()
        if (!ConnectionUtility().httpsHostIsReachable("github.com")) {
            refreshStatus.postValue(RefreshStatus.FAILED)
            return
        }
        asyncAwait {
            refreshStatus.postValue(RefreshStatus.ACTIVE)
            try {
                remoteAppsSource.fetchAppsList().forEach {
                    app ->
                    appsList.add(app.name)
                    if (app.category.toLowerCase() == "distribution") distributionsList.add(app.name)
                    remoteAppsSource.fetchAppIcon(app)
                    remoteAppsSource.fetchAppDescription(app)
                    remoteAppsSource.fetchAppScript(app)
                    appsDao.insertApp(app) // Insert the db element last to force observer refresh
                }
            } catch (err: Exception) {
                refreshStatus.postValue(RefreshStatus.FAILED)
                Log.e("refresh", err.message)
                return@asyncAwait
            }
            refreshStatus.postValue(RefreshStatus.FINISHED)
        }
        appsPreferences.setAppsList(appsList)
        appsPreferences.setDistributionsList(distributionsList)
    }

    fun getRefreshStatus(): LiveData<RefreshStatus> {
        return refreshStatus
    }
}

enum class RefreshStatus {
    ACTIVE, FINISHED, FAILED, INACTIVE
}