package top.apricityx.workshop.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import top.apricityx.workshop.SteamLanguagePreference

class SteamGameRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun searchWorkshopGames_defaultsToSimplifiedChinese() = runBlocking {
        val repository = SteamGameRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
        )
        server.enqueue(
            mockResponse(
                """
                <a data-ds-appid="646570"><div class="match_name">Slay the Spire</div></a>
                """.trimIndent(),
            ),
        )
        server.enqueue(
            mockResponse(
                """
                {
                  "646570": {
                    "success": true,
                    "data": {
                      "steam_appid": 646570,
                      "name": "Slay the Spire",
                      "short_description": "deckbuilding",
                      "header_image": "https://example.com/header.jpg",
                      "capsule_imagev5": "https://example.com/capsule.jpg",
                      "categories": [{"id": 30}]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        repository.searchWorkshopGames("slay")

        val searchRequest = server.takeRequest()
        val detailsRequest = server.takeRequest()
        assertThat(searchRequest.url.encodedPath).isEqualTo("/search/suggest")
        assertThat(searchRequest.url.queryParameter("l")).isEqualTo("schinese")
        assertThat(detailsRequest.url.encodedPath).isEqualTo("/api/appdetails")
        assertThat(detailsRequest.url.queryParameter("l")).isEqualTo("schinese")
    }

    @Test
    fun searchWorkshopGames_usesConfiguredLanguagePreference() = runBlocking {
        val repository = SteamGameRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            languagePreferenceProvider = { SteamLanguagePreference.English },
        )
        server.enqueue(
            mockResponse(
                """
                <a data-ds-appid="646570"><div class="match_name">Slay the Spire</div></a>
                """.trimIndent(),
            ),
        )
        server.enqueue(
            mockResponse(
                """
                {
                  "646570": {
                    "success": true,
                    "data": {
                      "steam_appid": 646570,
                      "name": "Slay the Spire",
                      "short_description": "deckbuilding",
                      "header_image": "https://example.com/header.jpg",
                      "capsule_imagev5": "https://example.com/capsule.jpg",
                      "categories": [{"id": 30}]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        repository.searchWorkshopGames("slay")

        val searchRequest = server.takeRequest()
        assertThat(searchRequest.url.queryParameter("l")).isEqualTo("english")
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
