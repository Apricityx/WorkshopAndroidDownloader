package top.apricityx.workshop.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.apricityx.workshop.SteamLanguagePreference
import top.apricityx.workshop.WorkshopBrowseSortOption
import top.apricityx.workshop.WorkshopBrowseTimeWindow

class WorkshopBrowseRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: WorkshopBrowseRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = WorkshopBrowseRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            detailBaseUrl = server.url("/"),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun browseGameWorkshop_includesSelectedPopularWindowInRequest() = runBlocking {
        server.enqueue(mockResponse("<html></html>"))

        repository.browseGameWorkshop(
            appId = 646570u,
            searchQuery = "spire",
            sortOption = WorkshopBrowseSortOption.MostPopular,
            timeWindow = WorkshopBrowseTimeWindow.ThirtyDays,
            page = 3,
        )

        val request = server.takeRequest()
        val requestUrl = request.url
        assertThat(requestUrl.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(requestUrl.queryParameter("appid")).isEqualTo("646570")
        assertThat(requestUrl.queryParameter("searchtext")).isEqualTo("spire")
        assertThat(requestUrl.queryParameter("l")).isEqualTo("schinese")
        assertThat(requestUrl.queryParameter("browsesort")).isEqualTo("trend")
        assertThat(requestUrl.queryParameter("actualsort")).isEqualTo("trend")
        assertThat(requestUrl.queryParameter("days")).isEqualTo("30")
        assertThat(requestUrl.queryParameter("p")).isEqualTo("3")
        assertThat(request.headers["User-Agent"]).isEqualTo(STEAM_WEB_BROWSER_USER_AGENT)
        assertThat(request.headers["Accept"]).contains("text/html")
    }

    @Test
    fun browseGameWorkshop_omitsDaysForNonPopularSort() = runBlocking {
        server.enqueue(mockResponse("<html></html>"))

        repository.browseGameWorkshop(
            appId = 480u,
            searchQuery = "",
            sortOption = WorkshopBrowseSortOption.LastUpdated,
            timeWindow = WorkshopBrowseTimeWindow.AllTime,
        )

        val request = server.takeRequest()
        val requestUrl = request.url
        assertThat(requestUrl.queryParameter("browsesort")).isEqualTo("lastupdated")
        assertThat(requestUrl.queryParameter("actualsort")).isEqualTo("lastupdated")
        assertThat(requestUrl.queryParameter("days")).isNull()
    }

    @Test
    fun browseGameWorkshop_usesConfiguredLanguagePreference() = runBlocking {
        repository = WorkshopBrowseRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            detailBaseUrl = server.url("/"),
            languagePreferenceProvider = { SteamLanguagePreference.English },
        )
        server.enqueue(mockResponse("<html></html>"))

        repository.browseGameWorkshop(
            appId = 480u,
            searchQuery = "",
        )

