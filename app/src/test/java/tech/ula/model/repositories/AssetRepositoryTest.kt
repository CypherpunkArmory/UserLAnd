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
import tech.ula.utils.AssetPreferences
import tech.ula.utils.ConnectionUtility
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

    @Mock lateinit var mockConnectionUtility: ConnectionUtility

    @Mock lateinit var mockLogger: Logger

    private lateinit var assetRepository: AssetRepository

    private val assetsTarName = "assets.tar.gz"
    private val rootFsTarName = "rootfs.tar.gz"
    private val assetName = "asset"
    private val supportRepo = "support"
    private val distRepo = "dist"
    private val supportAsset = Asset("supportAsset", supportRepo)
    private val distAsset = Asset("distAsset", distRepo)

    private val lowVersion = "v0.0.0"
    private val highVersion = "v1.0.0"

    private val filesystem = Filesystem(id = 0, distributionType = distRepo)

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

        assetRepository = AssetRepository(applicationFilesDirPath, mockAssetPreferences, mockGithubApiClient, mockConnectionUtility, mockLogger)
    }

    @Test(expected = IllegalStateException::class)
    fun `generateDownloadRequirements logs and throws and exception if sent an empty asset list`() {
        val assetLists = hashMapOf(supportRepo to listOf<Asset>())

        runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, true)
        }

        verify(mockLogger).addExceptionBreadcrumb(IOException())
    }

    @Test
    fun `generateDownloadRequirements does not include assets already present if they are up to date`() {
        val supportAssetDirectory = File("$applicationFilesDirPath/$supportRepo")
        val supportAssetFile = File("${supportAssetDirectory.absolutePath}/${supportAsset.name}")
        supportAssetDirectory.mkdirs()
        supportAssetFile.createNewFile()

        val cachedVersion = lowVersion
        val remoteVersion = lowVersion
        stubAssetsVersion(supportRepo, cachedVersion, remoteVersion)

        val assetLists = hashMapOf(supportRepo to listOf(supportAsset))
        val filesystemNeedsExtraction = false

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDownloadRequirements does not include assets already present if the network is unreachable`() {
        val supportAssetDirectory = File("$applicationFilesDirPath/$supportRepo")
        val supportAssetFile = File("${supportAssetDirectory.absolutePath}/${supportAsset.name}")
        supportAssetDirectory.mkdirs()
        supportAssetFile.createNewFile()

        whenever(mockAssetPreferences.getLatestDownloadVersion(supportRepo))
                .thenReturn(lowVersion)
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(supportRepo))
                    .thenThrow(UnknownHostException())
        }

        val assetLists = hashMapOf(supportRepo to listOf(supportAsset))
        val filesystemNeedsExtraction = false
        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }

        assertTrue(result.isEmpty())
    }

    @Test(expected = UnknownHostException::class)
    fun `generateDownloadRequirements throws UnknownHostException if assets are not present and the network is unreachable`() {
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(supportRepo))
                    .thenThrow(UnknownHostException())
        }

        val assetLists = hashMapOf(supportRepo to listOf(supportAsset))
        val filesystemNeedsExtraction = false

        runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }
    }

    @Test
    fun `generateDownloadRequirements includes assets that are present if version is out of date`() {
        val supportAssetDirectory = File("$applicationFilesDirPath/$supportRepo")
        val supportAssetFile = File("${supportAssetDirectory.absolutePath}/$assetName")
        supportAssetDirectory.mkdirs()
        supportAssetFile.createNewFile()

        val cachedVersion = lowVersion
        val remoteVersion = highVersion
        stubAssetsVersion(supportRepo, cachedVersion, remoteVersion)

        val assetLists = hashMapOf(supportRepo to listOf(supportAsset))
        val filesystemNeedsExtraction = false

        val url = "url"
        stubApiVersionAndUrl(supportRepo, remoteVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }

        val expectedDownloadMetadata = DownloadMetadata(assetsTarName, supportRepo, remoteVersion, url)
        assertEquals(listOf(expectedDownloadMetadata), result)
    }

    @Test
    fun `generateDownloadRequirements includes assets that are not present even if last version is up to date`() {
        val cachedVersion = highVersion
        val remoteVersion = highVersion
        stubAssetsVersion(supportRepo, cachedVersion, remoteVersion)

        val assetLists = hashMapOf(supportRepo to listOf(supportAsset))
        val filesystemNeedsExtraction = false

        val url = "url"
        stubApiVersionAndUrl(supportRepo, remoteVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }

        val expectedDownloadMetadata = DownloadMetadata(assetsTarName, supportRepo, remoteVersion, url)
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

        val assetLists = hashMapOf(repo to listOf(distAsset))
        stubAssetDoesNotNeedDownloading(distAsset, repo)
        val filesystemNeedsExtraction = false

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
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

        val assetLists = hashMapOf(repo to listOf(distAsset))
        stubAssetDoesNotNeedDownloading(distAsset, repo)
        val filesystemNeedsExtraction = true

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateDownloadRequirements does not include rootfs if filesystem needs extraction, there is a local version available, and the network is unreachable`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val assetFile = File("$distDirectory/${distAsset.name}")
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

        val assetLists = hashMapOf(repo to listOf(distAsset))
        val filesystemNeedsExtraction = true

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }

        assertTrue(result.isEmpty())
    }

    @Test(expected = UnknownHostException::class)
    fun `generateDownloadRequirements throws UnknownHostException if rootfs files need to be downloaded and the network is unreachable`() {
        val repo = filesystem.distributionType
        val distDirectory = File("$applicationFilesDirPath/$repo")
        val assetFile = File("$distDirectory/${distAsset.name}")
        distDirectory.mkdirs()
        assetFile.createNewFile()

        whenever(mockAssetPreferences.getLatestDownloadVersion(repo))
                .thenReturn(lowVersion)
        runBlocking {
            whenever(mockGithubApiClient.getLatestReleaseVersion(repo))
                    .thenThrow(UnknownHostException())
                    .thenThrow(UnknownHostException())
        }

        val assetLists = hashMapOf(repo to listOf(distAsset))
        val filesystemNeedsExtraction = true

        runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
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

        val assetLists = hashMapOf(repo to listOf(distAsset))
        stubAssetDoesNotNeedDownloading(distAsset, repo)
        val filesystemNeedsExtraction = true

        val url = "url"
        stubRootFsApiVersionAndUrl(repo, highVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
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

        val assetLists = hashMapOf(repo to listOf(distAsset))
        stubAssetDoesNotNeedDownloading(distAsset, repo)
        val filesystemNeedsExtraction = true

        val url = "url"
        stubRootFsApiVersionAndUrl(repo, highVersion, url)

        val result = runBlocking {
            assetRepository.generateDownloadRequirements(filesystem, assetLists, filesystemNeedsExtraction)
        }

        val expectedDownloads = listOf(DownloadMetadata(rootFsTarName, repo, highVersion, url))
        assertFalse(result.isEmpty())
        assertEquals(expectedDownloads, result)
    }

    @Test
    fun `getDistributionAssetsForExistingFilesystem propagates to AssetPreferences, and removes rootfs from list`() {
        val rootFsAsset = Asset("rootfs.tar.gz", distAsset.type)
        val assetList = listOf(distAsset, rootFsAsset)
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
        val assetList = listOf(supportAsset, distAsset, Asset("rootfs.tar.gz", distAsset.type))

        val supportDir = File("$applicationFilesDirPath/$supportRepo")
        val supportAssetFile = File("${supportDir.absolutePath}/${supportAsset.name}")
        val distDir = File("$applicationFilesDirPath/$distRepo")
        val distAssetFile = File("${distDir.absolutePath}/${distAsset.name}")

        supportDir.mkdirs()
        distDir.mkdirs()
        val result1 = assetRepository.assetsArePresentInSupportDirectories(assetList)

        supportAssetFile.createNewFile()
        val result2 = assetRepository.assetsArePresentInSupportDirectories(assetList)

        distAssetFile.createNewFile()
        val result3 = assetRepository.assetsArePresentInSupportDirectories(assetList)

        assertFalse(result1)
        assertFalse(result2)
        assertTrue(result3)
    }

    @Test
    fun `getAllAssetLists will fetch and cache support and distribution assets lists`() {
        val distRepo = filesystem.distributionType

        val distUrl = "distUrl"
        val supportUrl = "supportUrl"
        runBlocking {
            whenever(mockGithubApiClient.getAssetsListDownloadUrl(distRepo))
                    .thenReturn(distUrl)
            whenever(mockGithubApiClient.getAssetsListDownloadUrl(supportRepo))
                    .thenReturn(supportUrl)
        }

        val remoteDistAssetFile = File("$applicationFilesDirPath/remoteDist")
        remoteDistAssetFile.createNewFile()
        remoteDistAssetFile.bufferedWriter().apply {
            write("assets.txt garbage")
            newLine()
            write("distAsset garbage")
            flush()
            close()
        }

        val remoteSupportAssetFile = File("$applicationFilesDirPath/remoteSupport")
        remoteSupportAssetFile.createNewFile()
        remoteSupportAssetFile.bufferedWriter().apply {
            write("assets.txt garbage")
            newLine()
            write("supportAsset garbage")
            flush()
            close()
        }

        val distInputStream = remoteDistAssetFile.inputStream()
        val supportInputStream = remoteSupportAssetFile.inputStream()

        whenever(mockConnectionUtility.getUrlInputStream(distUrl))
                .thenReturn(distInputStream)
        whenever(mockConnectionUtility.getUrlInputStream(supportUrl))
                .thenReturn(supportInputStream)

        val result = runBlocking {
            assetRepository.getAllAssetLists(distRepo)
        }

        val expectedDistList = listOf(distAsset)
        val expectedSupportList = listOf(supportAsset)
        val expectedResult = hashMapOf(distRepo to expectedDistList, supportRepo to expectedSupportList)
        assertEquals(expectedResult, result)

        verifyBlocking(mockGithubApiClient) { getAssetsListDownloadUrl(distRepo) }
        verifyBlocking(mockGithubApiClient) { getAssetsListDownloadUrl(supportRepo) }
        verify(mockConnectionUtility).getUrlInputStream(distUrl)
        verify(mockConnectionUtility).getUrlInputStream(supportUrl)
        verify(mockAssetPreferences).setAssetList(distRepo, expectedDistList)
        verify(mockAssetPreferences).setAssetList(supportRepo, expectedSupportList)
    }

    @Test
    fun `getAllAssetLists will used cached lists if remote fetch fails`() {
        val distRepo = filesystem.distributionType
        val distAssetList = listOf(distAsset)
        val supportAssetList = listOf(supportAsset)

        runBlocking {
            whenever(mockGithubApiClient.getAssetsListDownloadUrl(any()))
                    .thenThrow(IOException::class.java)
        }
        whenever(mockAssetPreferences.getCachedAssetList(distRepo))
                .thenReturn(distAssetList)
        whenever(mockAssetPreferences.getCachedAssetList(supportRepo))
                .thenReturn(supportAssetList)

        val result = runBlocking {
            assetRepository.getAllAssetLists(distRepo)
        }

        val expectedResult = hashMapOf(distRepo to distAssetList, supportRepo to supportAssetList)
        assertEquals(expectedResult, result)
        verify(mockAssetPreferences).getCachedAssetList(distRepo)
        verify(mockAssetPreferences).getCachedAssetList(supportRepo)
    }
}