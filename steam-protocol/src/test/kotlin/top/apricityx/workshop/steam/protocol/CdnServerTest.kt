package top.apricityx.workshop.steam.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CdnServerTest {
    @Test
    fun `optional https support prefers https and keeps http fallback`() {
        val server = testServer(httpsSupport = "optional")

        assertThat(server.supportsHttps).isTrue()
        assertThat(server.requiresHttps).isFalse()
        assertThat(server.secureScheme).isEqualTo("https")
        assertThat(server.port).isEqualTo(443)
        assertThat(server.requestEndpoints()).containsExactly(
            CdnRequestEndpoint(scheme = "https", port = 443),
            CdnRequestEndpoint(scheme = "http", port = 80),
        ).inOrder()
    }

    @Test
    fun `missing https support uses http only`() {
        val server = testServer(httpsSupport = "")

        assertThat(server.supportsHttps).isFalse()
        assertThat(server.requiresHttps).isFalse()
        assertThat(server.secureScheme).isEqualTo("http")
        assertThat(server.port).isEqualTo(80)
        assertThat(server.requestEndpoints()).containsExactly(
            CdnRequestEndpoint(scheme = "http", port = 80),
        )
    }

    private fun testServer(httpsSupport: String) = CdnServer(
        type = "SteamCache",
        sourceId = 1,
        cellId = 1,
        load = 0,
        weightedLoad = 0f,
        numEntriesInClientList = 0,
        steamChinaOnly = false,
        host = "cache.example.net",
        vHost = "cache.example.net",
        useAsProxy = false,
        proxyRequestPathTemplate = null,
        httpsSupport = httpsSupport,
        allowedAppIds = emptyList(),
        priorityClass = 0u,
    )
}
