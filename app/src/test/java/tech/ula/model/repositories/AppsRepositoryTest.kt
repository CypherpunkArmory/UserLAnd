package tech.ula.model.repositories

import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.App
import tech.ula.model.remote.RemoteAppsSource

@RunWith(MockitoJUnitRunner::class)
class AppsRepositoryTest {

    @Mock
    lateinit var ulaDatabase: UlaDatabase

    @Mock
    lateinit var remoteAppsSource: RemoteAppsSource

    lateinit var appsRepository: AppsRepository

    @Before
    fun setup() {
        appsRepository = AppsRepository(ulaDatabase, remoteAppsSource)
    }

    @Test
    fun updatesStatusWhileFetchingRemoteApps() {
        whenever(runBlocking { remoteAppsSource.fetchAppsList() }).thenReturn(listOf())
        verify(appsRepository).setStatus(UpdateStatus.ACTIVE)
        verify(appsRepository).setStatus(UpdateStatus.INACTIVE)
    }
}