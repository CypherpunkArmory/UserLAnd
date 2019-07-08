package tech.ula.model.remote

import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.utils.Logger
import tech.ula.utils.BuildWrapper
import java.io.IOException

@RunWith(MockitoJUnitRunner::class)
class GithubApiClientTest {

    @get:Rule val server = MockWebServer()

    @Mock lateinit var mockBuildWrapper: BuildWrapper

    @Mock lateinit var mockUrlProvider: UrlProvider

    @Mock lateinit var mockLogger: Logger

    private val moshi = Moshi.Builder().build()

    private lateinit var githubApiClient: GithubApiClient

    private val testRepo = "repo"
    private val testReleaseToUse = "latest"
    private val testEndpoint = "/repos/CypherpunkArmory/UserLAnd-Assets-$testRepo/releases/$testReleaseToUse"

    private val testArch = "arch"
    private val testAssetsTxtUrl = "assetsTxtUrl"
    private val testAssetsTxtName = "$testArch-assets.txt"
    private val testAssetsTxtDownloadUrl = "assetsTxtDownloadUrl"

    private val testUrl = "testUrl"
    private val testAssetType = "testType"
    private val testName = "testName"
    private val testTag = "v1.0.0"
    private val testAssetUrl = "assetUrl"
    private val testAssetName = "$testArch-$testAssetType"
    private val testAssetDownloadUrl = "assetDownloadUrl"

    private val testAssetsJson = """
    [
        {
            "url": "$testAssetUrl",
            "name": "$testAssetName",
            "browser_download_url": "$testAssetDownloadUrl"
        },
        {
            "url": "$testAssetsTxtUrl",
            "name": "$testAssetsTxtName",
            "browser_download_url": "$testAssetsTxtDownloadUrl"
        }
    ]
    """
    private val json: String = """
    {
        "url": "$testUrl",
        "name": "$testName",
        "tag_name": "$testTag",
        "assets": $testAssetsJson
    }
    """

