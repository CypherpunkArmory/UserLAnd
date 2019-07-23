package tech.ula.model.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.util.Log
import kotlinx.coroutines.*
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.App
import tech.ula.model.remote.GithubAppsFetcher
import tech.ula.utils.preferences.AppsPreferences

class AppsRepository(
    private val appsDao: AppsDao,
    private val remoteAppsSource: GithubAppsFetcher,
    private val appsPreferences: AppsPreferences
) {
    private val refreshStatus = MutableLiveData<RefreshStatus>()

    fun getAllApps(): LiveData<List<App>> {
        return appsDao.getAllApps()
    }

    fun getActiveApps(): LiveData<List<App>> {
        return appsDao.getActiveApps()
    }

    suspend fun refreshData() = withContext(Dispatchers.IO) {
        val appsList = mutableSetOf<String>()
        val distributionsList = mutableSetOf<String>()
        refreshStatus.postValue(RefreshStatus.ACTIVE)
        val jobs = mutableListOf<Job>()
        try {
            remoteAppsSource.fetchAppsList().forEach {
                app ->
                jobs.add(launch {
                    appsList.add(app.name)
                    if (app.category.toLowerCase() == "distribution") distributionsList.add(app.name)
                    remoteAppsSource.fetchAppIcon(app)
                    remoteAppsSource.fetchAppDescription(app)
                    remoteAppsSource.fetchAppScript(app)
                    appsDao.insertApp(app) // Insert the db element last to force observer refresh

            })}
        } catch (err: Exception) {
            refreshStatus.postValue(RefreshStatus.FAILED)
            Log.e("refresh", err.message ?: "")
            return@withContext
        }
        jobs.joinAll()
        refreshStatus.postValue(RefreshStatus.FINISHED)
        appsPreferences.setDistributionsList(distributionsList)
}

    fun getRefreshStatus(): LiveData<RefreshStatus> {
        return refreshStatus
    }
}

enum class RefreshStatus {
    ACTIVE, FINISHED, FAILED, INACTIVE
}