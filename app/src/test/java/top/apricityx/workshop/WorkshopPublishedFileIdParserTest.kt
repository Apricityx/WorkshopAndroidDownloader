package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopPublishedFileIdParserTest {
    @Test
    fun `raw published id is accepted`() {
        assertThat(WorkshopPublishedFileIdParser.parse("3657277146")).isEqualTo(3657277146uL)
    }

    @Test
    fun `sharedfiles detail url is accepted`() {
        assertThat(
            WorkshopPublishedFileIdParser.parse(
                "https://steamcommunity.com/sharedfiles/filedetails/?id=3657277146&searchtext=slay",
            ),
        ).isEqualTo(3657277146uL)
    }

    @Test
    fun `workshop detail url without scheme is accepted`() {
        assertThat(
            WorkshopPublishedFileIdParser.parse(
                "steamcommunity.com/workshop/filedetails/?id=3657277146",
            ),
        ).isEqualTo(3657277146uL)
    }

    @Test
    fun `invalid url is rejected`() {
        assertThat(
            WorkshopPublishedFileIdParser.parse(
                "https://example.com/sharedfiles/filedetails/?id=3657277146",
            ),
        ).isNull()
    }

    @Test
    fun `zero id is rejected`() {
        assertThat(WorkshopPublishedFileIdParser.parse("0")).isNull()
    }
}
