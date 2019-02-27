package tech.ula.utils

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Session
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class ServerUtilityTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockBusyboxExecutor: BusyboxExecutor

    @Mock lateinit var mockProcess: Process

    lateinit var sshPidFile: File
    lateinit var vncPidFile: File

    private val filesystemId = 0L
    private val filesystemDirName = "0"
    private val fakePid = 100L

    lateinit var serverUtility: ServerUtility

    private fun createSshPidFile() {
        val folder = tempFolder.newFolder(filesystemDirName, "run")
        sshPidFile = File("${folder.path}/dropbear.pid")
        sshPidFile.createNewFile()
    }

    private fun createVNCPidFile(session: Session) {
        val folder = tempFolder.newFolder(filesystemDirName, "home", session.username, ".vnc")
        vncPidFile = File("${folder.path}/localhost:${session.port}.pid")
        vncPidFile.createNewFile()
    }

    private fun stubBusyboxExecutorCall(command: String) {
        whenever(mockBusyboxExecutor.executeProotCommand(
                eq(command),
                eq(filesystemDirName),
                eq(false),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()))
                .thenReturn(mockProcess)
    }

    @Before
    fun setup() {
        whenever(mockProcess.toString()).thenReturn("pid=$fakePid],")

        serverUtility = ServerUtility(tempFolder.root.path, mockBusyboxExecutor)
    }

    @Test
    fun `Calling startServer with an SSH session should generate the appropriate command`() {
        val session = Session(0, filesystemId = filesystemId, serviceType = "ssh")
        val command = "/support/startSSHServer.sh"
        stubBusyboxExecutorCall(command)

        createSshPidFile()
        assertTrue(sshPidFile.exists())

        val result = serverUtility.startServer(session)
        assertFalse(sshPidFile.exists())
        assertEquals(fakePid, result)
    }

//    @Test
//    fun startVNCServer() {
//        val session = Session(0, filesystemId = 0, serviceType = "vnc", username = "user", vncPassword = "userland")
//        val command = "../support/execInProot.sh /bin/bash -c /support/startVNCServer.sh"
//        val env = hashMapOf("INITIAL_USERNAME" to "user", "INITIAL_VNC_PASSWORD" to "userland")
//
////        `when`(busyboxExecutor.executeProotCommand("0", command, doWait = false, environmentVars = env)).thenReturn(process)
//
//        createVNCPidFile(session)
//        assertTrue(vncPidFile.exists())
//
//        serverUtility.startServer(session)
////        verify(execUtility).wrapWithBusyboxAndExecute("0", command, doWait = false, environmentVars = env)
//        assertFalse(vncPidFile.exists())
//    }
//
//    @Test
//    fun stopService() {
//        val session = Session(0, filesystemId = 0, serviceType = "ssh")
//        serverUtility.stopService(session)
//        val command = "../support/killProcTree.sh ${session.pid} -1"
////        verify(execUtility).wrapWithBusyboxAndExecute("0", command)
//    }
//
//    @Test
//    fun verifyServerRunning() {
//        val session = Session(0, filesystemId = 0, serviceType = "ssh")
//        val command = "../support/isServerInProcTree.sh -1"
////        `when`(execUtility.wrapWithBusyboxAndExecute("0", command)).thenReturn(process)
//        val isServerCurrentlyRunning = serverUtility.isServerRunning(session)
//
////        verify(execUtility).wrapWithBusyboxAndExecute("0", command)
//        assertTrue(isServerCurrentlyRunning)
//    }
}
