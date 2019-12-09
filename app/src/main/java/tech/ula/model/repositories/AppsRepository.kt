package tech.ula.model.repositories

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    fun upsertApp(application: App) {
        try {
            appsDao.insertApp(application)
        } catch (exception: SQLiteConstraintException) {
            val oldApplication = appsDao.getAppByName(application.name)
            application.serviceType = oldApplication.serviceType
            application.serviceLocation = oldApplication.serviceLocation
            appsDao.updateApp(application)
        }
    }

    suspend fun refreshData(scope: CoroutineScope) {
        val distributionsList = mutableSetOf<String>()
        val cloudDistributionsList = mutableSetOf<String>()
        refreshStatus.postValue(RefreshStatus.ACTIVE)
        val jobs = mutableListOf<Job>()
        try {
            remoteAppsSource.fetchAppsList().forEach { app ->
                jobs.add(scope.launch {
                    if (app.category.toLowerCase() == "distribution" && app.supportsLocal) distributionsList.add(app.name)
                    if (app.category.toLowerCase() == "distribution" && app.supportsRemote) cloudDistributionsList.add(app.name)
                    remoteAppsSource.fetchAppIcon(app)
                    remoteAppsSource.fetchAppDescription(app)
                    remoteAppsSource.fetchAppScript(app)
                    upsertApp(app)
            }) }
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
        appsPreferences.setCloudDistributionsList(cloudDistributionsList)
    }
}

enum class RefreshStatus {
    ACTIVE, FINISHED, FAILED, INACTIVE
}