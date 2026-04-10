package top.apricityx.workshop.steam.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.apricityx.workshop.steam.proto.EAuthTokenPlatformType
import top.apricityx.workshop.steam.proto.ESessionPersistence

class SteamAuthenticationClientRequestTest {
    @Test
    fun `buildBeginAuthSessionRequest matches SteamKit credentials shape`() {
        val request = buildBeginAuthSessionRequest(
            details = SteamAuthSessionDetails(
                username = "demo_user",
                password = "ignored",
                guardData = "guard-token",
                isPersistentSession = true,
                deviceFriendlyName = "Android Workshop",
                websiteId = "Client",
                clientOsType = -500,
            ),
            encryptedPassword = "ciphertext",
            encryptionTimestamp = 123456789L,
        )

        assertThat(request.accountName).isEqualTo("demo_user")
        assertThat(request.encryptedPassword).isEqualTo("ciphertext")
        assertThat(request.encryptionTimestamp).isEqualTo(123456789L)
        assertThat(request.persistence).isEqualTo(ESessionPersistence.k_ESessionPersistence_Persistent)
        assertThat(request.websiteId).isEqualTo("Client")
        assertThat(request.guardData).isEqualTo("guard-token")
        assertThat(request.hasDeviceFriendlyName()).isFalse()
        assertThat(request.hasRememberLogin()).isFalse()
        assertThat(request.hasPlatformType()).isFalse()
        assertThat(request.hasQosLevel()).isFalse()
        assertThat(request.deviceDetails.deviceFriendlyName).isEqualTo("Android Workshop")
        assertThat(request.deviceDetails.platformType).isEqualTo(EAuthTokenPlatformType.k_EAuthTokenPlatformType_SteamClient)
        assertThat(request.deviceDetails.osType).isEqualTo(-500)
        assertThat(request.deviceDetails.hasMachineId()).isFalse()
    }

    @Test
    fun `buildBeginAuthSessionRequest omits blank guard data and supports ephemeral sessions`() {
        val request = buildBeginAuthSessionRequest(
            details = SteamAuthSessionDetails(
                username = "demo_user",
                password = "ignored",
                guardData = "",
                isPersistentSession = false,
            ),
            encryptedPassword = "ciphertext",
            encryptionTimestamp = 1L,
        )

        assertThat(request.persistence).isEqualTo(ESessionPersistence.k_ESessionPersistence_Ephemeral)
        assertThat(request.hasGuardData()).isFalse()
    }
}
