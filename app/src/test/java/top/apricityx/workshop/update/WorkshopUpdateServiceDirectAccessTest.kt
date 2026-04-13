package top.apricityx.workshop.update

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.apricityx.workshop.BuildConfig
import top.apricityx.workshop.ExperimentalGithubDirectAccessRuntime
import top.apricityx.workshop.GithubApiWattToolkitRouteProfile
import top.apricityx.workshop.GithubUserContentWattToolkitRouteProfile
import top.apricityx.workshop.GithubWebWattToolkitRouteProfile
import top.apricityx.workshop.WattToolkitWorkshopRouteResolver
import top.apricityx.workshop.WorkshopDirectHostnameVerifier

class WorkshopUpdateServiceDirectAccessTest {
    private lateinit var apiServer: MockWebServer
    private lateinit var githubApiForwardServer: MockWebServer
    private lateinit var githubWebForwardServer: MockWebServer
    private lateinit var githubAssetForwardServer: MockWebServer

    @Before
    fun setUp() {
        apiServer = MockWebServer()
        githubApiForwardServer = MockWebServer()
        githubWebForwardServer = MockWebServer()
        githubAssetForwardServer = MockWebServer()
        apiServer.start()
        githubApiForwardServer.start()
        githubWebForwardServer.start()
        githubAssetForwardServer.start()
    }

    @After
    fun tearDown() {
        apiServer.close()
        githubApiForwardServer.close()
        githubWebForwardServer.close()
        githubAssetForwardServer.close()
    }