        val request = server.takeRequest()
        assertThat(request.url.queryParameter("l")).isEqualTo("english")
    }

    @Test
    fun browseGameWorkshop_enrichesItemsWithFileSize() = runBlocking {
        server.enqueue(
            mockResponse(
                """
                <div class="workshopItem">
                    <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=3677098410&searchtext=" class="ugc" data-appid="646570" data-publishedfileid="3677098410">
                        <div id="sharedfile_3677098410" class="workshopItemPreviewHolder ">
                            <img class="workshopItemPreviewImage " src="https://example.com/skip.png">
                        </div>
                    </a>
                    <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=3677098410&searchtext=" class="item_link"><div class="workshopItemTitle ellipsis">Skip The Spire</div></a>
                    <div class="workshopItemAuthorName ellipsis">by&nbsp;<a class="workshop_author_link" href="https://steamcommunity.com/id/test/myworkshopfiles/?appid=646570">apricity</a></div>
                </div>
                <script>
                    SharedFileBindMouseHover( "sharedfile_3677098410", false, {"id":"3677098410","title":"Skip The Spire","description":"A fun mod"} );
                </script>
                """.trimIndent(),
            ),
        )
        server.enqueue(
            mockResponse(
                """
                {
                  "response": {
                    "publishedfiledetails": [
                      {
                        "publishedfileid": "3677098410",
                        "file_size": "123456"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.browseGameWorkshop(
            appId = 646570u,
            searchQuery = "",
        )

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].fileSizeBytes).isEqualTo(123456L)

        val detailRequest = server.takeRequest()
        val fileSizeRequest = server.takeRequest()
        assertThat(detailRequest.url.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(fileSizeRequest.url.encodedPath).isEqualTo("/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
        assertThat(requireNotNull(fileSizeRequest.body).utf8()).contains("publishedfileids%5B0%5D=3677098410")
    }

    @Test
    fun browseGameWorkshop_returns_items_when_file_size_lookup_fails() = runBlocking {
        repository = WorkshopBrowseRepository(
            client = OkHttpClient(),
            detailClient = OkHttpClient.Builder()
                .addInterceptor { throw IOException("detail lookup failed") }
                .build(),
            baseUrl = server.url("/"),
            detailBaseUrl = server.url("/"),
        )
        server.enqueue(
            mockResponse(
                """
                <div class="workshopItem">
                    <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=3677098410&searchtext=" class="ugc" data-appid="646570" data-publishedfileid="3677098410">
                        <div id="sharedfile_3677098410" class="workshopItemPreviewHolder ">
                            <img class="workshopItemPreviewImage " src="https://example.com/skip.png">
                        </div>
                    </a>
                    <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=3677098410&searchtext=" class="item_link"><div class="workshopItemTitle ellipsis">Skip The Spire</div></a>
                    <div class="workshopItemAuthorName ellipsis">by&nbsp;<a class="workshop_author_link" href="https://steamcommunity.com/id/test/myworkshopfiles/?appid=646570">apricity</a></div>
                </div>
                """.trimIndent(),
            ),
        )

        val result = repository.browseGameWorkshop(
            appId = 646570u,
            searchQuery = "",
        )

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].fileSizeBytes).isNull()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun browseGameWorkshop_usesFileSizeFromSsrRenderContextWithoutDetailLookup() = runBlocking {
        val queryData = """
            {
              "mutations": [],
              "queries": [
                {
                  "state": {
                    "data": {
                      "public_data": {
                        "steamid": "76561198000000001",
                        "persona_name": "apricity"
                      }
                    }
                  },
                  "queryKey": ["PlayerLinkDetails", "76561198000000001"]
                },
                {
                  "state": {
                    "data": {
                      "current_page": 1,
                      "total_pages": 1,
                      "total_count": 1,
                      "results": [
                        {
                          "publishedfileid": "3677098410",
                          "creator": "76561198000000001",
                          "consumer_appid": 646570,
                          "preview_url": "https://example.com/skip.png",
                          "title": "Skip The Spire",
                          "short_description": "A fun mod",
                          "file_size": "123456"
                        }
                      ]
                    }
                  },
                  "queryKey": ["workshop_browse", 646570, "trend"]
                }
              ]
            }
        """.trimIndent()
        val renderContext = """{"queryData":${Json.encodeToString(queryData)}}"""
        server.enqueue(
            mockResponse(
                """
                <html>
                <head>
                    <script>window.SSR.renderContext=JSON.parse(${Json.encodeToString(renderContext)});</script>
                </head>
                <body></body>
                </html>
                """.trimIndent(),
            ),
        )

        val result = repository.browseGameWorkshop(
            appId = 646570u,
            searchQuery = "",
        )

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].fileSizeBytes).isEqualTo(123456L)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun createWorkshopBrowseDetailClient_uses_short_http1_profile() {
        val detailClient = createWorkshopBrowseDetailClient(OkHttpClient())

        assertThat(detailClient.connectTimeoutMillis).isEqualTo(5_000)
        assertThat(detailClient.readTimeoutMillis).isEqualTo(8_000)
        assertThat(detailClient.callTimeoutMillis).isEqualTo(8_000)
        assertThat(detailClient.protocols).containsExactly(Protocol.HTTP_1_1)
    }
}

private fun mockResponse(
    body: String,
    code: Int = 200,
): MockResponse =
    MockResponse.Builder()
        .code(code)
        .body(body)
        .build()
