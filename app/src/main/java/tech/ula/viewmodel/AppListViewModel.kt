package tech.ula.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.experimental.launch
import tech.ula.model.entities.App
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.* // ktlint-disable no-wildcard-imports
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val ulaDatabase: UlaDatabase by lazy {
        UlaDatabase.getInstance(application)
    }

    private val internalApps: LiveData<List<App>> by lazy {
        ulaDatabase.appsDao().getAllApps()
    }

    private val apps: LiveData<List<App>> = internalApps

    fun getAllApps(): LiveData<List<App>> {
        return apps
    }

    // TODO: Implement
    fun getAppsByName(name: String): App {
        return App(id = 0)
    }

    suspend fun insertApplication(app: App): Boolean {
        lateinit var result: Continuation<Boolean>
        launch {
            async {
                try {
                    ulaDatabase.appsDao().insertApp(app)
                    result.resume(true)
                } catch (err: SQLiteConstraintException) {
                    result.resume(false)
                }
            }
        }
        return suspendCoroutine { continuation -> result = continuation }
    }
}
