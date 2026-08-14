package top.apricityx.workshop.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Test

class WorkshopBrowseParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parse_extractsWorkshopItemsAndDescriptions() {
        val payload = """
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
            <a class='pagebtn' href="https://steamcommunity.com/workshop/browse/?appid=646570&p=2">&gt;</a>
        """.trimIndent()

        val page = WorkshopBrowseParser.parse(payload, page = 1, json = json)

        assertThat(page.page).isEqualTo(1)
        assertThat(page.hasNextPage).isTrue()
        assertThat(page.items).hasSize(1)
        assertThat(page.items[0].publishedFileId).isEqualTo(3677098410uL)
        assertThat(page.items[0].title).isEqualTo("Skip The Spire")
        assertThat(page.items[0].authorName).isEqualTo("apricity")
        assertThat(page.items[0].descriptionSnippet).isEqualTo("A fun mod")
    }

    @Test
    fun parse_extractsWorkshopItemsFromSimplifiedChineseMarkup() {
        val payload = """
            <div data-panel="{&quot;type&quot;:&quot;PanelGroup&quot;}" class="workshopItem">
                <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=3680514339&searchtext=" class="ugc" data-appid="646570" data-publishedfileid="3680514339">
                    <div id="sharedfile_3680514339" class="workshopItemPreviewHolder ">
                        <img class="workshopItemPreviewImage " src="https://example.com/vibration.png">
                    </div>
                </a>
                <a data-panel="{&quot;focusable&quot;:false}" href="https://steamcommunity.com/sharedfiles/filedetails/?id=3680514339&searchtext=" class="item_link"><div class="workshopItemTitle ellipsis">手柄振动支持</div></a>
                <div class="workshopItemAuthorName ellipsis">作者：&nbsp;<a class="workshop_author_link" href="https://steamcommunity.com/profiles/76561198883607238/myworkshopfiles/?appid=646570">Apricityx_</a></div>
                <div style="clear: both"></div>
            </div>
            <script>
                SharedFileBindMouseHover( "sharedfile_3680514339", false, {"id":"3680514339","title":"\u624b\u67c4\u632f\u52a8\u652f\u6301","description":"\u4e2d\u6587\u63cf\u8ff0"} );
            </script>
        """.trimIndent()

        val page = WorkshopBrowseParser.parse(payload, page = 1, json = json)

        assertThat(page.items).hasSize(1)
        assertThat(page.items[0].publishedFileId).isEqualTo(3680514339uL)
        assertThat(page.items[0].title).isEqualTo("手柄振动支持")
        assertThat(page.items[0].authorName).isEqualTo("Apricityx_")
        assertThat(page.items[0].descriptionSnippet).isEqualTo("中文描述")
    }

    @Test
    fun parse_extractsWorkshopItemsFromSsrRenderContext() {
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
                      "current_page": 2,
                      "total_pages": 4,
                      "total_count": 120,
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
        val payload = """
            <html>
            <head>
                <script>window.SSR.renderContext=JSON.parse(${Json.encodeToString(renderContext)});</script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val page = WorkshopBrowseParser.parse(payload, page = 1, json = json)

        assertThat(page.page).isEqualTo(2)
        assertThat(page.hasNextPage).isTrue()
        assertThat(page.items).hasSize(1)
        assertThat(page.items[0].publishedFileId).isEqualTo(3677098410uL)
        assertThat(page.items[0].title).isEqualTo("Skip The Spire")
        assertThat(page.items[0].authorName).isEqualTo("apricity")
        assertThat(page.items[0].descriptionSnippet).isEqualTo("A fun mod")
        assertThat(page.items[0].fileSizeBytes).isEqualTo(123456L)
    }

    @Test
    fun parse_ignoresNonObjectQueryEntriesInSsrRenderContext() {
        val queryData = """
            {
              "mutations": [],
              "queries": [
                [],
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
                      "results": [
                        {
                          "publishedfileid": "3677098410",
                          "creator": "76561198000000001",
                          "consumer_appid": 646570,
                          "title": "Skip The Spire",
                          "short_description": "A fun mod"
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
        val payload = """
            <html>
            <head>
                <script>window.SSR.renderContext=JSON.parse(${Json.encodeToString(renderContext)});</script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val page = WorkshopBrowseParser.parse(payload, page = 1, json = json)

        assertThat(page.items).hasSize(1)
        assertThat(page.items[0].title).isEqualTo("Skip The Spire")
        assertThat(page.items[0].authorName).isEqualTo("apricity")
    }

    @Test
    fun parseSsrRenderContextWhenNestedDataContainsJsonParseTerminator() {
        val queryData = """
            {
              "mutations": [],
              "queries": [
                {
                  "state": {
                    "data": {
                      "current_page": 1,
                      "total_pages": 2,
                      "results": [
                        {
                          "publishedfileid": "3770021703",
                          "creator": "76561198000000001",
                          "consumer_appid": 646570,
                          "preview_url": "https://example.com/preview.png",
                          "title": "Act 3 Boss Chest",
                          "short_description": "Contains the marker \\\"example.webm\\\"); inside text"
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
        val payload = "window.SSR.renderContext=JSON.parse(${Json.encodeToString(renderContext)});"

        val page = WorkshopBrowseParser.parse(payload, page = 1, json = json)

        assertThat(page.items).hasSize(1)
        assertThat(page.items[0].publishedFileId).isEqualTo(3770021703uL)
        assertThat(page.items[0].title).isEqualTo("Act 3 Boss Chest")
        assertThat(page.items[0].descriptionSnippet).contains("example.webm")
    }
}
