package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Test

class SteamTokenProjectionTest {
    @Test
    fun `parseSteamJwtInfo extracts steam id token id and expiration`() {
        val payload = """{"sub":"76561198000000001","jti":"123456789","exp":1893456000}"""
        val token = buildJwt(payload)

        val parsed = parseSteamJwtInfo(token)

        assertThat(parsed.steamId).isEqualTo(76_561_198_000_000_001L)
        assertThat(parsed.tokenId).isEqualTo(123_456_789uL)
        assertThat(parsed.expiresAtEpochSeconds).isEqualTo(1_893_456_000L)
    }

    @Test
    fun `buildSteamLoginSecureCookie keeps steam id and raw token`() {
        val cookie = buildSteamLoginSecureCookie(
            steamId = 76_561_198_000_000_001L,
            accessToken = "abc.def.ghi",
        )

        assertThat(cookie).isEqualTo("steamLoginSecure=76561198000000001||abc.def.ghi")
    }

    private fun buildJwt(payload: String): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none"}""".toByteArray())
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "$header.$body."
    }
}
