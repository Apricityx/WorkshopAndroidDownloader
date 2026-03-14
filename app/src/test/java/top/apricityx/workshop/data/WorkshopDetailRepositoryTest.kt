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

class WorkshopDetailRepositoryTest {
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
    fun loadWorkshopItemDetail_prefersLocalizedCommunityContent() = runBlocking {
        val repository = WorkshopDetailRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            communityBaseUrl = server.url("/"),
            languagePreferenceProvider = { SteamLanguagePreference.SimplifiedChinese },
        )
        server.enqueue(
            mockResponse(
                """
                {
                  "response": {
                    "publishedfiledetails": [
                      {
                        "publishedfileid": "3680514339",
                        "title": "Controller Vibration Support",
                        "description": "[h1]Controller Vibration Support[/h1]",
                        "preview_url": "https://example.com/full.png",
                        "file_size": "45426",
                        "time_updated": "1772900923",
                        "subscriptions": "307",
                        "favorited": "14",
                        "views": "1155",
                        "tags": [{"tag": "Utility"}]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            mockResponse(
                """
                <html>
                    <div class="workshopItemTitle">手柄振动支持</div>
                    <div class="workshopItemDescription" id="highlightContent"><div class="bb_h1">手柄振动支持</div><br>中文说明<ul class="bb_ul"><li>第一项</li><li>第二项</li></ul></div>
                </html>
                """.trimIndent(),
            ),
        )

        val result = repository.loadWorkshopItemDetail(
            WorkshopBrowseItem(
                appId = 646570u,
                publishedFileId = 3680514339uL,
                title = "手柄振动支持",
                authorName = "Apricityx_",
                previewImageUrl = "https://example.com/thumb.png",
                descriptionSnippet = "中文摘要",
            ),
        )

        assertThat(result.title).isEqualTo("手柄振动支持")
        assertThat(result.description).contains("中文说明")
        assertThat(result.description).contains("第一项")
        assertThat(result.workshopUrl).contains("l=schinese")

        val apiRequest = server.takeRequest()
        val communityRequest = server.takeRequest()
        assertThat(apiRequest.url.encodedPath).isEqualTo("/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
        assertThat(communityRequest.url.encodedPath).isEqualTo("/sharedfiles/filedetails/")
        assertThat(communityRequest.url.queryParameter("l")).isEqualTo("schinese")
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
