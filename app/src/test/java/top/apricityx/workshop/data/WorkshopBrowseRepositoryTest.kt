package top.apricityx.workshop.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
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
        server.shutdown()
    }

    @Test
    fun browseGameWorkshop_includesSelectedPopularWindowInRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))

        repository.browseGameWorkshop(
            appId = 646570u,
            searchQuery = "spire",
            sortOption = WorkshopBrowseSortOption.MostPopular,
            timeWindow = WorkshopBrowseTimeWindow.ThirtyDays,
            page = 3,
        )

        val request = server.takeRequest()
        val requestUrl = request.requestUrl
        assertThat(requestUrl?.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(requestUrl?.queryParameter("appid")).isEqualTo("646570")
        assertThat(requestUrl?.queryParameter("searchtext")).isEqualTo("spire")
        assertThat(requestUrl?.queryParameter("browsesort")).isEqualTo("trend")
        assertThat(requestUrl?.queryParameter("actualsort")).isEqualTo("trend")
        assertThat(requestUrl?.queryParameter("days")).isEqualTo("30")
        assertThat(requestUrl?.queryParameter("p")).isEqualTo("3")
    }

    @Test
    fun browseGameWorkshop_omitsDaysForNonPopularSort() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))

        repository.browseGameWorkshop(
            appId = 480u,
            searchQuery = "",
            sortOption = WorkshopBrowseSortOption.LastUpdated,
            timeWindow = WorkshopBrowseTimeWindow.AllTime,
        )

        val request = server.takeRequest()
        val requestUrl = request.requestUrl
        assertThat(requestUrl?.queryParameter("browsesort")).isEqualTo("lastupdated")
        assertThat(requestUrl?.queryParameter("actualsort")).isEqualTo("lastupdated")
        assertThat(requestUrl?.queryParameter("days")).isNull()
    }

    @Test
    fun browseGameWorkshop_enrichesItemsWithFileSize() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
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
            MockResponse().setResponseCode(200).setBody(
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
        assertThat(detailRequest.requestUrl?.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(fileSizeRequest.requestUrl?.encodedPath).isEqualTo("/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
        assertThat(fileSizeRequest.body.readUtf8()).contains("publishedfileids%5B0%5D=3677098410")
    }
}
