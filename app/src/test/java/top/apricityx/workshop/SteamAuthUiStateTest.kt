package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SteamAuthUiStateTest {
    @Test
    fun `toUiState marks anonymous browsing when there is no active account`() {
        val uiState = SteamAccountsSnapshot().toUiState()

        assertThat(uiState.isBrowsingUnauthenticated).isTrue()
    }

    @Test
    fun `toUiState marks anonymous browsing when active account needs reauthentication`() {
        val uiState = SteamAccountsSnapshot(
            accounts = listOf(
                SteamAccountSummary(
                    accountId = "account-1",
                    accountName = "Need Login",
                    steamId = 76561198000000001,
                    isActive = true,
                    requiresReauthentication = true,
                ),
            ),
            activeAccountId = "account-1",
        ).toUiState()

        assertThat(uiState.isBrowsingUnauthenticated).isTrue()
        assertThat(uiState.statusSummary).contains("需要重新认证")
    }

    @Test
    fun `toUiState marks authenticated browsing for healthy active account`() {
        val uiState = SteamAccountsSnapshot(
            accounts = listOf(
                SteamAccountSummary(
                    accountId = "account-1",
                    accountName = "Ready",
                    steamId = 76561198000000002,
                    isActive = true,
                    requiresReauthentication = false,
                ),
            ),
            activeAccountId = "account-1",
        ).toUiState()

        assertThat(uiState.isBrowsingUnauthenticated).isFalse()
        assertThat(uiState.statusSummary).contains("工坊浏览会自动投影 Steam 登录态")
    }
}
