package tech.ula.model.repositories

import android.arch.lifecycle.LiveData
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import tech.ula.model.entities.App
import tech.ula.model.remote.RemoteAppsSource
import tech.ula.utils.asyncAwait

class AppsRepository(private val ulaDatabase: UlaDatabase, private val remoteAppsSource: RemoteAppsSource) {

    private var currentStatus = UpdateStatus.INACTIVE

    fun getAllApps(): LiveData<List<App>> {
        return ulaDatabase.appsDao().getAllApps()
    }

    fun getAppByName(name: String): App {
        return ulaDatabase.appsDao().getAppByName(name)
    }

    fun refreshData() {
        setStatus(UpdateStatus.ACTIVE)
        launch(CommonPool) {
            asyncAwait {
                remoteAppsSource.fetchAppsList().forEach {
                    ulaDatabase.appsDao().insertApp(it)
                }
            }
            setStatus(UpdateStatus.INACTIVE)
        }
    }

    fun getStatus(): UpdateStatus {
        return currentStatus
    }

    fun setStatus(status: UpdateStatus) {
        currentStatus = status
    }
}

enum class UpdateStatus {
    ACTIVE, INACTIVE
}