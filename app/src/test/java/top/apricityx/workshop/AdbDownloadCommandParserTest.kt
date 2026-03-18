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

        assertThat(command).isInstanceOf(AdbDownloadCommand::class.java)
        val downloadCommand = command as AdbDownloadCommand
        assertThat(downloadCommand.appIdText).isEqualTo("646570")
        assertThat(downloadCommand.publishedFileIdText).isEqualTo("3677098410")
        assertThat(downloadCommand.autoStart).isTrue()
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

        assertThat(command).isInstanceOf(AdbDownloadCommand::class.java)
        val downloadCommand = command as AdbDownloadCommand
        assertThat(downloadCommand.appIdText).isEqualTo("480")
        assertThat(downloadCommand.publishedFileIdText).isEqualTo("1234567890")
        assertThat(downloadCommand.autoStart).isTrue()
    }

    @Test
    fun `missing download extras returns null`() {
        val command = AdbDownloadCommandParser.parse(
            action = "android.intent.action.MAIN",
            extras = emptyMap(),
        )

        assertThat(command).isNull()
    }

    @Test
    fun `search probe extras parse into probe command`() {
        val command = AdbDownloadCommandParser.parse(
            action = AdbDownloadCommandParser.actionWorkshopSearchProbe,
            extras = mapOf(
                "app_id" to "646570",
                "search_query" to "nsfw",
                "expected_published_file_id" to "3367459929",
            ),
        )

        assertThat(command).isInstanceOf(AdbWorkshopSearchProbeCommand::class.java)
        val probeCommand = command as AdbWorkshopSearchProbeCommand
        assertThat(probeCommand.appIdText).isEqualTo("646570")
        assertThat(probeCommand.searchQuery).isEqualTo("nsfw")
        assertThat(probeCommand.expectedPublishedFileIdText).isEqualTo("3367459929")
    }
}
