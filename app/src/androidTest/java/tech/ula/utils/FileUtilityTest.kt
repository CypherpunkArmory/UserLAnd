package tech.ula.utils

import android.content.Context
import android.support.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File

class FileUtilityTest {
    lateinit var fileUtility: FileUtility

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Mock
    lateinit var context: Context

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        fileUtility = FileUtility(context)
    }

    @Test
    fun movesDownloadedAssets() {
        val ulaAsset1 = "test1.sh"
        val ulaAsset2 = "test2.rootfs.tar.gz"
        val nonUlaAsset1 = "HelloWorld.txt"
        val nonUlaAsset2 = "HelloWorld.png"

        val source = tempFolder.newFolder("source")
        tempFolder.newFolder("target", "support")

        val toMove1 = tempFolder.newFile("source/UserLAnd:support:$ulaAsset1")
        val toMove2 = tempFolder.newFile("source/UserLAnd:support:$ulaAsset2")
        tempFolder.newFile("source/$nonUlaAsset1")
        tempFolder.newFile("source/$nonUlaAsset2")

        fileUtility.moveAssetsToCorrectSharedDirectory(source.absolutePath, "${tempFolder.root}/target")

        // Appropriate files have been moved
        assertTrue(File("${tempFolder.root}/target/support/$ulaAsset1").exists())
        assertTrue(File("${tempFolder.root}/target/support/$ulaAsset2").exists())
        assertFalse(File("${tempFolder.root}/target/$nonUlaAsset1").exists())
        assertFalse(File("${tempFolder.root}/target/$nonUlaAsset2").exists())

        // Appropriate files have been deleted
        assertFalse(toMove1.exists())
        assertFalse(toMove2.exists())
    }
}