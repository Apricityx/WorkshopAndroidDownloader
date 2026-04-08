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
        server.enqueue(
            mockResponse(
                """
                <html>
                    <script type="text/javascript">
                        InitializeCommentThread(
                            "PublishedFile_Public",
                            "PublishedFile_Public_123_3680514339",
                            {"total_count":12,"start":0,"pagesize":50},
                            "https://steamcommunity.com/comment/PublishedFile_Public/",
                            40
                        );
                    </script>
                    <div class="commentthread_area" id="commentthread_PublishedFile_Public_123_3680514339_area">
                        <div class="commentthread_comment responsive_body_text" id="comment_1001">
                            <div class="commentthread_comment_content">
                                <div class="commentthread_comment_author">
                                    <a class="hoverunderline commentthread_author_link" href="https://steamcommunity.com/profiles/76561198000000001">
                                        <bdi>测试玩家</bdi>
                                    </a>
                                    <span class="commentthread_comment_timestamp" data-timestamp="1772901000">
                                        2026 年 3 月 6 日 12:30
                                    </span>
                                </div>
                                <div class="commentthread_comment_text" id="comment_content_1001">
                                    真好用<img src="https://community.fastly.steamstatic.com/economy/emoticon/steamthumbsup" alt=":steamthumbsup:" class="emoticon"><br>谢谢作者
                                </div>
                            </div>
                        </div>
                    </div>
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
        assertThat(result.commentsUrl).contains("/comments/3680514339")
        assertThat(result.commentsUrl).contains("l=schinese")
        assertThat(result.commentCount).isEqualTo(12)
        assertThat(result.comments).hasSize(1)
        assertThat(result.comments[0].authorName).isEqualTo("测试玩家")
        assertThat(result.comments[0].content).contains("真好用")
        assertThat(result.comments[0].content).contains(":steamthumbsup:")
        assertThat(result.comments[0].content).contains("谢谢作者")
        assertThat(result.comments[0].postedEpochSeconds).isEqualTo(1772901000)

        val apiRequest = server.takeRequest()
        val communityRequest = server.takeRequest()
        val commentsRequest = server.takeRequest()
        assertThat(apiRequest.url.encodedPath).isEqualTo("/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
        assertThat(communityRequest.url.encodedPath).isEqualTo("/sharedfiles/filedetails/")
        assertThat(communityRequest.url.queryParameter("l")).isEqualTo("schinese")
        assertThat(commentsRequest.url.encodedPath).isEqualTo("/sharedfiles/filedetails/comments/3680514339")
        assertThat(commentsRequest.url.queryParameter("l")).isEqualTo("schinese")
    }

    @Test
    fun loadWorkshopItemDetail_parsesRequiredItemsFromCommunityPage() = runBlocking {
        val repository = WorkshopDetailRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            communityBaseUrl = server.url("/"),
            languagePreferenceProvider = { SteamLanguagePreference.English },
        )
        server.enqueue(
            mockResponse(
                """
                {
                  "response": {
                    "publishedfiledetails": [
                      {
                        "publishedfileid": "519359552",
                        "title": "World Trade Center - Part 10 of 11",
                        "description": "Description",
                        "preview_url": "https://example.com/full.png",
                        "file_size": "9840019",
                        "time_updated": "1466727569",
                        "subscriptions": "14412",
                        "favorited": "522",
                        "views": "15701",
                        "tags": [{"tag": "Building"}]
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
                    <div class="workshopItemTitle">World Trade Center - Part 10 of 11</div>
                    <div class="workshopItemDescription" id="highlightContent">Description</div>
                    <div class="requiredItemsContainer" id="RequiredItems">
                        <a href="https://steamcommunity.com/workshop/filedetails/?id=519353802" target="_blank" data-subscribed="0">
                            <div class="requiredItem">World Trade Center - Part 1 of 11</div>
                        </a>
                        <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=519354649&amp;searchtext=" target="_blank" data-subscribed="0">
                            <div class="requiredItem">World Trade Center - Part 2 of 11</div>
                        </a>
                    </div>
                </html>
                """.trimIndent(),
            ),
        )
        server.enqueue(
            mockResponse(
                """
                <html>
                    <div class="commentthread_header">
                        <span class="ellipsis commentthread_count_label">
                            <span id="commentthread_PublishedFile_Public_123_519359552_totalcount">0</span> Comments
                        </span>
                    </div>
                </html>
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
                        "publishedfileid": "519353802",
                        "consumer_app_id": 255710,
                        "title": "World Trade Center - Part 1 of 11",
                        "description": "[b]Office tower[/b]",
                        "preview_url": "https://example.com/required-1.png"
                      },
                      {
                        "publishedfileid": "519354649",
                        "consumer_app_id": 255710,
                        "title": "World Trade Center - Part 2 of 11",
                        "description": "[b]Connector building[/b]",
                        "preview_url": "https://example.com/required-2.png"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.loadWorkshopItemDetail(
            WorkshopBrowseItem(
                appId = 255710u,
                publishedFileId = 519359552uL,
                title = "World Trade Center - Part 10 of 11",
                authorName = "BoldlyBuilding",
                previewImageUrl = "https://example.com/thumb.png",
                descriptionSnippet = "Description",
            ),
        )

        assertThat(result.requiredItems).hasSize(2)
        assertThat(result.requiredItems[0].appId).isEqualTo(255710u)
        assertThat(result.requiredItems[0].publishedFileId).isEqualTo(519353802uL)
        assertThat(result.requiredItems[0].title).isEqualTo("World Trade Center - Part 1 of 11")
        assertThat(result.requiredItems[0].previewImageUrl).isEqualTo("https://example.com/required-1.png")
        assertThat(result.requiredItems[0].descriptionSnippet).isEqualTo("Office tower")
        assertThat(result.requiredItems[1].publishedFileId).isEqualTo(519354649uL)
        assertThat(result.requiredItems[1].title).isEqualTo("World Trade Center - Part 2 of 11")
        assertThat(result.requiredItems[1].previewImageUrl).isEqualTo("https://example.com/required-2.png")
        assertThat(result.requiredItems[1].descriptionSnippet).isEqualTo("Connector building")
        assertThat(result.requiredItems[1].workshopUrl)
            .isEqualTo("https://steamcommunity.com/sharedfiles/filedetails/?id=519354649&searchtext=")
        assertThat(result.commentCount).isEqualTo(0)
        assertThat(result.comments).isEmpty()
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
