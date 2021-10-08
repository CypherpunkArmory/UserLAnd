package tech.ula.viewmodel

import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.R
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.Session
import tech.ula.utils.AppDetails

@RunWith(MockitoJUnitRunner::class)
class AppDetailsViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    // Constructor parameters
    @Mock lateinit var mockSessionDao: SessionDao
    @Mock lateinit var mockAppDetails: AppDetails
    @Mock lateinit var mockSharedPreferences: SharedPreferences

    // Mocks returned from stubs
    @Mock lateinit var mockUri: Uri
    @Mock lateinit var mockViewStateObserver: Observer<AppDetailsViewState>

    private lateinit var viewModel: AppDetailsViewModel

    private val inactiveName = "inactive"
    private val inactiveDescription = "super fun text game"
    private val inactiveApp = App(name = inactiveName, supportsGui = true, supportsCli = true)

    private val activeName = "active"
    private val activeDescription = "super fun non-functioning browser"
    private val activeApp = App(name = activeName, supportsGui = true, supportsCli = true)

    private fun stubAppDetails(app: App) {
        whenever(mockAppDetails.findIconUri(app.name)).thenReturn(mockUri)
        val description = if (app.name == activeName) activeDescription else inactiveDescription
        whenever(mockAppDetails.findAppDescription(app.name)).thenReturn(description)
    }

    private fun buildSession(app: App, serviceType: ServiceType): Session {
        val active = app.name == activeName
        return Session(name = app.name, serviceType = serviceType, active = active, id = 0, filesystemId = 0)
    }

    @Test
    fun `ViewState is accurate for a setup, inactive app`() {
        stubAppDetails(inactiveApp)

        val session = buildSession(inactiveApp, ServiceType.Ssh)
        whenever(mockSessionDao.findAppsSession(inactiveName)).thenReturn(listOf(session))

        val buildVersion = Build.VERSION_CODES.M
        viewModel = AppDetailsViewModel(mockSessionDao, mockAppDetails, buildVersion, mockSharedPreferences)
        viewModel.viewState.observeForever(mockViewStateObserver)

        runBlocking {
            viewModel.submitEvent(AppDetailsEvent.SubmitApp(inactiveApp), this)
        }

        val expectedResult = AppDetailsViewState(
                mockUri,
                inactiveName,
                inactiveDescription,
                sshEnabled = true,
                vncEnabled = true,
                xsdlEnabled = true,
                describeStateHintEnabled = false,
                describeStateText = null,
                selectedServiceTypeButton = R.id.apps_ssh_preference,
                autoStartEnabled = false
        )
        verify(mockViewStateObserver).onChanged(expectedResult)
    }

    @Test
    fun `Buttons are disabled if app is active, and hint is for stopping app`() {
        stubAppDetails(activeApp)

        val session = buildSession(activeApp, ServiceType.Ssh)
        whenever(mockSessionDao.findAppsSession(activeName)).thenReturn(listOf(session))

        val buildVersion = Build.VERSION_CODES.M
        viewModel = AppDetailsViewModel(mockSessionDao, mockAppDetails, buildVersion, mockSharedPreferences)
        viewModel.viewState.observeForever(mockViewStateObserver)

        runBlocking {
            viewModel.submitEvent(AppDetailsEvent.SubmitApp(activeApp), this)
        }

        val expectedResult = AppDetailsViewState(
                mockUri,
                activeName,
                activeDescription,
                sshEnabled = false,
                vncEnabled = false,
                xsdlEnabled = false,
                describeStateHintEnabled = true,
                describeStateText = R.string.info_stop_app,
                selectedServiceTypeButton = R.id.apps_ssh_preference,
                autoStartEnabled = false
        )
        verify(mockViewStateObserver).onChanged(expectedResult)
    }

    @Test
    fun `Xsdl button is disabled if device is newer than O_MR1`() {
        stubAppDetails(inactiveApp)

        val session = buildSession(inactiveApp, ServiceType.Ssh)
        whenever(mockSessionDao.findAppsSession(inactiveName)).thenReturn(listOf(session))

        val buildVersion = Build.VERSION_CODES.P
        viewModel = AppDetailsViewModel(mockSessionDao, mockAppDetails, buildVersion, mockSharedPreferences)
        viewModel.viewState.observeForever(mockViewStateObserver)

        runBlocking {
            viewModel.submitEvent(AppDetailsEvent.SubmitApp(inactiveApp), this)
        }

        val expectedResult = AppDetailsViewState(
                mockUri,
                inactiveName,
                inactiveDescription,
                sshEnabled = true,
                vncEnabled = true,
                xsdlEnabled = false,
                describeStateHintEnabled = false,
                describeStateText = null,
                selectedServiceTypeButton = R.id.apps_ssh_preference,
                autoStartEnabled = false
        )
        verify(mockViewStateObserver).onChanged(expectedResult)
    }

    @Test
    fun `Buttons are disabled if setup is incomplete, and hint says as much`() {
        stubAppDetails(inactiveApp)

        val session = buildSession(inactiveApp, ServiceType.Unselected)
        whenever(mockSessionDao.findAppsSession(inactiveName)).thenReturn(listOf(session))

        val buildVersion = Build.VERSION_CODES.M
        viewModel = AppDetailsViewModel(mockSessionDao, mockAppDetails, buildVersion, mockSharedPreferences)
        viewModel.viewState.observeForever(mockViewStateObserver)

        runBlocking {
            viewModel.submitEvent(AppDetailsEvent.SubmitApp(inactiveApp), this)
        }

        val expectedResult = AppDetailsViewState(
                mockUri,
                inactiveName,
                inactiveDescription,
                sshEnabled = false,
                vncEnabled = false,
                xsdlEnabled = false,
                describeStateHintEnabled = true,
                describeStateText = R.string.info_finish_app_setup,
                selectedServiceTypeButton = null,
                autoStartEnabled = false
        )
        verify(mockViewStateObserver).onChanged(expectedResult)
    }

    @Test
    fun `Buttons are disabled if app session cannot be found, hint is for finishing setup`() {
        stubAppDetails(inactiveApp)

        whenever(mockSessionDao.findAppsSession(inactiveName)).thenReturn(listOf())

        val buildVersion = Build.VERSION_CODES.M
        viewModel = AppDetailsViewModel(mockSessionDao, mockAppDetails, buildVersion, mockSharedPreferences)
        viewModel.viewState.observeForever(mockViewStateObserver)

        runBlocking {
            viewModel.submitEvent(AppDetailsEvent.SubmitApp(inactiveApp), this)
        }

        val expectedResult = AppDetailsViewState(
                mockUri,
                inactiveName,
                inactiveDescription,
                sshEnabled = false,
                vncEnabled = false,
                xsdlEnabled = false,
                describeStateHintEnabled = true,
                describeStateText = R.string.info_finish_app_setup,
                selectedServiceTypeButton = null,
                autoStartEnabled = false
        )
        verify(mockViewStateObserver).onChanged(expectedResult)
    }
}