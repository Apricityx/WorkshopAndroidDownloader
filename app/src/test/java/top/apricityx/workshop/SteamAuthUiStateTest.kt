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
    fun `accountListItems shows anonymous as current account when no Steam account is active`() {
        val items = SteamAccountsSnapshot().toUiState().accountListItems()

        assertThat(items).hasSize(1)
        assertThat(items.first().accountName).isEqualTo("匿名")
        assertThat(items.first().isActive).isTrue()
        assertThat(items.first().statusText).isEqualTo("当前浏览账号")
        assertThat(items.first().canManage).isFalse()
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

    @Test
    fun `accountListItems keeps anonymous switchable when another account is active`() {
        val items = SteamAccountsSnapshot(
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
        ).toUiState().accountListItems()

        assertThat(items).hasSize(2)
        assertThat(items[0].accountName).isEqualTo("匿名")
        assertThat(items[0].isActive).isFalse()
        assertThat(items[0].statusText).isEqualTo("匿名浏览")
        assertThat(items[0].canManage).isFalse()
        assertThat(items[1].accountName).isEqualTo("Ready")
        assertThat(items[1].isActive).isTrue()
        assertThat(items[1].canManage).isTrue()
    }
}
