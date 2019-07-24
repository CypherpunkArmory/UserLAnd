package tech.ula.model.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.util.Log
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.App
import tech.ula.model.remote.GithubAppsFetcher
import tech.ula.utils.BreadcrumbType
import tech.ula.utils.Logger
import tech.ula.utils.SentryLogger
import tech.ula.utils.UlaBreadcrumb
import tech.ula.utils.preferences.AppsPreferences

class AppsRepository(
    private val appsDao: AppsDao,
    private val remoteAppsSource: GithubAppsFetcher,
    private val appsPreferences: AppsPreferences,
    private val logger: Logger = SentryLogger()
) {
    private val className = "AppsRepository"

    private val refreshStatus = MutableLiveData<RefreshStatus>()

    fun getAllApps(): LiveData<List<App>> {
        return appsDao.getAllApps()
    }

    fun getActiveApps(): LiveData<List<App>> {
        return appsDao.getActiveApps()
    }

    fun getRefreshStatus(): LiveData<RefreshStatus> {
        return refreshStatus
    }

    suspend fun refreshData(scope: CoroutineScope) {
        val distributionsList = mutableSetOf<String>()
        refreshStatus.postValue(RefreshStatus.ACTIVE)
        val jobs = mutableListOf<Job>()
        try {
            remoteAppsSource.fetchAppsList().forEach { app ->
                jobs.add(scope.launch {
                    if (app.category.toLowerCase() == "distribution") distributionsList.add(app.name)
                    remoteAppsSource.fetchAppIcon(app)
                    remoteAppsSource.fetchAppDescription(app)
                    remoteAppsSource.fetchAppScript(app)
                    appsDao.insertApp(app) // Insert the db element last to force observer refresh
            })}
        } catch (err: Exception) {
            refreshStatus.postValue(RefreshStatus.FAILED)
            val message = err.message ?: "Not found"
            val breadcrumb = UlaBreadcrumb(className, BreadcrumbType.RuntimeError, message)
            logger.addBreadcrumb(breadcrumb)
            logger.sendEvent("App Refresh Failed")
            return
        }
        jobs.joinAll()
        refreshStatus.postValue(RefreshStatus.FINISHED)
        appsPreferences.setDistributionsList(distributionsList)
    }
}

enum class RefreshStatus {
    ACTIVE, FINISHED, FAILED, INACTIVE
}