package top.apricityx.workshop.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
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
}
