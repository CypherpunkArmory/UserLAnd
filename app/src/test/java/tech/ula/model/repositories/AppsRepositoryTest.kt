package tech.ula.model.repositories

import android.arch.lifecycle.LiveData
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.App
import tech.ula.model.remote.RemoteAppsSource

@RunWith(MockitoJUnitRunner::class)
class AppsRepositoryTest {

    @Mock
    lateinit var remoteAppsSource: RemoteAppsSource

    @Mock
    lateinit var appsList: LiveData<List<App>>

    @Mock
    lateinit var appsDao: AppsDao

    lateinit var appsRepository: AppsRepository

    @Before
    fun setup() {
        appsRepository = AppsRepository(appsDao, remoteAppsSource)
    }

    @Test
    fun retrievesAppsFromDatabase() {
        whenever(appsDao.getAllApps()).thenReturn(appsList)

        appsRepository.getAllApps()

        verify(appsDao).getAllApps()
    }

    @Test
    fun updatesStatusWhileFetchingRemoteApps() {
//        whenever(runBlocking { remoteAppsSource.fetchAppsList() }).thenReturn(listOf())

        runBlocking {
            launch {
                whenever(remoteAppsSource.fetchAppsList()).thenReturn(listOf())
                appsRepository.refreshData()
            }.join()
        }
    }
}