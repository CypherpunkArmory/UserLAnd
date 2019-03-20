package tech.ula.model.repositories

import com.nhaarman.mockitokotlin2.* // ktlint-disable no-wildcard-imports
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.remote.GithubApiClient
import tech.ula.utils.AssetPreferences
import tech.ula.utils.ConnectionUtility
import java.io.File

@RunWith(MockitoJUnitRunner::class)
class AssetRepositoryTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val applicationFilesDirPath = tempFolder.root.absolutePath

    @Mock lateinit var mockAssetPreferences: AssetPreferences

    @Mock lateinit var mockGithubApiClient: GithubApiClient

    @Mock lateinit var mockConnectionUtility: ConnectionUtility

    private lateinit var assetRepository: AssetRepository

    @Before
    fun setup() {
        assetRepository = AssetRepository(applicationFilesDirPath, mockAssetPreferences, mockGithubApiClient, mockConnectionUtility)
    }
}