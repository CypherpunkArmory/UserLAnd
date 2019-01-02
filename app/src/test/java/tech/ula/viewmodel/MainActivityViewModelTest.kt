package tech.ula.viewmodel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.model.state.*

@RunWith(MockitoJUnitRunner::class)
class MainActivityViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock lateinit var appsStartupStateLiveData: MutableLiveData<AppsStartupState>

    @Mock lateinit var sessionStartupStateLiveData: MutableLiveData<SessionStartupState>

    @Mock lateinit var mockAppsStartupFsm: AppsStartupFsm

    @Mock lateinit var mockSessionStartupFsm: SessionStartupFsm

    @Mock lateinit var  mockStateObserver: Observer<State>

    lateinit var mainActivityViewModel: MainActivityViewModel

    val selectedApp = App(name = "app")
    val selectedSession = Session(id = 0, filesystemId = 0)

    @Before
    fun setup() {
        appsStartupStateLiveData.postValue(WaitingForAppSelection)
        whenever(mockAppsStartupFsm.getState()).thenReturn(appsStartupStateLiveData)
        sessionStartupStateLiveData.postValue(WaitingForSessionSelection)
        whenever(mockSessionStartupFsm.getState()).thenReturn(sessionStartupStateLiveData)

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

    }

    @Test
    fun `Posts IllegalState if neither a session nor an app has been selected when permissions are granted`() {

    }
}