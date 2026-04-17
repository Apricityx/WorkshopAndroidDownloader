package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadFailureMessageFormatterTest {
    @Test
    fun steamCdn401_withoutBoundAccount_usesGenericLoginHint() {
        val message = formatDownloadFailureMessage(
            rawMessage = "Steam CDN request failed: 401",
            gameTitle = "Slay the Spire",
            hasBoundAccount = false,
            ownershipStatus = SteamAppOwnershipStatus.Unknown,
        )

        assertThat(message).isEqualTo("Steam CDN 返回 401，该内容可能需要登录购买过游戏的 Steam 账号才能下载。")
    }

    @Test
    fun steamCdn401_forNotOwnedGame_mentionsMissingOwnership() {
        val message = formatDownloadFailureMessage(
            rawMessage = "Steam CDN request exhausted retries: Steam CDN request failed: 401",
            gameTitle = "Wallpaper Engine",
            hasBoundAccount = true,
            ownershipStatus = SteamAppOwnershipStatus.NotOwned,
        )

        assertThat(message).isEqualTo(
            "Steam CDN 返回 401，当前绑定账号未检测到拥有《Wallpaper Engine》，该内容可能需要登录购买过游戏的 Steam 账号才能下载。",
        )
    }

    @Test
    fun non401Failure_keepsOriginalMessage() {
        val message = formatDownloadFailureMessage(
            rawMessage = "Failed to download chunk deadbeef",
            gameTitle = "Wallpaper Engine",
            hasBoundAccount = true,
            ownershipStatus = SteamAppOwnershipStatus.NotOwned,
        )

        assertThat(message).isEqualTo("Failed to download chunk deadbeef")
    }
}