    @Before
    fun setup() {
        whenever(mockBuildWrapper.getArchType()).thenReturn(testArch)

        githubApiClient = GithubApiClient(mockBuildWrapper, mockUrlProvider, mockLogger)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun stubBaseUrl() {
        val url = server.url("/")
        whenever(mockUrlProvider.getBaseUrl()).thenReturn("${url.url()}")
    }

    @Test
    fun `JsonClass correctly generate ReleasesResponse`() {
        val adapter = moshi.adapter(GithubApiClient.ReleasesResponse::class.java)
        val releasesResponse: GithubApiClient.ReleasesResponse = adapter.fromJson(json)!!

        assertEquals(testUrl, releasesResponse.url)
        assertEquals(testName, releasesResponse.name)
        assertEquals(testTag, releasesResponse.tag)
        assertEquals(testAssetUrl, releasesResponse.assets[0].url)
        assertEquals(testAssetName, releasesResponse.assets[0].name)
        assertEquals(testAssetDownloadUrl, releasesResponse.assets[0].downloadUrl)
    }

    @Test
    fun `getAssetsListDownloadUrl can parse json results`() {
        val response = MockResponse()
        response.setBody(json)
        server.enqueue(response)
        stubBaseUrl()

        val result = runBlocking { githubApiClient.getAssetsListDownloadUrl(testRepo) }

        val request = server.takeRequest()
        assertEquals(testEndpoint, request.path)
        assertEquals(testAssetsTxtDownloadUrl, result)
    }

    @Test
    fun `getAssetsListDownloadUrl memoizes results`() {
        val response = MockResponse()
        response.setBody(json)
        server.enqueue(response)
        stubBaseUrl()

        val result1 = runBlocking { githubApiClient.getAssetsListDownloadUrl(testRepo) }
        val result2 = runBlocking { githubApiClient.getAssetsListDownloadUrl(testRepo) }

        assertEquals(testAssetsTxtDownloadUrl, result1)
        assertEquals(testAssetsTxtDownloadUrl, result2)
        verify(mockUrlProvider, times(1)).getBaseUrl()
    }

    @Test(expected = IOException::class)
    fun `getAssetsListDownloadUrl throws IOException if server response is not successful`() {
        val response = MockResponse()
        response.setResponseCode(500)
        server.enqueue(response)
        stubBaseUrl()

        runBlocking { githubApiClient.getAssetsListDownloadUrl(testRepo) }

        verify(mockLogger.addExceptionBreadcrumb(IOException()))
    }

    @Test
    fun `getLatestReleaseVersion can parse json results`() {
        val response = MockResponse()
        response.setBody(json)
        server.enqueue(response)
        stubBaseUrl()

        val result = runBlocking { githubApiClient.getLatestReleaseVersion(testRepo) }

        val request = server.takeRequest()
        assertEquals(testEndpoint, request.path)
        assertEquals(testTag, result)
    }

    @Test
    fun `getLatestReleaseVersion memoizes result`() {
        val response = MockResponse()
        response.setBody(json)
        server.enqueue(response)
        stubBaseUrl()

        val result1 = runBlocking { githubApiClient.getLatestReleaseVersion(testRepo) }
        val result2 = runBlocking { githubApiClient.getLatestReleaseVersion(testRepo) }

        val request = server.takeRequest()
        assertEquals(testEndpoint, request.path)
        assertEquals(testTag, result1)
        assertEquals(testTag, result2)
        verify(mockUrlProvider, times(1)).getBaseUrl()
    }

    @Test(expected = IOException::class)
    fun `getLatestReleaseVersion throws IOException if server response is not successful`() {
        val response = MockResponse()
        response.setResponseCode(500)
        server.enqueue(response)
        stubBaseUrl()

        runBlocking { githubApiClient.getLatestReleaseVersion(testRepo) }

        verify(mockLogger.addExceptionBreadcrumb(IOException()))
    }

    @Test
    fun `getAssetEndpoint can parse json results`() {
        val response = MockResponse()
        response.setBody(json)
        server.enqueue(response)
        stubBaseUrl()

        val result = runBlocking { githubApiClient.getAssetEndpoint(testAssetType, testRepo) }

        val request = server.takeRequest()
        assertEquals(testEndpoint, request.path)
        assertEquals(testAssetDownloadUrl, result)
    }

    @Test
    fun `getAssetEndpoint memoizes result`() {
        val response = MockResponse()
        response.setBody(json)
        server.enqueue(response)
        stubBaseUrl()

        val result1 = runBlocking { githubApiClient.getAssetEndpoint(testAssetType, testRepo) }
        val result2 = runBlocking { githubApiClient.getAssetEndpoint(testAssetType, testRepo) }

        val request = server.takeRequest()
        assertEquals(testEndpoint, request.path)
        assertEquals(testAssetDownloadUrl, result1)
        assertEquals(testAssetDownloadUrl, result2)
        verify(mockUrlProvider, times(1)).getBaseUrl()
    }

    @Test(expected = IOException::class)
    fun `getAssetEndpoint throws IOException if server response is not successful`() {
        val response = MockResponse()
        response.setResponseCode(500)
        server.enqueue(response)
        stubBaseUrl()

        runBlocking { githubApiClient.getAssetEndpoint(testAssetType, testRepo) }

        verify(mockLogger).addExceptionBreadcrumb(IOException())
    }

    @Test
    fun `Results are memoized across API queries`() {
        val response = MockResponse()
        response.setBody(json)
        server.enqueue(response)
        stubBaseUrl()

        val assetsListResult = runBlocking { githubApiClient.getAssetsListDownloadUrl(testRepo) }
        val releaseVersionResult = runBlocking { githubApiClient.getLatestReleaseVersion(testRepo) }
        val assetEndpointResult = runBlocking { githubApiClient.getAssetEndpoint(testAssetType, testRepo) }

        assertEquals(testAssetsTxtDownloadUrl, assetsListResult)
        assertEquals(testTag, releaseVersionResult)
        assertEquals(testAssetDownloadUrl, assetEndpointResult)
        verify(mockUrlProvider, times(1)).getBaseUrl()
    }
}