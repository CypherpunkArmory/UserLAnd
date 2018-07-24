package tech.ula.utils

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import kotlin.text.Charsets.UTF_8

class FileUtilityTest {
    lateinit var fileUtility: FileUtility

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var context: Context

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        val target = tempFolder.root
        `when`(context.filesDir).thenReturn(target)
        fileUtility = FileUtility(context)
    }

    @Test
    fun movesDownloadedAssets() {
        val ulaAsset1 = "test1.sh"
        val ulaAsset2 = "test2.rootfs.tar.gz"
        val nonUlaAsset1 = "HelloWorld.txt"
        val nonUlaAsset2 = "HelloWorld.png"

        val source = tempFolder.newFolder("source")
        tempFolder.newFolder("support")

        val toMove1 = tempFolder.newFile("source/UserLAnd:support:$ulaAsset1")
        val toMove2 = tempFolder.newFile("source/UserLAnd:support:$ulaAsset2")
        tempFolder.newFile("source/$nonUlaAsset1")
        tempFolder.newFile("source/$nonUlaAsset2")

        fileUtility.moveAssetsToCorrectSharedDirectory(source)

        assertTrue(File("${tempFolder.root}/support/$ulaAsset1").exists())
        assertTrue(File("${tempFolder.root}/support/$ulaAsset2").exists())
        assertFalse(File("${tempFolder.root}/support/$nonUlaAsset1").exists())
        assertFalse(File("${tempFolder.root}/support/$nonUlaAsset2").exists())

        assertFalse(toMove1.exists())
        assertFalse(toMove2.exists())
    }

    @Test
    fun copiesDistributionAssetsToCorrectFilesystems() {
        val distType = "dist1"
        tempFolder.newFolder(distType)
        val distAsset1 = tempFolder.newFile("dist1/dist1file1")
        val distAsset2 = tempFolder.newFile("dist1/dist1file2")

        tempFolder.newFolder("dist2")
        tempFolder.newFile("dist2/dist2file1")
        tempFolder.newFile("dist2/dist2file2")

        val targetFilesystemName = "filesystem"

        fileUtility.copyDistributionAssetsToFilesystem(targetFilesystemName, distType)

        assertTrue(File("${tempFolder.root}/$targetFilesystemName/support/dist1file1").exists())
        assertTrue(File("${tempFolder.root}/$targetFilesystemName/support/dist1file2").exists())

        assertTrue(distAsset1.exists())
        assertTrue(distAsset2.exists())

        assertFalse(File("${tempFolder.root}/$targetFilesystemName/support/dist2file1").exists())
        assertFalse(File("${tempFolder.root}/$targetFilesystemName/support/dist2file1").exists())

        // TODO test that permissions are changed
        var output = ""
        val proc = Runtime.getRuntime().exec("stat -f %A ${tempFolder.root}/$targetFilesystemName/support/dist1file1")

        proc.inputStream.bufferedReader(UTF_8).forEachLine { output += it }
        assert(output == "777")
    }

    @Test
    fun correctsFilePermissions() {
        val distributionType = "dist"
        val assets = listOf(
                "proot" to "support",
                "killProcTree.sh" to "support",
                "isServerInProcTree.sh" to "support",
                "busybox" to "support",
                "libtalloc.so.2" to "support",
                "execInProot.sh" to "support",
                "startSSHServer.sh" to distributionType,
                "startVNCServer.sh" to distributionType,
                "startVNCServerStep2.sh" to distributionType,
                "busybox" to distributionType,
                "libdisableselinux.so" to distributionType
        )

        tempFolder.newFolder("support")
        tempFolder.newFolder(distributionType)
        assets.forEach {
            (asset, directory) ->
            tempFolder.newFile("$directory/$asset")
        }

        fileUtility.correctFilePermissions(distributionType)

        assets.forEach {
            (asset, directory) ->
            val proc = Runtime.getRuntime().exec("stat -f %A ${tempFolder.root}/$directory/$asset")
            var output = ""
            proc.inputStream.bufferedReader(UTF_8).forEachLine { output += it }
            assert(output == "777")
        }
    }
}