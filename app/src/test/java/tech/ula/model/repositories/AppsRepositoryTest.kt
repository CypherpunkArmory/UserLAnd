package tech.ula.model.repositories

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyBlocking
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.App
import tech.ula.model.remote.GithubAppsFetcher
import tech.ula.utils.Logger
import tech.ula.utils.preferences.AppsPreferences
import java.io.IOException

@RunWith(MockitoJUnitRunner::class)
class AppsRepositoryTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var mockGithubAppsFetcher: GithubAppsFetcher

    @Mock lateinit var mockAppsDao: AppsDao

    @Mock lateinit var mockAppsPreferences: AppsPreferences

    @Mock lateinit var mockLogger: Logger

    @Mock lateinit var mockAppsObserver: Observer<List<App>>

    @Mock lateinit var mockRefreshStatusObserver: Observer<RefreshStatus>

    private val inactiveAppName = "inactive"
    private val inactiveApp = App(name = inactiveAppName, category = "distribution")
    private val activeAppName = "active"
    private val activeApp = App(name = activeAppName)
    private val appsList = listOf(inactiveApp)
    private val activeAppsList = listOf(activeApp)
    private val appsListLiveData = MutableLiveData<List<App>>()
    private val activeAppsListLiveData = MutableLiveData<List<App>>()

    private lateinit var appsRepository: AppsRepository

    @Before
    fun setup() {
        appsListLiveData.postValue(appsList)
        activeAppsListLiveData.postValue(activeAppsList)
        appsRepository = AppsRepository(
                mockAppsDao,
                mockGithubAppsFetcher, 
                mockAppsPreferences,
                mockLogger
        )
    }

    @Test
    fun `Fetches apps from database`() {
        whenever(mockAppsDao.getAllApps()).thenReturn(appsListLiveData)

        appsRepository.getAllApps().observeForever(mockAppsObserver)

        verify(mockAppsDao).getAllApps()
        verify(mockAppsObserver).onChanged(appsList)
    }

    @Test
    fun `Fetches active apps from database`() {
        whenever(mockAppsDao.getActiveApps()).thenReturn(activeAppsListLiveData)
        
        appsRepository.getActiveApps().observeForever(mockAppsObserver)
        
        verify(mockAppsDao).getActiveApps()
        verify(mockAppsObserver).onChanged(activeAppsList)
    }

    @Test
    fun `Apps are inserted into the database and distributions are saved in the cache`() {
        runBlocking {
            whenever(mockGithubAppsFetcher.fetchAppsList()).thenReturn(appsList)
        }

        appsRepository.getRefreshStatus().observeForever(mockRefreshStatusObserver)

        runBlocking {
            appsRepository.refreshData(this)
        }

        verifyBlocking(mockGithubAppsFetcher) { fetchAppIcon(inactiveApp) }
        verifyBlocking(mockGithubAppsFetcher) { fetchAppDescription(inactiveApp) }
        verifyBlocking(mockGithubAppsFetcher) { fetchAppScript(inactiveApp) }
        verify(mockAppsDao).insertApp(inactiveApp)
        verify(mockAppsPreferences).setDistributionsList(setOf(inactiveAppName))
        verify(mockRefreshStatusObserver).onChanged(RefreshStatus.ACTIVE)
        verify(mockRefreshStatusObserver).onChanged(RefreshStatus.FINISHED)
    }

    @Test
    fun `Failure during refresh posts RefreshStatus FAILED`() {
        runBlocking {
            whenever(mockGithubAppsFetcher.fetchAppsList()).thenThrow(IOException())
        }
        appsRepository.getRefreshStatus().observeForever(mockRefreshStatusObserver)

        runBlocking {
            appsRepository.refreshData(this)
        }

        verify(mockRefreshStatusObserver).onChanged(RefreshStatus.ACTIVE)
        verify(mockRefreshStatusObserver).onChanged(RefreshStatus.FAILED)
    }
}