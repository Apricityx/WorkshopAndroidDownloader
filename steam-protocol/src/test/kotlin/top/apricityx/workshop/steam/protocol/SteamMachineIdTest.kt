package top.apricityx.workshop.steam.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.MessageDigest

class SteamMachineIdTest {
    @Test
    fun `buildSteamMachineId writes steam style message object fields`() {
        val machineId = buildSteamMachineId(
            machineGuidSource = "guid-source".toByteArray(),
            macAddressSource = "mac-source".toByteArray(),
            diskIdSource = "disk-source".toByteArray(),
        )
        val payload = machineId.toString(Charsets.UTF_8)

        assertThat(machineId.first().toInt() and 0xFF).isEqualTo(0)
        assertThat(machineId[machineId.lastIndex - 1]).isEqualTo(8.toByte())
        assertThat(machineId.last()).isEqualTo(8.toByte())
        assertThat(payload).contains("MessageObject\u0000")
        assertThat(payload).contains("BB3\u0000${sha1Hex("guid-source")}\u0000")
        assertThat(payload).contains("FF2\u0000${sha1Hex("mac-source")}\u0000")
        assertThat(payload).contains("3B3\u0000${sha1Hex("disk-source")}\u0000")
    }

    @Test
    fun `buildSteamAuthenticationErrorMessage keeps result code and friendly text`() {
        val message = buildSteamAuthenticationErrorMessage(
            prefix = "Steam 登录失败",
            resultCode = 5,
        )

        assertThat(message).isEqualTo(
            "Steam 登录失败: 账号名或密码错误 (EResult=5)",
        )
    }

    @Test
    fun `buildSteamAuthenticationErrorMessage appends detail when available`() {
        val message = buildSteamAuthenticationErrorMessage(
            prefix = "Steam 登录失败",
            resultCode = 84,
            detail = "retry later",
        )

        assertThat(message).isEqualTo(
            "Steam 登录失败: 请求过于频繁，请稍后再试 (EResult=84): retry later",
        )
    }
}

private fun sha1Hex(value: String): String =
    MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
