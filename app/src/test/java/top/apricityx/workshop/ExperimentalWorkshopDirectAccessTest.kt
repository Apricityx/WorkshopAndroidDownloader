package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.Dns
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test

class ExperimentalWorkshopDirectAccessTest {
    private lateinit var apiServer: MockWebServer
    private lateinit var forwardedServer: MockWebServer

    @Before
    fun setUp() {
        ExperimentalWorkshopDirectAccessFallbackNotifier.resetForTesting()
        WattToolkitRouteFetchState.resetForTesting()
        apiServer = MockWebServer()
        forwardedServer = MockWebServer()
        apiServer.start()
        forwardedServer.start()
    }

    @After
    fun tearDown() {
        apiServer.close()
        forwardedServer.close()
        ExperimentalWorkshopDirectAccessFallbackNotifier.resetForTesting()
        WattToolkitRouteFetchState.resetForTesting()
    }

    @Test
    fun interceptor_fetches_watt_route_and_rewrites_workshop_request() {
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "http://steamcommunity.rmbgame.net:${forwardedServer.port}",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Set-Cookie", "steamCountry=US%7C123; Path=/; Domain=steamcommunity.com")
                .body("ok")
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val cookieJar = SteamWebSessionCookieJar()
        val originalUrl = "http://steamcommunity.com/workshop/browse/?appid=646570&searchtext=basemod".toHttpUrl()
        cookieJar.saveFromResponse(
            originalUrl,
            listOf(
                Cookie.Builder()
                    .name("sessionid")
                    .value("abc123")
                    .domain("steamcommunity.com")
                    .path("/")
                    .build(),
            ),
        )

        val routeResolver = WattToolkitWorkshopRouteResolver(
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val directClient = OkHttpClient.Builder()
            .dns(dns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = routeResolver,
                    steamCookieJar = cookieJar,
                    directCallFactory = directClient,
                ),
            )
            .build()

        client.newCall(Request.Builder().url(originalUrl).build()).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
            assertThat(response.request.url.host).isEqualTo("steamcommunity.com")
        }

        val routeRequest = apiServer.takeRequest()
        assertThat(routeRequest.url.encodedPath).isEqualTo("/accelerator/projectgroups")

        val forwardedRequest = forwardedServer.takeRequest()
        assertThat(forwardedRequest.url.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(forwardedRequest.url.queryParameter("appid")).isEqualTo("646570")
        assertThat(forwardedRequest.url.queryParameter("searchtext")).isEqualTo("basemod")
        assertThat(forwardedRequest.headers["Host"]).isEqualTo("steamcommunity.com")
        assertThat(forwardedRequest.headers["Cookie"]).contains("sessionid=abc123")

        val persistedCookies = cookieJar.loadForRequest(originalUrl)
        assertThat(persistedCookies.map { it.name }).containsAtLeast("sessionid", "steamCountry")
    }

    @Test
    fun routeResolver_parsesFakeServerName_andUsesItForForwardedTlsHost() {
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
                              "MatchDomainNames": "api.steampowered.com",
                              "ForwardDomainNames": "http://steamstore.rmbgame.net:${forwardedServer.port}",
                              "FakeServerName": "officecdn-microsoft-com.akamaized.net",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": false
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamStoreWattToolkitRouteProfile,
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )

        val route = resolver.resolveRouteForHost("api.steampowered.com")

