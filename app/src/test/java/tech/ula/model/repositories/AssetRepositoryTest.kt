package tech.ula.model.repositories

import com.nhaarman.mockitokotlin2.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.runBlocking
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import tech.ula.model.remote.GithubApiClient
import tech.ula.utils.Logger
import tech.ula.utils.HttpStream
import tech.ula.utils.preferences.AssetPreferences
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException
import java.net.UnknownHostException

@RunWith(MockitoJUnitRunner::class)
class AssetRepositoryTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var applicationFilesDirPath: String

    @Mock lateinit var mockAssetPreferences: AssetPreferences

    @Mock lateinit var mockGithubApiClient: GithubApiClient

    @Mock lateinit var mockHttpStream: HttpStream

    @Mock lateinit var mockLogger: Logger

    private lateinit var assetRepository: AssetRepository

    private val assetsTarName = "assets.tar.gz"
    private val rootFsTarName = "rootfs.tar.gz"
    private val assetName = "asset"
    private val repo = "dist"
    private val asset = Asset("asset", repo)

    private val lowVersion = "v0.0.0"
    private val highVersion = "v1.0.0"

    private val filesystem = Filesystem(id = 0, distributionType = repo)

    private fun stubAssetsVersion(repo: String, cachedVersion: String, remoteVersion: String) {
        whenever(mockAssetPreferences.getLatestDownloadVersion(repo))
                .thenReturn(cachedVersion)

        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenReturn(remoteVersion)
        }
    }

    private fun stubRootFsVersion(repo: String, cachedVersion: String, remoteVersion: String) {
        whenever(mockAssetPreferences.getLatestDownloadFilesystemVersion(repo))
                .thenReturn(cachedVersion)

        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenReturn(remoteVersion)
        }
    }

    private fun stubApiVersionAndUrl(repo: String, versionCode: String, url: String) {
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenReturn(versionCode)
            whenever(mockGithubApiClient.getAssetEndpoint(assetsTarName, repo))
                    .thenReturn(url)
        }
    }

    private fun stubAssetDoesNotNeedDownloading(asset: Asset, repo: String) {
        val repoDir = File("$applicationFilesDirPath/$repo")
        val assetFile = File("${repoDir.absolutePath}/${asset.name}")
        repoDir.mkdirs()
        assetFile.createNewFile()

        stubAssetsVersion(repo, highVersion, highVersion)
    }

    private fun stubRootFsApiVersionAndUrl(repo: String, version: String, url: String) {
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenReturn(version)
            whenever(mockGithubApiClient.getAssetEndpoint(rootFsTarName, repo))
                    .thenReturn(url)
        }
    }

    @Before
    fun setup() {
        applicationFilesDirPath = tempFolder.root.absolutePath

        assetRepository = AssetRepository(applicationFilesDirPath, mockAssetPreferences, mockGithubApiClient, mockHttpStream, mockLogger)
    }

    @Test(expected = IllegalStateException::class)
    fun `generateDownloadRequirements logs and throws and exception if sent an empty asset list`() {
        val assetList = listOf<Asset>()

        runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, true)
        }

        verify(mockLogger).addExceptionBreadcrumb(IOException())
    }

    @Test
    fun `generateDownloadRequirements does not include assets already present if they are up to date`() {
        val supportAssetDirectory = File("$applicationFilesDirPath/$repo")
        val supportAssetFile = File("${supportAssetDirectory.absolutePath}/${asset.name}")
        supportAssetDirectory.mkdirs()
        supportAssetFile.createNewFile()

        val cachedVersion = lowVersion
        val remoteVersion = lowVersion
        stubAssetsVersion(repo, cachedVersion, remoteVersion)

        val assetList = listOf(asset)
        val filesystemNeedsExtraction = false

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDownloadRequirements does not include assets already present if the network is unreachable`() {
        val supportAssetDirectory = File("$applicationFilesDirPath/$repo")
        val supportAssetFile = File("${supportAssetDirectory.absolutePath}/${asset.name}")
        supportAssetDirectory.mkdirs()
        supportAssetFile.createNewFile()

        whenever(mockAssetPreferences.getLatestDownloadVersion(repo))
                .thenReturn(lowVersion)
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenThrow(UnknownHostException())
        }

        val assetList = listOf(asset)
        val filesystemNeedsExtraction = false
        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        assertTrue(result.isEmpty())
    }

    @Test(expected = UnknownHostException::class)
    fun `generateDownloadRequirements throws UnknownHostException if assets are not present and the network is unreachable`() {
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenThrow(UnknownHostException())
        }

        val assetList = listOf(asset)
        val filesystemNeedsExtraction = false

        runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }
    }

    @Test
    fun `generateDownloadRequirements includes assets that are present if version is out of date`() {
        val supportAssetDirectory = File("$applicationFilesDirPath/$repo")
        val supportAssetFile = File("${supportAssetDirectory.absolutePath}/$assetName")
        supportAssetDirectory.mkdirs()
        supportAssetFile.createNewFile()

        val cachedVersion = lowVersion
        val remoteVersion = highVersion
        stubAssetsVersion(repo, cachedVersion, remoteVersion)

        val assetList = listOf(asset)
        val filesystemNeedsExtraction = false

        val url = "url"
        stubApiVersionAndUrl(repo, remoteVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        val expectedDownloadMetadata = DownloadMetadata(assetsTarName, repo, remoteVersion, url)
        assertEquals(listOf(expectedDownloadMetadata), result)
    }

    @Test
    fun `generateDownloadRequirements includes assets that are not present even if last version is up to date`() {
        val cachedVersion = highVersion
        val remoteVersion = highVersion
        stubAssetsVersion(repo, cachedVersion, remoteVersion)

        val assetList = listOf(asset)
        val filesystemNeedsExtraction = false

        val url = "url"
        stubApiVersionAndUrl(repo, remoteVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        val expectedDownloadMetadata = DownloadMetadata(assetsTarName, repo, remoteVersion, url)
        assertEquals(listOf(expectedDownloadMetadata), result)
    }

    @Test
    fun `generateDownloadRequirements does not include rootfs if filesystem does not need extraction but would otherwise be included`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val rootfsFile = File("$distDirectory/$rootFsTarName")
        distDirectory.mkdirs()
        assertFalse(rootfsFile.exists())

        val cachedVersion = lowVersion
        val remoteVersion = highVersion
        stubRootFsVersion(repo, cachedVersion, remoteVersion)

        val assetList = listOf(asset)
        stubAssetDoesNotNeedDownloading(asset, repo)
        val filesystemNeedsExtraction = false

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDownloadRequirements does not include rootfs if filesystem needs extraction but a local copy is up to date`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val rootfsFile = File("$distDirectory/$rootFsTarName")
        distDirectory.mkdirs()
        rootfsFile.createNewFile()

        val cachedVersion = highVersion
        val remoteVersion = highVersion
        stubRootFsVersion(repo, cachedVersion, remoteVersion)

        val assetList = listOf(asset)
        stubAssetDoesNotNeedDownloading(asset, repo)
        val filesystemNeedsExtraction = true

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDownloadRequirements does not include rootfs if filesystem needs extraction, there is a local version available, and the network is unreachable`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val assetFile = File("$distDirectory/${asset.name}")
        val rootfsFile = File("$distDirectory/$rootFsTarName")
        distDirectory.mkdirs()
        rootfsFile.createNewFile()
        assetFile.createNewFile()

        whenever(mockAssetPreferences.getLatestDownloadVersion(repo))
                .thenReturn(lowVersion)
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenThrow(UnknownHostException())
                    .thenThrow(UnknownHostException())
        }

        val assetList = listOf(asset)
        val filesystemNeedsExtraction = true

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        assertTrue(result.isEmpty())
    }

    @Test(expected = UnknownHostException::class)
    fun `generateDownloadRequirements throws UnknownHostException if rootfs files need to be downloaded and the network is unreachable`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val assetFile = File("$distDirectory/${asset.name}")
        distDirectory.mkdirs()
        assetFile.createNewFile()

        whenever(mockAssetPreferences.getLatestDownloadVersion(repo))
                .thenReturn(lowVersion)
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenThrow(UnknownHostException())
                    .thenThrow(UnknownHostException())
        }

        val assetList = listOf(asset)
        val filesystemNeedsExtraction = true

        runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }
    }

    @Test
    fun `generateDownloadRequirements does include rootfs if filesystem needs extraction and a local copy does not exist, but the last cached version is up to date`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val rootfsFile = File("$distDirectory/$rootFsTarName")
        distDirectory.mkdirs()
        assertFalse(rootfsFile.exists())

        val cachedVersion = highVersion
        val remoteVersion = highVersion
        stubRootFsVersion(repo, cachedVersion, remoteVersion)

        val assetList = listOf(asset)
        stubAssetDoesNotNeedDownloading(asset, repo)
        val filesystemNeedsExtraction = true

        val url = "url"
        stubRootFsApiVersionAndUrl(repo, highVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        val expectedDownloads = listOf(DownloadMetadata(rootFsTarName, repo, highVersion, url))
        assertFalse(result.isEmpty())
        assertEquals(expectedDownloads, result)
    }

    @Test
    fun `generateDownloads does include rootfs if filesystem needs extraction even if a local copy exists, if the version is out of date`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val rootfsFile = File("$distDirectory/$rootFsTarName")
        distDirectory.mkdirs()
        rootfsFile.createNewFile()

        val cachedVersion = lowVersion
        val remoteVersion = highVersion
        stubRootFsVersion(repo, cachedVersion, remoteVersion)

        val assetList = listOf(asset)
        stubAssetDoesNotNeedDownloading(asset, repo)
        val filesystemNeedsExtraction = true

        val url = "url"
        stubRootFsApiVersionAndUrl(repo, highVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetList, filesystemNeedsExtraction)
        }

        val expectedDownloads = listOf(DownloadMetadata(rootFsTarName, repo, highVersion, url))
        assertFalse(result.isEmpty())
        assertEquals(expectedDownloads, result)
    }

    @Test
    fun `getDistributionAssetsForExistingFilesystem propagates to AssetPreferences, and removes rootfs from list`() {
        val rootFsAsset = Asset("rootfs.tar.gz", asset.type)
        val assetList = listOf(asset, rootFsAsset)
        whenever(mockAssetPreferences.getCachedAssetList(filesystem.distributionType))
                .thenReturn(assetList)

        val result = assetRepository.getDistributionAssetsForExistingFilesystem(filesystem)

        val expectedResult = assetList.minus(rootFsAsset)
        verify(mockAssetPreferences).getCachedAssetList(filesystem.distributionType)
        assertEquals(expectedResult, result)
    }

    @Test
    fun `getLatestDistributionVersion propagates to AssetPreferences`() {
        val version = "v0.0.0"
        whenever(mockAssetPreferences.getLatestDownloadVersion(filesystem.distributionType))
                .thenReturn(version)

        val result = assetRepository.getLatestDistributionVersion(filesystem.distributionType)

        verify(mockAssetPreferences).getLatestDownloadVersion(filesystem.distributionType)
        assertEquals(version, result)
    }

    @Test
    fun `assetsArePresentInSupportDirectories correctly reports existence, skipping rootfs files`() {
        val assetList = listOf(asset, Asset("rootfs.tar.gz", asset.type))

        val distDir = File("$applicationFilesDirPath/$repo")
        val distAssetFile = File("${distDir.absolutePath}/${asset.name}")

        distDir.mkdirs()
        val result1 = assetRepository.assetsArePresentInSupportDirectories(assetList)

        distAssetFile.createNewFile()
        val result2 = assetRepository.assetsArePresentInSupportDirectories(assetList)

        assertFalse(result1)
        assertTrue(result2)
    }

    @Test
    fun `getAssetList will fetch and cache distribution assets lists`() {
        val distRepo = filesystem.distributionType

        val distUrl = "distUrl"
        runBlocking {
            whenever(mockGithubApiClient.getAssetsListDownloadUrl(distRepo))
                    .thenReturn(distUrl)
        }

        val remoteDistAssetFile = File("$applicationFilesDirPath/remoteDist")
        remoteDistAssetFile.createNewFile()
        remoteDistAssetFile.bufferedWriter().apply {
            write("assets.txt garbage")
            newLine()
            write("${asset.name} garbage")
            flush()
            close()
        }

        val distInputStream = remoteDistAssetFile.inputStream()

        whenever(mockHttpStream.fromUrl(distUrl))
                .thenReturn(distInputStream)

        val result = runBlocking {
            assetRepository.getAssetList(distRepo)
        }

        val expectedDistList = listOf(asset)
        assertEquals(expectedDistList, result)

        verifyBlocking(mockGithubApiClient) { getAssetsListDownloadUrl(distRepo) }
        verify(mockHttpStream).fromUrl(distUrl)
        verify(mockAssetPreferences).setAssetList(distRepo, expectedDistList)
    }

    @Test
    fun `getAllAssetLists will used cached lists if remote fetch fails`() {
        val distRepo = filesystem.distributionType
        val distAssetList = listOf(asset)

        runBlocking {
            whenever(mockGithubApiClient.getAssetsListDownloadUrl(any()))
                    .thenThrow(IOException::class.java)
        }
        whenever(mockAssetPreferences.getCachedAssetList(distRepo))
                .thenReturn(distAssetList)

        val result = runBlocking {
            assetRepository.getAssetList(distRepo)
        }

        assertEquals(distAssetList, result)
        verify(mockAssetPreferences).getCachedAssetList(distRepo)
    }
}