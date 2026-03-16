package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.apricityx.workshop.steam.protocol.SteamGuardChallengeType

class SteamLoginDialogUiStateTest {
    @Test
    fun `waiting for Steam confirmation still allows switching to token login`() {
        val state = SteamLoginDialogUiState(
            challengeType = SteamGuardChallengeType.DeviceConfirmation,
            isPollingConfirmation = true,
        )

        assertThat(state.canSwitchSteamLoginInputMode()).isTrue()
    }

    @Test
    fun `guard code challenge does not show token switch`() {
        val state = SteamLoginDialogUiState(
            challengeType = SteamGuardChallengeType.DeviceCode,
        )

        assertThat(state.canSwitchSteamLoginInputMode()).isFalse()
    }

    @Test
    fun `token mode can always switch back to credential login when idle`() {
        val state = SteamLoginDialogUiState(
            inputMode = SteamLoginInputMode.RefreshToken,
        )

        assertThat(state.canSwitchSteamLoginInputMode()).isTrue()
    }
}
