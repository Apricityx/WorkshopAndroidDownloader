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
}
