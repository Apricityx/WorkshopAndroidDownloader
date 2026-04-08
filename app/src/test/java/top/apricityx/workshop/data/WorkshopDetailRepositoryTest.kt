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
                    <script type="text/javascript">
                        var g_sessionID = "session-1";
                        InitializeCommentThread(
                            "PublishedFile_Public",
                            "PublishedFile_Public_123_3680514339",
                            {"feature":"3680514339","feature2":-1,"owner":"76561198000000001","total_count":12,"start":0,"pagesize":10,"extended_data":"{\"appid\":646570}"},
                            'https://steamcommunity.com/comment/PublishedFile_Public/',
                            40
                        );
                    </script>
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
        assertThat(result.commentsUrl).contains("/comments/3680514339")
        assertThat(result.commentsUrl).contains("l=schinese")
        assertThat(result.commentCount).isEqualTo(12)
        assertThat(result.commentPage).isEqualTo(1)
        assertThat(result.commentTotalPages).isEqualTo(3)
        assertThat(result.hasPreviousCommentPage).isFalse()
        assertThat(result.hasNextCommentPage).isTrue()
        assertThat(result.comments).isEmpty()
        assertThat(result.commentThreadContext).isNotNull()
        assertThat(result.commentThreadContext?.ownerId).isEqualTo("76561198000000001")
        assertThat(result.commentThreadContext?.featureId).isEqualTo("3680514339")
        assertThat(result.commentThreadContext?.sessionId).isEqualTo("session-1")

        val apiRequest = server.takeRequest()
        val communityRequest = server.takeRequest()
        assertThat(apiRequest.url.encodedPath).isEqualTo("/ISteamRemoteStorage/GetPublishedFileDetails/v1/")
        assertThat(communityRequest.url.encodedPath).isEqualTo("/sharedfiles/filedetails/")
        assertThat(communityRequest.url.queryParameter("l")).isEqualTo("schinese")
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
                    <script type="text/javascript">
                        var g_sessionID = "session-2";
                        InitializeCommentThread(
                            "PublishedFile_Public",
                            "PublishedFile_Public_123_519359552",
                            {"feature":"519359552","feature2":-1,"owner":"76561198000000002","total_count":0,"start":0,"pagesize":10,"extended_data":"{\"appid\":255710}"},
                            'https://steamcommunity.com/comment/PublishedFile_Public/',
                            40
                        );
                    </script>
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
        assertThat(result.commentPage).isEqualTo(1)
        assertThat(result.commentTotalPages).isEqualTo(1)
        assertThat(result.hasPreviousCommentPage).isFalse()
        assertThat(result.hasNextCommentPage).isFalse()
        assertThat(result.comments).isEmpty()
    }

    @Test
    fun loadWorkshopCommentPage_supportsPagination() = runBlocking {
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
                  "success": true,
                  "start": 5,
                  "pagesize": "5",
                  "total_count": 1639,
                  "comments_html": "<div class=\"commentthread_comment responsive_body_text\" id=\"comment_2001\"><div class=\"commentthread_comment_content\"><div class=\"commentthread_comment_author\"><a class=\"hoverunderline commentthread_author_link\" href=\"https://steamcommunity.com/profiles/76561198000000002\"><bdi>Second Page User</bdi></a><span class=\"commentthread_comment_timestamp\" data-timestamp=\"1775238905\">3 Apr, 2026 @ 10:55am</span></div><div class=\"commentthread_comment_text\" id=\"comment_content_2001\">page 2 comment</div></div></div>"
                }
                """.trimIndent(),
            ),
        )

        val result = repository.loadWorkshopCommentPage(
            detail = WorkshopItemDetail(
                appId = 4000u,
                publishedFileId = 973145750uL,
                title = "Addon Share",
                authorName = "OriginalAuthor",
                previewImageUrl = "https://example.com/thumb.png",
                description = "Description",
                fileSizeBytes = null,
                timeUpdatedEpochSeconds = null,
                subscriptions = null,
                favorited = null,
                views = null,
                tags = emptyList(),
                workshopUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=973145750&l=english",
                commentsUrl = "https://steamcommunity.com/sharedfiles/filedetails/comments/973145750?l=english",
                commentThreadContext = WorkshopCommentThreadContext(
                    ownerId = "76561198088859981",
                    featureId = "973145750",
                    extendedData = "{\"appid\":4000}",
                    sessionId = "session-3",
                ),
            ),
            page = 2,
        )

        assertThat(result.commentCount).isEqualTo(1639)
        assertThat(result.page).isEqualTo(2)
        assertThat(result.totalPages).isEqualTo(328)
        assertThat(result.hasPreviousPage).isTrue()
        assertThat(result.hasNextPage).isTrue()
        assertThat(result.commentsUrl).isEqualTo("https://steamcommunity.com/sharedfiles/filedetails/comments/973145750?l=english")
        assertThat(result.comments).hasSize(1)
        assertThat(result.comments[0].content).isEqualTo("page 2 comment")

        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/comment/PublishedFile_Public/render/76561198088859981/973145750/")
        assertThat(request.url.queryParameter("l")).isEqualTo("english")
        val requestBody = requireNotNull(request.body).utf8()
        assertThat(requestBody).contains("count=5")
        assertThat(requestBody).contains("start=5")
        assertThat(requestBody).contains("sessionid=session-3")
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
