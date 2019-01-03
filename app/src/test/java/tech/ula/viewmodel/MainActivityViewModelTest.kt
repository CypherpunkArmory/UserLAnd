package tech.ula.viewmodel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.state.*

@RunWith(MockitoJUnitRunner::class)
class MainActivityViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var mockAppsStartupFsm: AppsStartupFsm

    @Mock lateinit var mockSessionStartupFsm: SessionStartupFsm

    @Mock lateinit var mockAppStateObserver: Observer<AppsStartupState>

    @Mock lateinit var  mockStateObserver: Observer<State>

    lateinit var appsStartupStateLiveData: MutableLiveData<AppsStartupState>

    lateinit var sessionStartupStateLiveData: MutableLiveData<SessionStartupState>

    lateinit var mainActivityViewModel: MainActivityViewModel

    val selectedApp = App(name = "app")
    val selectedSession = Session(id = 0, filesystemId = 0)

    @Before
    fun setup() {
        appsStartupStateLiveData = MutableLiveData()
        whenever(mockAppsStartupFsm.getState()).thenReturn(appsStartupStateLiveData)
        appsStartupStateLiveData.postValue(WaitingForAppSelection)

        sessionStartupStateLiveData = MutableLiveData()
        whenever(mockSessionStartupFsm.getState()).thenReturn(sessionStartupStateLiveData)
        sessionStartupStateLiveData.postValue(WaitingForSessionSelection)

        mainActivityViewModel = MainActivityViewModel(mockAppsStartupFsm, mockSessionStartupFsm)
        mainActivityViewModel.getState().observeForever(mockStateObserver)
    }

    @Test
    fun `State does not initially publish onChanged event`() {
        verify(mockStateObserver, never()).onChanged(any())
    }

    @Test
    fun `Restarts app setup after permissions are granted if an app was selected first`() {
        mainActivityViewModel.waitForPermissions(appToContinue = selectedApp)
        mainActivityViewModel.permissionsHaveBeenGranted()
        runBlocking {
            verify(mockAppsStartupFsm).submitEvent(AppSelected(selectedApp))
        }
    }

    @Test
    fun `Restarts session setup after permissions are granted if a session was selected first`() {
        mainActivityViewModel.waitForPermissions(sessionToContinue = selectedSession)
        mainActivityViewModel.permissionsHaveBeenGranted()
        runBlocking {
            verify(mockSessionStartupFsm).submitEvent(SessionSelected(selectedSession))
        }
    }

    @Test
    fun `Posts IllegalState if both a session and app have been selected when permissions are granted`() {
        mainActivityViewModel.waitForPermissions(selectedApp, selectedSession)
        mainActivityViewModel.permissionsHaveBeenGranted()

        verify(mockStateObserver).onChanged(IllegalState("Both a session and an app have been selected when permissions are granted"))
    }

    @Test
    fun `Posts IllegalState if neither a session nor an app has been selected when permissions are granted`() {
        mainActivityViewModel.waitForPermissions()
        mainActivityViewModel.permissionsHaveBeenGranted()

        verify(mockStateObserver).onChanged(IllegalState("Neither a session nor app have been selected when permissions are granted."))
    }

    @Test
    fun `Apps and sessions cannot be selected if apps state is not WaitingForAppSelection`() {
        appsStartupStateLiveData.postValue(FetchingDatabaseEntries)

        mainActivityViewModel.submitAppSelection(selectedApp)
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockAppsStartupFsm, never()).submitEvent(AppSelected(selectedApp))
            verify(mockSessionStartupFsm, never()).submitEvent(SessionSelected(selectedSession))
        }
    }

    @Test
    fun `Apps and sessions cannot be selected if session state is not WaitingForSessionSelection`() {
        mainActivityViewModel.getState().observeForever(mockStateObserver)
        sessionStartupStateLiveData.postValue(SessionIsReadyForPreparation(selectedSession, Filesystem(id = 0)))

        mainActivityViewModel.submitAppSelection(selectedApp)
        mainActivityViewModel.submitSessionSelection(selectedSession)

        runBlocking {
            verify(mockAppsStartupFsm, never()).submitEvent(AppSelected(selectedApp))
            verify(mockSessionStartupFsm, never()).submitEvent(SessionSelected(selectedSession))
        }
    }
}