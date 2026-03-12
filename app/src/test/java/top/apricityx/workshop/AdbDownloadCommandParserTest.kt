package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdbDownloadCommandParserTest {
    @Test
    fun `custom download action auto starts`() {
        val command = AdbDownloadCommandParser.parse(
            action = AdbDownloadCommandParser.actionDownload,
            extras = mapOf(
                "app_id" to "646570",
                "published_file_id" to "3677098410",
            ),
        )

        assertThat(command).isNotNull()
        assertThat(command?.appIdText).isEqualTo("646570")
        assertThat(command?.publishedFileIdText).isEqualTo("3677098410")
        assertThat(command?.autoStart).isTrue()
    }

    @Test
    fun `aliases and numeric extras are accepted`() {
        val command = AdbDownloadCommandParser.parse(
            action = "android.intent.action.MAIN",
            extras = mapOf(
                "appid" to 480,
                "publishedFieldId" to 1234567890L,
                "auto_start" to 1,
            ),
        )

        assertThat(command).isNotNull()
        assertThat(command?.appIdText).isEqualTo("480")
        assertThat(command?.publishedFileIdText).isEqualTo("1234567890")
        assertThat(command?.autoStart).isTrue()
    }

    @Test
    fun `missing download extras returns null`() {
        val command = AdbDownloadCommandParser.parse(
            action = "android.intent.action.MAIN",
            extras = emptyMap(),
        )

        assertThat(command).isNull()
    }
}
