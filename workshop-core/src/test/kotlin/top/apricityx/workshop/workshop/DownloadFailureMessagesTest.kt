package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadFailureMessagesTest {
    @Test
    fun `userVisibleDownloadFailureMessage prefers nested steam cdn 401 reason`() {
        val error = WorkshopDownloadException(
            "Unable to download UGC manifest",
            WorkshopDownloadException(
                "Steam CDN request exhausted retries: Steam CDN request failed: 401",
                WorkshopDownloadException("Steam CDN request failed: 401"),
            ),
        )

        assertThat(error.userVisibleDownloadFailureMessage()).isEqualTo("Steam CDN request failed: 401")
        assertThat(isSteamCdnUnauthorizedFailure(error.userVisibleDownloadFailureMessage())).isTrue()
    }

    @Test
    fun `userVisibleDownloadFailureMessage falls back to top level message when no nested 401 exists`() {
        val error = WorkshopDownloadException(
            "Failed to download chunk deadbeef",
            IllegalStateException("Chunk checksum mismatch"),
        )

        assertThat(error.userVisibleDownloadFailureMessage()).isEqualTo("Failed to download chunk deadbeef")
    }
}
