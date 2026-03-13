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
}