        assertThat(route).isNotNull()
        assertThat(route?.fakeServerName).isEqualTo("officecdn-microsoft-com.akamaized.net")
        assertThat(
            route?.buildForwardedUrl(
                "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/".toHttpUrl(),
            )?.host,
        ).isEqualTo("officecdn-microsoft-com.akamaized.net")
    }

    @Test
    fun interceptor_mapsFakeServerNameToForwardDns_andPreservesLogicalHost() {
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
                              "MatchDomainNames": "api.steampowered.com",
                              "ForwardDomainNames": "http://steamstore-forward.test:${forwardedServer.port}",
                              "FakeServerName": "officecdn-microsoft-com.akamaized.net",
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
        forwardedServer.enqueue(MockResponse.Builder().code(200).body("ok").build())

        var lookedUpHost: String? = null
        val routeDns = WattToolkitForwardDns(
            Dns { hostname ->
                lookedUpHost = hostname
                listOf(InetAddress.getByName("127.0.0.1"))
            },
        )
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamStoreWattToolkitRouteProfile,
            client = OkHttpClient.Builder().build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val directClient = OkHttpClient.Builder()
            .dns(routeDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = resolver,
                    steamCookieJar = SteamWebSessionCookieJar(),
                    directCallFactory = directClient,
                    forwardDns = routeDns,
                ),
            )
            .build()

        client.newCall(
            Request.Builder()
                .url("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
                .build(),
        ).execute().use { response ->
            assertThat(response.code).isEqualTo(200)
            assertThat(response.request.url.host).isEqualTo("api.steampowered.com")
        }

        assertThat(lookedUpHost).isEqualTo("steamstore-forward.test")
        apiServer.takeRequest()
        val forwardedRequest = forwardedServer.takeRequest()
        assertThat(forwardedRequest.headers["Host"]).isEqualTo("api.steampowered.com")
        assertThat(forwardedRequest.url.encodedPath)
            .isEqualTo("/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
    }

    @Test
    fun interceptor_keeps_redirect_chain_on_forwarded_route_and_refreshes_cookies() {
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "http://steamcommunity.rmbgame.net:${forwardedServer.port}",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", "https://steamcommunity.com/workshop/")
                .addHeader("Set-Cookie", "steamLogin=1; Path=/; Domain=steamcommunity.com")
                .build(),
        )
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("ok")
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val cookieJar = SteamWebSessionCookieJar()
        val originalUrl = "http://steamcommunity.com/login/home/?goto=workshop%2F".toHttpUrl()
        cookieJar.saveFromResponse(
            originalUrl,
            listOf(
                Cookie.Builder()
                    .name("sessionid")
                    .value("abc123")
                    .domain("steamcommunity.com")
                    .path("/")
                    .build(),
            ),
        )

        val routeResolver = WattToolkitWorkshopRouteResolver(
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val directClient = OkHttpClient.Builder()
            .dns(dns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = routeResolver,
                    steamCookieJar = cookieJar,
                    directCallFactory = directClient,
                ),
            )
            .build()

        client.newCall(Request.Builder().url(originalUrl).build()).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
            assertThat(response.request.url.host).isEqualTo("steamcommunity.com")
            assertThat(response.request.url.encodedPath).isEqualTo("/workshop/")
        }

        apiServer.takeRequest()

        val firstForwardedRequest = forwardedServer.takeRequest()
        assertThat(firstForwardedRequest.url.encodedPath).isEqualTo("/login/home/")
        assertThat(firstForwardedRequest.headers["Host"]).isEqualTo("steamcommunity.com")
        assertThat(firstForwardedRequest.headers["Cookie"]).contains("sessionid=abc123")

        val secondForwardedRequest = forwardedServer.takeRequest()
        assertThat(secondForwardedRequest.url.encodedPath).isEqualTo("/workshop/")
        assertThat(secondForwardedRequest.headers["Host"]).isEqualTo("steamcommunity.com")
        assertThat(secondForwardedRequest.headers["Cookie"]).contains("sessionid=abc123")
        assertThat(secondForwardedRequest.headers["Cookie"]).contains("steamLogin=1")
    }

    @Test
    fun interceptor_rewrites_api_requests_through_watt_store_route() {
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
                              "MatchDomainNames": "store.steampowered.com;api.steampowered.com;login.steampowered.com",
                              "ForwardDomainNames": "http://steamstore.rmbgame.net:${forwardedServer.port}",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"response":{"publishedfiledetails":[]}}""")
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val routeResolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamStoreWattToolkitRouteProfile,
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val directClient = OkHttpClient.Builder()
            .dns(dns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = routeResolver,
                    steamCookieJar = SteamWebSessionCookieJar(),
                    directCallFactory = directClient,
                ),
            )
            .build()

        client.newCall(
            Request.Builder()
                .url("http://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/".toHttpUrl())
                .post("itemcount=0".toByteArray().toRequestBody())
                .build(),
        ).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
            assertThat(response.request.url.host).isEqualTo("api.steampowered.com")
        }

        apiServer.takeRequest()
        val forwardedRequest = forwardedServer.takeRequest()
        assertThat(forwardedRequest.url.encodedPath).isEqualTo("/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
        assertThat(forwardedRequest.headers["Host"]).isEqualTo("api.steampowered.com")
    }

    @Test
    fun interceptor_rewrites_steam_preview_images_through_watt_image_route() {
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
                              "MatchDomainNames": "steamcdn-a.akamaihd.net;steamuserimages-a.akamaihd.net;cdn.akamai.steamstatic.com;community.akamai.steamstatic.com;avatars.akamai.steamstatic.com;store.akamai.steamstatic.com",
                              "ForwardDomainNames": "http://steamimage.rmbgame.net:${forwardedServer.port}",
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
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("image-bytes")
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamImageWattToolkitRouteProfile,
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )
        val directClient = OkHttpClient.Builder()
            .dns(dns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = resolver,
                    steamCookieJar = SteamWebSessionCookieJar(),
                    directCallFactory = directClient,
                ),
            )
            .build()

        client.newCall(
            Request.Builder()
                .url("https://images.steamusercontent.com/ugc/123/preview.jpg")
                .build(),
        ).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
            assertThat(response.body?.string()).isEqualTo("image-bytes")
            assertThat(response.request.url.host).isEqualTo("images.steamusercontent.com")
        }

        apiServer.takeRequest()
        val forwardedRequest = forwardedServer.takeRequest()
        assertThat(forwardedRequest.url.encodedPath).isEqualTo("/ugc/123/preview.jpg")
        assertThat(forwardedRequest.headers["Host"]).isEqualTo("images.steamusercontent.com")
    }

    @Test
    fun routeResolver_matches_wildcard_steam_content_cdn_host() {
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
                              "MatchDomainNames": "*.st.dl.eccdnx.com",
                              "ListenDomainNames": "*.st.dl.eccdnx.com",
                              "ForwardDomainNames": "cdn.example.test",
                              "ProxyType": 0,
                              "Checked": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        val profile = WattToolkitRouteProfile(
            name = "steam-content-cdn-test",
            cacheFileName = "steam-content-cdn-test.json",
            supportedHosts = setOf("st.dl.eccdnx.com"),
            bootstrapForwardTargets = emptyList(),
        )
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = profile,
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )

        val route = resolver.resolveRouteForHost("st.dl.eccdnx.com")

        assertThat(route?.forwardTargets).containsExactly("cdn.example.test")
    }

    @Test
    fun routeResolver_ignores_unchecked_watt_route() {
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
                              "MatchDomainNames": "steamcommunity.com",
                              "ForwardDomainNames": "unchecked.example.test",
                              "ProxyType": 0,
                              "Checked": false
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = WattToolkitRouteProfile(
                name = "unchecked-route-test",
                cacheFileName = "unchecked-route-test.json",
                supportedHosts = setOf("steamcommunity.com"),
                bootstrapForwardTargets = emptyList(),
            ),
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )

        assertThat(resolver.resolveRouteForHost("steamcommunity.com")).isNull()
    }

    @Test
    fun routeResolver_ranks_forward_targets_by_success_rate_then_latency() {
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
                              "MatchDomainNames": "steamcommunity.com",
                              "ForwardDomainNames": "slow-node.test;fast-node.test",
                              "ProxyType": 0,
                              "Checked": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = WattToolkitRouteProfile(
                name = "ranked-route-test",
                cacheFileName = "ranked-route-test.json",
                supportedHosts = setOf("steamcommunity.com"),
                bootstrapForwardTargets = emptyList(),
            ),
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            forwardTargetProbe = { target ->
                when (target) {
                    "slow-node.test" -> WattToolkitForwardTargetProbe(
                        successes = 2,
                        attempts = 3,
                        latencyMs = 5,
                    )
                    else -> WattToolkitForwardTargetProbe(
                        successes = 3,
                        attempts = 3,
                        latencyMs = 40,
                    )
                }
            },
        )

        val route = resolver.resolveRouteForHost("steamcommunity.com")

        assertThat(route?.forwardTargets).containsExactly("fast-node.test", "slow-node.test").inOrder()
    }

    @Test
    fun interceptor_fails_over_to_next_forward_target_after_connection_error() {
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
                              "MatchDomainNames": "steamcommunity.com",
                              "ForwardDomainNames": "http://127.0.0.1:1;http://steamcommunity-fallback.test:${forwardedServer.port}",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true,
                              "Checked": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("fallback-ok")
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = WattToolkitRouteProfile(
                name = "fallback-route-test",
                cacheFileName = "fallback-route-test.json",
                supportedHosts = setOf("steamcommunity.com"),
                bootstrapForwardTargets = emptyList(),
            ),
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            forwardTargetProbe = {
                WattToolkitForwardTargetProbe(successes = 1, attempts = 1, latencyMs = 1)
            },
        )
        val directClient = OkHttpClient.Builder()
            .dns(dns)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = resolver,
                    steamCookieJar = SteamWebSessionCookieJar(),
                    directCallFactory = directClient,
                ),
            )
            .build()

        client.newCall(
            Request.Builder()
                .url("https://steamcommunity.com/workshop/browse/?appid=646570")
                .build(),
        ).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
            assertThat(response.body?.string()).isEqualTo("fallback-ok")
            assertThat(response.request.url.host).isEqualTo("steamcommunity.com")
        }

        val forwardedRequest = forwardedServer.takeRequest()
        assertThat(forwardedRequest.url.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(forwardedRequest.headers["Host"]).isEqualTo("steamcommunity.com")
    }

    @Test
    fun routeResolver_matches_github_release_hosts_from_listen_domain_names() {
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
                              "MatchDomainNames": "githubusercontent.com;raw.github.com",
                              "ListenDomainNames": "raw.github.com;raw.githubusercontent.com;objects.githubusercontent.com;release-assets.githubusercontent.com",
                              "ForwardDomainNames": "23.235.37.133",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = GithubUserContentWattToolkitRouteProfile,
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
        )

        val route = resolver.resolveRouteForHost("objects.githubusercontent.com")

        assertThat(route).isEqualTo(
            WattToolkitWorkshopRoute(
                logicalHosts = setOf(
                    "githubusercontent.com",
                    "raw.github.com",
                    "raw.githubusercontent.com",
                    "objects.githubusercontent.com",
                    "release-assets.githubusercontent.com",
                ),
                forwardTargets = listOf("23.235.37.133"),
                ignoreSslCertVerification = true,
            ),
        )
    }

    @Test
    fun routeResolver_retries_transient_failures_before_succeeding() {
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "steamcommunity.rmbgame.net",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        var remainingFailures = 2
        val retryDelaysMs = mutableListOf<Long>()
        val resolver = WattToolkitWorkshopRouteResolver(
            client = OkHttpClient.Builder()
                .dns(dns)
                .addInterceptor { chain ->
                    if (remainingFailures > 0) {
                        remainingFailures--
                        throw IOException("transient route failure")
                    }
                    chain.proceed(chain.request())
                }.build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            sleepProvider = { delayMs -> retryDelaysMs.add(delayMs) },
        )

        val route = resolver.resolveSteamCommunityRoute()

        assertThat(route).isNotNull()
        assertThat(route?.forwardTargets).containsExactly("steamcommunity.rmbgame.net")
        assertThat(retryDelaysMs).containsExactly(250L, 500L).inOrder()
        assertThat(apiServer.requestCount).isEqualTo(1)
    }

    @Test
    fun routeResolver_accepts_current_store_mirror_when_fake_sni_is_present() {
        val route = WattToolkitWorkshopRoute(
            logicalHosts = SteamStoreWattToolkitRouteProfile.supportedHosts,
            forwardTargets = listOf("steamstore.rmbgame.net"),
            ignoreSslCertVerification = false,
            fakeServerName = "steamstore-a.akamaihd.net",
        )

        assertThat(route.isKnownBrokenLegacyRoute()).isFalse()
    }

    @Test
    fun routeResolver_rejects_legacy_store_mirror_without_fake_sni() {
        val route = WattToolkitWorkshopRoute(
            logicalHosts = SteamStoreWattToolkitRouteProfile.supportedHosts,
            forwardTargets = listOf("steamstore.rmbgame.net"),
            ignoreSslCertVerification = true,
        )

        assertThat(route.isKnownBrokenLegacyRoute()).isTrue()
    }

    @Test
    fun routeResolver_discards_known_broken_persisted_route_when_refresh_fails() {
        val persistedRoute = WattToolkitWorkshopRoute(
            logicalHosts = DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS,
            forwardTargets = listOf("steamcommunity.rmbgame.net"),
            ignoreSslCertVerification = true,
        )
        val store = FakeWattToolkitWorkshopRouteStore(
            persisted = PersistedWattToolkitWorkshopRoute(
                route = persistedRoute,
                cachedAtMs = 1_000L,
            ),
        )
        val resolver = WattToolkitWorkshopRouteResolver(
            client = OkHttpClient.Builder()
                .addInterceptor { throw IOException("route fetch unavailable") }
                .build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            routeStore = store,
            nowProvider = { 1_000L + 31L * 60L * 1_000L },
            sleepProvider = { _ -> },
        )

        val route = resolver.resolveSteamCommunityRoute()

        assertThat(route?.forwardTargets).containsExactly("https://www.valvesoftware.com")
        assertThat(route?.isKnownBrokenLegacyRoute()).isFalse()
        assertThat(store.loadCount).isEqualTo(1)
        assertThat(store.saved).hasSize(1)
    }

    @Test
    fun routeResolver_refreshRouteForHost_clears_persisted_route_before_fetching_replacement() {
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "fresh.steamcommunity.rmbgame.net",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        val persistedRoute = WattToolkitWorkshopRoute(
            logicalHosts = DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS,
            forwardTargets = listOf("stale.steamcommunity.rmbgame.net"),
            ignoreSslCertVerification = true,
        )
        val store = FakeWattToolkitWorkshopRouteStore(
            persisted = PersistedWattToolkitWorkshopRoute(
                route = persistedRoute,
                cachedAtMs = 1_000L,
            ),
        )
        val resolver = WattToolkitWorkshopRouteResolver(
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            routeStore = store,
            nowProvider = { 2_000L },
            sleepProvider = { _ -> },
        )

        assertThat(resolver.resolveSteamCommunityRoute()).isEqualTo(persistedRoute)

        val refreshed = resolver.refreshRouteForHost("steamcommunity.com")

        assertThat(refreshed?.forwardTargets).containsExactly("fresh.steamcommunity.rmbgame.net")
        assertThat(store.clearCount).isEqualTo(1)
        assertThat(store.currentPersisted?.route).isEqualTo(refreshed)
    }

    @Test
    fun routeResolver_uses_builtin_bootstrap_route_when_fetch_fails_without_cache() {
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamCommunityWattToolkitRouteProfile,
            client = OkHttpClient.Builder()
                .addInterceptor { throw IOException("route fetch unavailable") }
                .build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            nowProvider = { 12_345L },
            sleepProvider = { _ -> },
        )

        val route = resolver.resolveRouteForHost("steamcommunity.com")

        assertThat(route).isEqualTo(
            WattToolkitWorkshopRoute(
                logicalHosts = DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS,
                forwardTargets = listOf("https://www.valvesoftware.com"),
                ignoreSslCertVerification = false,
                fakeServerName = "www.valvesoftware.com",
            ),
        )
    }

    @Test
    fun routeResolver_uses_builtin_bootstrap_store_route_when_fetch_fails_without_cache() {
        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamStoreWattToolkitRouteProfile,
            client = OkHttpClient.Builder()
                .addInterceptor { throw IOException("route fetch unavailable") }
                .build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            nowProvider = { 12_345L },
            sleepProvider = { _ -> },
        )

        val route = resolver.resolveRouteForHost("api.steampowered.com")

        assertThat(route).isEqualTo(
            WattToolkitWorkshopRoute(
                logicalHosts = DEFAULT_WATT_TOOLKIT_STEAM_STORE_ROUTE_HOSTS,
                forwardTargets = listOf("steamstore.rmbgame.net"),
                ignoreSslCertVerification = false,
                fakeServerName = "steamstore-a.akamaihd.net",
            ),
        )
    }

    @Test
    fun routeResolver_detects_captive_portal_keyword_after_preview_window() {
        val longPrefix = "x".repeat(240)
        apiServer.enqueue(
            MockResponse.Builder()
                .code(404)
                .addHeader("Server", "nginx/1.14.0")
                .body("$longPrefix 欢迎使用校园网，正在进入认证页面")
                .build(),
        )

        val resolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamCommunityWattToolkitRouteProfile,
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            sleepProvider = { _ -> },
        )

        val route = resolver.resolveSteamCommunityRoute()

        assertThat(route?.forwardTargets).containsExactly("https://www.valvesoftware.com")
        assertThat(WattToolkitRouteFetchState.isCaptivePortalBlocked()).isTrue()
    }

    @Test
    fun routeResolver_suppresses_follow_up_fetches_after_captive_portal_detection() {
        apiServer.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("欢迎使用校园网，正在进入认证页面")
                .build(),
        )

        val firstResolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamCommunityWattToolkitRouteProfile,
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            sleepProvider = { _ -> },
        )
        val secondResolver = WattToolkitWorkshopRouteResolver(
            routeProfile = SteamStoreWattToolkitRouteProfile,
            client = OkHttpClient(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            sleepProvider = { _ -> },
        )

        assertThat(firstResolver.resolveSteamCommunityRoute()?.forwardTargets)
            .containsExactly("https://www.valvesoftware.com")
        assertThat(secondResolver.resolveRouteForHost("api.steampowered.com")?.forwardTargets)
            .containsExactly("steamstore.rmbgame.net")
        assertThat(apiServer.requestCount).isEqualTo(1)
    }

    @Test
    fun fileBackedRouteStore_roundTrips_route_snapshot() {
        val tempDir = Files.createTempDirectory("watt-route-store").toFile()
        val cacheFile = File(tempDir, "watt-route-cache.json")
        val store = FileBackedWattToolkitWorkshopRouteStore(cacheFile)
        val expected = PersistedWattToolkitWorkshopRoute(
            route = WattToolkitWorkshopRoute(
                logicalHosts = DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS,
                forwardTargets = listOf("https://steamcommunity.rmbgame.net"),
                ignoreSslCertVerification = true,
                fakeServerName = "officecdn-microsoft-com.akamaized.net",
            ),
            cachedAtMs = 12_345L,
        )

        store.save(expected)
        val restored = store.load()

        assertThat(restored).isEqualTo(expected)
        tempDir.deleteRecursively()
    }

    @Test
    fun fileBackedRouteStore_loads_legacy_snapshot_with_fallback_hosts() {
        val tempDir = Files.createTempDirectory("watt-route-store-legacy").toFile()
        val cacheFile = File(tempDir, "watt-route-cache.json")
        cacheFile.writeText(
            """
            {
              "cachedAtMs": 12345,
              "forwardTargets": ["steamcommunity.rmbgame.net"],
              "ignoreSslCertVerification": true
            }
            """.trimIndent(),
        )
        val store = FileBackedWattToolkitWorkshopRouteStore(
            file = cacheFile,
            fallbackLogicalHosts = DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS,
        )

        val restored = store.load()

        assertThat(restored?.route?.logicalHosts).isEqualTo(DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS)
        assertThat(restored?.route?.forwardTargets).containsExactly("steamcommunity.rmbgame.net")
        tempDir.deleteRecursively()
    }

    @Test
    fun defaultWattToolkitRouteClient_uses_http1_only() {
        val client = defaultWattToolkitRouteClient()

        assertThat(client.protocols).containsExactly(Protocol.HTTP_1_1)
    }

    @Test
    fun wattRoute_bypasses_hostname_verification_only_for_forward_hosts_when_flag_is_enabled() {
        val route = WattToolkitWorkshopRoute(
            logicalHosts = DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS,
            forwardTargets = listOf("steamcommunity.rmbgame.net"),
            ignoreSslCertVerification = true,
        )

        assertThat(route.shouldBypassHostnameVerification("steamcommunity.rmbgame.net")).isTrue()
        assertThat(route.shouldBypassHostnameVerification("steamcommunity.com")).isFalse()
    }

    @Test
    fun interceptor_retries_with_refreshed_watt_route_after_forwarded_403() {
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "http://steamcommunity.rmbgame.net:${forwardedServer.port}",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "http://steamcommunity.rmbgame.net:${forwardedServer.port}",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(403)
                .body("forbidden")
                .build(),
        )
        forwardedServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("ok")
                .build(),
        )

        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val store = FakeWattToolkitWorkshopRouteStore()
        val routeResolver = WattToolkitWorkshopRouteResolver(
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            routeStore = store,
            sleepProvider = { _ -> },
        )
        val directClient = OkHttpClient.Builder()
            .dns(dns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = routeResolver,
                    steamCookieJar = SteamWebSessionCookieJar(),
                    directCallFactory = directClient,
                ),
            )
            .build()

        client.newCall(
            Request.Builder()
                .url("http://steamcommunity.com/workshop/browse/?appid=646570&searchtext=basemod".toHttpUrl())
                .build(),
        ).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
            assertThat(response.request.url.host).isEqualTo("steamcommunity.com")
        }

        assertThat(apiServer.requestCount).isEqualTo(2)
        assertThat(store.clearCount).isEqualTo(1)
        val rejectedRequest = forwardedServer.takeRequest()
        assertThat(rejectedRequest.url.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(rejectedRequest.headers["Host"]).isEqualTo("steamcommunity.com")
        val forwardedRequest = forwardedServer.takeRequest()
        assertThat(forwardedRequest.url.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(forwardedRequest.headers["Host"]).isEqualTo("steamcommunity.com")
    }

    @Test
    fun interceptor_falls_back_when_refreshed_route_still_fails() {
        val unavailablePort = ServerSocket(0).use { serverSocket -> serverSocket.localPort }
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "http://steamcommunity.rmbgame.net:$unavailablePort",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
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
                              "MatchDomainNames": "steamcommunity.com;www.steamcommunity.com",
                              "ForwardDomainNames": "http://steamcommunity.rmbgame.net:$unavailablePort",
                              "ProxyType": 0,
                              "IgnoreSSLCertVerification": true
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ).build(),
        )
        val dns = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        val store = FakeWattToolkitWorkshopRouteStore()
        var fallbackNoticeCount = 0
        val routeResolver = WattToolkitWorkshopRouteResolver(
            client = OkHttpClient.Builder().dns(dns).build(),
            projectGroupsUrl = apiServer.url("/accelerator/projectgroups"),
            routeStore = store,
            sleepProvider = { _ -> },
        )
        val directClient = OkHttpClient.Builder()
            .dns(dns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val client = OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = { true },
                    routeResolver = routeResolver,
                    steamCookieJar = SteamWebSessionCookieJar(),
                    directCallFactory = directClient,
                    fallbackNoticeSink = SteamDirectAccessFallbackNoticeSink { fallbackNoticeCount++ },
                ),
            )
            .build()
        val error = runCatching {
            client.newCall(
                Request.Builder()
                    .url("http://steamcommunity.com/workshop/browse/?appid=646570&searchtext=basemod")
                    .build(),
            ).execute().use { it.body?.close() }
        }.exceptionOrNull()

        assertThat(apiServer.requestCount).isEqualTo(2)
        assertThat(store.clearCount).isEqualTo(1)
        assertThat(fallbackNoticeCount).isEqualTo(1)
        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(forwardedServer.requestCount).isEqualTo(0)
    }

    @Test
    fun fallbackNotifier_disables_direct_access_for_current_process_after_first_fallback() {
        assertThat(ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessAllowed(userEnabled = true)).isTrue()
        assertThat(
            ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessDisabledForCurrentProcess(),
        ).isFalse()

        ExperimentalWorkshopDirectAccessFallbackNotifier.onFallbackToOriginalSteamRoute()

        assertThat(ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessAllowed(userEnabled = true)).isFalse()
        assertThat(
            ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessDisabledForCurrentProcess(),
        ).isTrue()
        assertThat(ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessAllowed(userEnabled = false)).isFalse()
    }

    private class FakeWattToolkitWorkshopRouteStore(
        private val persisted: PersistedWattToolkitWorkshopRoute? = null,
    ) : WattToolkitWorkshopRouteStore {
        var loadCount: Int = 0
            private set
        var clearCount: Int = 0
            private set
        var currentPersisted: PersistedWattToolkitWorkshopRoute? = persisted
            private set
        val saved: MutableList<PersistedWattToolkitWorkshopRoute> = mutableListOf()

        override fun load(): PersistedWattToolkitWorkshopRoute? {
            loadCount++
            return currentPersisted
        }

        override fun save(route: PersistedWattToolkitWorkshopRoute) {
            currentPersisted = route
            saved += route
        }

        override fun clear() {
            clearCount++
            currentPersisted = null
        }
    }
}
