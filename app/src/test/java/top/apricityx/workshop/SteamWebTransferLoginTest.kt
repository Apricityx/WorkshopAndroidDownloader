package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SteamWebTransferLoginTest {
    @Test
    fun `sanitizeSteamTransferLoginRedirect removes need_password query`() {
        val sanitized = sanitizeSteamTransferLoginRedirect(
            "https://store.steampowered.com/account/preferences/?need_password=1&foo=bar",
        )

        assertThat(sanitized)
            .isEqualTo("https://store.steampowered.com/account/preferences/?foo=bar")
    }

    @Test
    fun `parseSteamFinalizeLoginResponse extracts steam id primary domain and transfer info`() {
        val payload = """
            {
              "steamID": "76561198000000001",
              "primary_domain": "steamcommunity.com",
              "transfer_info": [
                {
                  "url": "https://steamcommunity.com/login/settoken",
                  "params": {
                    "nonce": "nonce-value",
                    "auth": "auth-value"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = parseSteamFinalizeLoginResponse(payload)

        assertThat(parsed.steamId).isEqualTo("76561198000000001")
        assertThat(parsed.primaryDomain).isEqualTo("steamcommunity.com")
        assertThat(parsed.transferInfo).hasSize(1)
        assertThat(parsed.transferInfo.first().url).isEqualTo("https://steamcommunity.com/login/settoken")
        assertThat(parsed.transferInfo.first().params)
            .containsExactly(
                "nonce",
                "nonce-value",
                "auth",
                "auth-value",
            )
    }

    @Test
    fun `parseSteamFinalizeLoginResponse accepts lowercase steamid`() {
        val payload = """
            {
              "steamid": "76561198000000002",
              "transfer_info": []
            }
        """.trimIndent()

        val parsed = parseSteamFinalizeLoginResponse(payload)

        assertThat(parsed.steamId).isEqualTo("76561198000000002")
    }

    @Test
    fun `parseSteamSetTokenResult returns one for success payload and null for blank payload`() {
        assertThat(parseSteamSetTokenResult("""{"result":1,"rtExpiry":123}""")).isEqualTo(1)
        assertThat(parseSteamSetTokenResult("")).isNull()
    }
}
