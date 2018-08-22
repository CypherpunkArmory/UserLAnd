package tech.ula.utils

import android.content.res.Resources
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.AssetRepository

@RunWith(MockitoJUnitRunner::class)
class SessionControllerTest {

    // Class dependencies

    val testFilesystem = Filesystem(name = "testFS", id = 1)

    @Mock
    lateinit var resourcesFetcher: ResourcesFetcher

    @Mock
    lateinit var resources: Resources

    @Mock
    lateinit var assetRepository: AssetRepository

    @Mock
    lateinit var filesystemUtility: FilesystemUtility

    val progressBarUpdater: (String, String) -> Unit = {
        step: String, details: String ->
        System.out.println("progress bar updater called with step: $step and details: $details")
    }

    val dialogBroadcaster: (String) -> Unit = {
        type ->
        System.out.println("dialog broadcast called with type: $type")
    }

    // Function dependencies

    @Mock
    lateinit var networkUtility: NetworkUtility

    @Mock
    lateinit var downloadBroadcastReceiver: DownloadBroadcastReceiver

    @Mock
    lateinit var downloadUtility: DownloadUtility

    val filesystemExtractLogger: (String) -> Unit = {
        System.out.println("filesystem extract logger called with line: $it")
    }

    val session = Session(name = "testSession", id = 1, filesystemName = "testFS", filesystemId = 1)

    lateinit var sessionController: SessionController

    @Before
    fun setup() {
        sessionController = SessionController(testFilesystem, resourcesFetcher, assetRepository,
                filesystemUtility, progressBarUpdater, dialogBroadcaster)
    }
}