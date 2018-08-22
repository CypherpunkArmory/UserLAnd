package tech.ula.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Session
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class ServerUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var execUtility: ExecUtility

    @Mock
    lateinit var process: Process

    lateinit var serverUtility: ServerUtility

    lateinit var sshPidFile: File

    lateinit var vncPidFile: File

    @Before
    fun setup() {
        serverUtility = ServerUtility(tempFolder.root.path, execUtility)
    }

    fun createSshPidFile() {
        val folder = tempFolder.newFolder("0", "run")
        sshPidFile = File("${folder.path}/dropbear.pid")
        sshPidFile.createNewFile()
    }

    fun createVNCPidFile(session: Session) {
        val folder = tempFolder.newFolder("0", "home", session.username, ".vnc")
        vncPidFile = File("${folder.path}/localhost:${session.port}.pid")
        vncPidFile.createNewFile()
    }

    @Test
    fun startSSHServer() {
        val session = Session(0, filesystemId = 0, serviceType = "ssh")
        val command = "../support/execInProot.sh /bin/bash -c /support/startSSHServer.sh"

        `when`(execUtility.wrapWithBusyboxAndExecute("0", command, doWait = false)).thenReturn(process)
        `when`(process.toString()).thenReturn("pid=100,")

        createSshPidFile()
        assertTrue(sshPidFile.exists())

        serverUtility.startServer(session)
        verify(execUtility).wrapWithBusyboxAndExecute("0", command, doWait = false)
        assertFalse(sshPidFile.exists())
    }

    @Test
    fun startVNCServer() {
        val session = Session(0, filesystemId = 0, serviceType = "vnc")
        val command = "../support/execInProot.sh /bin/bash -c /support/startVNCServer.sh"

        `when`(execUtility.wrapWithBusyboxAndExecute("0", command, doWait = false)).thenReturn(process)
        `when`(process.toString()).thenReturn("pid=100,")

        createVNCPidFile(session)
        assertTrue(vncPidFile.exists())

        serverUtility.startServer(session)
        verify(execUtility).wrapWithBusyboxAndExecute("0", command, doWait = false)
        assertFalse(vncPidFile.exists())
    }

    @Test
    fun stopService() {
        val session = Session(0, filesystemId = 0, serviceType = "ssh")
        serverUtility.stopService(session)
        val command = "../support/killProcTree.sh ${session.pid} -1"
        verify(execUtility).wrapWithBusyboxAndExecute("0", command)
    }

    @Test
    fun verifyServerRunning() {
        val session = Session(0, filesystemId = 0, serviceType = "ssh")
        val command = "../support/isServerInProcTree.sh -1"
        `when`(execUtility.wrapWithBusyboxAndExecute("0", command)).thenReturn(process)
        val isServerCurrentlyRunning = serverUtility.isServerRunning(session)

        verify(execUtility).wrapWithBusyboxAndExecute("0", command)
        assertTrue(isServerCurrentlyRunning)
    }
}
