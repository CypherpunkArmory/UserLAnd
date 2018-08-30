package tech.ula.viewmodel

import android.app.Application as App
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.experimental.launch
import tech.ula.model.repositories.AppDatabase
import tech.ula.model.entities.Application
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class AppListViewModel(application: App) : AndroidViewModel(application) {

    private val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(application)
    }

    private val internalApps: LiveData<List<Application>> by lazy {
        appDatabase.applicationDao().getAllApplications()
    }

    private val apps: LiveData<List<Application>> = internalApps

    fun getAllApps(): LiveData<List<Application>> {
        return apps
    }

    // TODO: Implement
    fun getAppsByName(name: String): Application {
        return Application(id = 0)
    }

    suspend fun insertApplication(application: Application): Boolean {
        lateinit var result: Continuation<Boolean>
        launch {
            async {
                try {
                    appDatabase.applicationDao().insertApplication(application = application)
                    result.resume(true)
                } catch (err: SQLiteConstraintException) {
                    result.resume(false)
                }
            }
        }
        return suspendCoroutine { continuation -> result = continuation }
    }
}
