package tech.ula.model.state

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.daos.AppsDao
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.UlaDatabase
import tech.ula.utils.AppsPreferences
import tech.ula.utils.BuildWrapper

@RunWith(MockitoJUnitRunner::class)
class AppsStartupFsmTest {

    @get:Rule val instantExecutorRule = InstantTaskExecutorRule()

    // Mocks

    @Mock lateinit var mockFilesystemDao: FilesystemDao
    lateinit var filesystemListLiveData: MutableLiveData<List<Filesystem>>

    @Mock lateinit var mockSessionDao: SessionDao
    lateinit var activeSessionLiveData: MutableLiveData<List<Session>>

    @Mock lateinit var mockAppsDao: AppsDao
    lateinit var appsListLiveData: MutableLiveData<List<App>>

    @Mock lateinit var mockUlaDatabase: UlaDatabase

    @Mock lateinit var mockAppsPreferences: AppsPreferences

    @Mock lateinit var mockBuildWrapper: BuildWrapper

    @Mock lateinit var mockStateObserver: Observer<AppsStartupState>

    lateinit var appsFSM: AppsStartupFsm

    // Test setup variables
    val app1Name = "testApp1"
    val app2Name = "testApp2"

    val app1 = App(name = app1Name)
    val app2 = App(name = app2Name)

    val nonAppActiveSession = Session(id = -1, name = "notAnApp", filesystemId = -1, active = true)
    val app1ActiveSession = Session(id = -1, name = app1Name, filesystemId =  -1, active = true)
    val app2ActiveSession = Session(id = -1, name = app2Name, filesystemId = -1, active = true)

    @Before
    fun setup() {
        activeSessionLiveData = MutableLiveData()
        appsListLiveData = MutableLiveData()

        whenever(mockUlaDatabase.sessionDao()).thenReturn(mockSessionDao)
        whenever(mockSessionDao.findActiveSessions()).thenReturn(activeSessionLiveData)

        whenever(mockUlaDatabase.appsDao()).thenReturn(mockAppsDao)
        whenever(mockAppsDao.getAllApps()).thenReturn(appsListLiveData)

        appsFSM = AppsStartupFsm(mockUlaDatabase, mockAppsPreferences, mockBuildWrapper)  // TODO Showerthought: default initialization of arch in db entry and then erroring to BuildWrapper?
    }

    @After
    fun teardown() {
        activeSessionLiveData = MutableLiveData()
        appsListLiveData = MutableLiveData()
    }

    fun stubEmptyActiveSessions() {
        activeSessionLiveData.postValue(listOf())
    }

    fun stubEmptyAppsList() {
        appsListLiveData.postValue(listOf())
    }

    fun stubFullAppsList() {
        appsListLiveData.postValue(listOf(app1, app2))
    }

    @Test
    fun initialStateIsWaitingForApps() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubEmptyActiveSessions()
        stubFullAppsList()


        val expectedState = WaitingForAppSelection
        verify(mockStateObserver).onChanged(expectedState)
    }

    @Test
    fun initialStateIsAppsListIsEmptyWhenDatabaseIsNotPopulated() {
        appsFSM.getState().observeForever(mockStateObserver)
        stubEmptyActiveSessions()
        stubEmptyAppsList()


        val expectedState = AppsListIsEmpty
        verify(mockStateObserver).onChanged(expectedState)
    }

    @Test
    fun stateIsSingleSessionPermittedWhenNonAppSessionIsRunning() {
        appsFSM.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf(nonAppActiveSession))
        stubFullAppsList()

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(SingleSessionPermitted)
    }

    @Test
    fun stateIsSingleSessionPermittedWhenSelectedAppIsNotActiveOne() {
        appsFSM.getState().observeForever(mockStateObserver)
        activeSessionLiveData.postValue(listOf(app2ActiveSession))
        stubFullAppsList()

        appsFSM.submitEvent(AppSelected(app1))

        verify(mockStateObserver).onChanged(SingleSessionPermitted)
    }
}