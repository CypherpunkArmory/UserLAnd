package tech.ula.viewmodel

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
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

    @Before
    fun setup() {
        appsStartupStateLiveData.postValue(WaitingForAppSelection)
        whenever(mockAppsStartupFsm.getState()).thenReturn(appsStartupStateLiveData)
        sessionStartupStateLiveData.postValue(WaitingForSessionSelection)
        whenever(mockSessionStartupFsm.getState()).thenReturn(sessionStartupStateLiveData)

        mainActivityViewModel = MainActivityViewModel(mockAppsStartupFsm, mockSessionStartupFsm)
        mainActivityViewModel.getState().observeForever(mockStateObserver)
    }
}