    @Test
    fun `check for updates routes official github metadata through experimental github runtime`() = runBlocking {
        apiServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {
                      "🦓": [
                        {
                          "Items": [
                            {
                              "MatchDomainNames": "api.github.com",
                              "ForwardDomainNames": "http://githubapi.rmbgame.net:${githubApiForwardServer.port}",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        githubApiForwardServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {
                      "tag_name": "v1.0.1",
                      "published_at": "2026-03-12T10:00:00Z",
                      "body": "# 更新\n- 测试 GitHub 直连",
                      "assets": [
                        {
                          "name": "app-release.apk",
                          "browser_download_url": "https://github.com/Apricityx/WorkshopAndroidDownloader/releases/download/v1.0.1/app-release.apk"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = GithubApiWattToolkitRouteProfile,
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val runtime = ExperimentalGithubDirectAccessRuntime(
            resolvers = listOf(resolver),
            hostnameVerifier = WorkshopDirectHostnameVerifier(
                unsafeHostBypassProvider = resolver::allowsUnsafeHostnameBypass,
            ),
            directHttpClient = OkHttpClient.Builder()
                .dns(dns)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
        )
        val service = WorkshopUpdateService(
            baseClient = OkHttpClient.Builder().dns(dns).build(),
            directAccessRuntime = runtime,
        )

        val result = service.checkForUpdates(
            currentVersion = "1.0.1",
            preferredUserSource = UpdateSource.OFFICIAL,
        )

        assertThat(result).isInstanceOf(UpdateCheckExecutionResult.Success::class.java)
        val success = result as UpdateCheckExecutionResult.Success
        assertThat(success.hasUpdate).isFalse()
        assertThat(success.metadataSource).isEqualTo(UpdateSource.OFFICIAL)
        assertThat(success.release.normalizedVersion).isEqualTo("1.0.1")

        val routeRequest = apiServer.takeRequest()
        assertThat(routeRequest.url.encodedPath).isEqualTo("/accelerator/projectgroups")

        val forwardedRequest = githubApiForwardServer.takeRequest()
        assertThat(forwardedRequest.url.encodedPath).isEqualTo(
            "/repos/${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}/releases/latest",
        )
        assertThat(forwardedRequest.headers["Host"]).isEqualTo("api.github.com")
        assertThat(forwardedRequest.headers["Accept"]).isEqualTo("application/vnd.github+json")
    }

    @Test
    fun `check for updates resolves official download through github release redirect chain`() = runBlocking {
        val routePayload =
            """
            {
              "🦓": [
                {
                  "Items": [
                    {
                      "MatchDomainNames": "api.github.com",
                      "ForwardDomainNames": "http://githubapi.rmbgame.net:${githubApiForwardServer.port}",
                      "ProxyType": 0,
                      "IgnoreSSLCertVerification": true
                    },
                    {
                      "MatchDomainNames": "github.com",
                      "ForwardDomainNames": "http://github.rmbgame.net:${githubWebForwardServer.port}",
                      "ProxyType": 0,
                      "IgnoreSSLCertVerification": true
                    },
                    {
                      "MatchDomainNames": "githubusercontent.com;raw.github.com",
                      "ListenDomainNames": "raw.github.com;raw.githubusercontent.com;objects.githubusercontent.com;release-assets.githubusercontent.com",
                      "ForwardDomainNames": "http://githubusercontent.rmbgame.net:${githubAssetForwardServer.port}",
                      "ProxyType": 0,
                      "IgnoreSSLCertVerification": true
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        repeat(3) {
            apiServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(routePayload)
                    .build(),
            )
        }
        githubApiForwardServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {
                      "tag_name": "v1.0.1",
                      "published_at": "2026-03-12T10:00:00Z",
                      "body": "# 更新\n- 测试 GitHub Release",
                      "assets": [
                        {
                          "name": "app-release.apk",
                          "browser_download_url": "https://github.com/Apricityx/WorkshopAndroidDownloader/releases/download/v1.0.1/app-release.apk"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        githubWebForwardServer.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", "https://objects.githubusercontent.com/github-production-release-asset-test/app-release.apk")
                .build(),
        )
        githubAssetForwardServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Length", "1024")
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val apiResolver = WattToolkitWorkshopRouteResolver(
            routeProfile = GithubApiWattToolkitRouteProfile,
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val webResolver = WattToolkitWorkshopRouteResolver(
            routeProfile = GithubWebWattToolkitRouteProfile,
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val userContentResolver = WattToolkitWorkshopRouteResolver(
            routeProfile = GithubUserContentWattToolkitRouteProfile,
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val runtime = ExperimentalGithubDirectAccessRuntime(
            resolvers = listOf(apiResolver, webResolver, userContentResolver),
            hostnameVerifier = WorkshopDirectHostnameVerifier(
                unsafeHostBypassProvider = { host ->
                    listOf(apiResolver, webResolver, userContentResolver).any { resolver ->
                        resolver.allowsUnsafeHostnameBypass(host)
                    }
                },
            ),
            directHttpClient = OkHttpClient.Builder()
                .dns(dns)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
        )
        val service = WorkshopUpdateService(
            baseClient = OkHttpClient.Builder().dns(dns).build(),
            directAccessRuntime = runtime,
        )

        val result = service.checkForUpdates(
            currentVersion = "1.0.0",
            preferredUserSource = UpdateSource.OFFICIAL,
        )

        assertThat(result).isInstanceOf(UpdateCheckExecutionResult.Success::class.java)
        val success = result as UpdateCheckExecutionResult.Success
        assertThat(success.hasUpdate).isTrue()
        assertThat(success.metadataSource).isEqualTo(UpdateSource.OFFICIAL)
        assertThat(success.downloadResolution?.source).isEqualTo(UpdateSource.OFFICIAL)
        assertThat(success.downloadResolution?.resolvedUrl).isEqualTo(
            "https://github.com/Apricityx/WorkshopAndroidDownloader/releases/download/v1.0.1/app-release.apk",
        )

        repeat(3) { apiServer.takeRequest() }

        val metadataRequest = githubApiForwardServer.takeRequest()
        assertThat(metadataRequest.headers["Host"]).isEqualTo("api.github.com")

        val releaseRequest = githubWebForwardServer.takeRequest()
        assertThat(releaseRequest.headers["Host"]).isEqualTo("github.com")
        assertThat(releaseRequest.url.encodedPath).isEqualTo(
            "/Apricityx/WorkshopAndroidDownloader/releases/download/v1.0.1/app-release.apk",
        )

        val assetRequest = githubAssetForwardServer.takeRequest()
        assertThat(assetRequest.headers["Host"]).isEqualTo("objects.githubusercontent.com")
        assertThat(assetRequest.url.encodedPath).isEqualTo("/github-production-release-asset-test/app-release.apk")
    }
}
