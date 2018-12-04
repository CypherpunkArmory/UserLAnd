package tech.ula.model.repositories

import android.arch.lifecycle.LiveData
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.App
import tech.ula.model.remote.RemoteAppsSource
import tech.ula.utils.AppsPreferences

@RunWith(MockitoJUnitRunner::class)
class AppsRepositoryTest {

    @Mock
    lateinit var remoteAppsSource: RemoteAppsSource

    @Mock
    lateinit var appsList: LiveData<List<App>>

    @Mock
    lateinit var appsDao: AppsDao

    @Mock
    lateinit var appsPreferences: AppsPreferences

    lateinit var appsRepository: AppsRepository

    @Before
    fun setup() {
        appsRepository = AppsRepository(appsDao, remoteAppsSource, appsPreferences)
    }

    @Test
    fun retrievesAppsFromDatabase() {
        whenever(appsDao.getAllApps()).thenReturn(appsList)

        appsRepository.getAllApps()

        verify(appsDao).getAllApps()
    }

    // TODO these tests can be written now that livedata has been figured out. see AppsStartupFsmTest.kt
    @Test
    fun updatesStatusWhileFetchingRemoteApps() {
    }
}