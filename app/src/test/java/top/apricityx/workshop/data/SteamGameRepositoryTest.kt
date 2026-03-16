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
            workshopBaseUrl = server.url("/"),
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

    @Test
    fun lookupGame_marksWorkshopSupportFromCommunityBrowsePage() = runBlocking {
        val repository = SteamGameRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            workshopBaseUrl = server.url("/"),
        )
        server.enqueue(
            mockResponse(
                """
                {
                  "262060": {
                    "success": true,
                    "data": {
                      "steam_appid": 262060,
                      "name": "Darkest Dungeon",
                      "short_description": "gothic roguelike",
                      "header_image": "https://example.com/header.jpg",
                      "capsule_imagev5": "https://example.com/capsule.jpg",
                      "categories": [{"id": 2}]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            mockResponse(
                """
                <html>
                  <head><title>Darkest Dungeon</title></head>
                  <body>Workshop browse page</body>
                </html>
                """.trimIndent(),
            ),
        )

        val game = repository.lookupGame(262060u)

        assertThat(game?.supportsWorkshop).isTrue()
        val detailsRequest = server.takeRequest()
        val browseRequest = server.takeRequest()
        assertThat(detailsRequest.url.encodedPath).isEqualTo("/api/appdetails")
        assertThat(browseRequest.url.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(browseRequest.url.queryParameter("appid")).isEqualTo("262060")
    }

    @Test
    fun lookupGame_keepsWorkshopDisabledWhenCommunityBrowseRedirectsToGenericHome() = runBlocking {
        val repository = SteamGameRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            workshopBaseUrl = server.url("/"),
        )
        server.enqueue(
            mockResponse(
                """
                {
                  "1145360": {
                    "success": true,
                    "data": {
                      "steam_appid": 1145360,
                      "name": "Hades",
                      "short_description": "roguelike action",
                      "header_image": "https://example.com/header.jpg",
                      "capsule_imagev5": "https://example.com/capsule.jpg",
                      "categories": [{"id": 2}]
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", "/workshop/")
                .build(),
        )
        server.enqueue(
            mockResponse(
                """
                <html>
                  <head><title>Steam Community :: Steam Workshop</title></head>
                  <body>Generic workshop home</body>
                </html>
                """.trimIndent(),
            ),
        )

        val game = repository.lookupGame(1145360u)

        assertThat(game?.supportsWorkshop).isFalse()
        val detailsRequest = server.takeRequest()
        val browseRequest = server.takeRequest()
        val redirectedRequest = server.takeRequest()
        assertThat(detailsRequest.url.encodedPath).isEqualTo("/api/appdetails")
        assertThat(browseRequest.url.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(redirectedRequest.url.encodedPath).isEqualTo("/workshop/")
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
