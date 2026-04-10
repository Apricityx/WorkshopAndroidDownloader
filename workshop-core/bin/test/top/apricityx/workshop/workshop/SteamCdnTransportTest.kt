package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test
import top.apricityx.workshop.steam.protocol.CdnRequestEndpoint
import top.apricityx.workshop.steam.protocol.CdnServer

class SteamCdnTransportTest {
    private val transport = SteamCdnTransport(OkHttpClient())

    @Test
    fun `buildServerPool matches reference filtering and weighting`() {
        val pool = transport.buildServerPool(
            appId = 1u,
            contentServers = listOf(
                testServer(
                    host = "proxy.example.net",
                    type = "Proxy",
                    weightedLoad = 0f,
                    numEntriesInClientList = 1,
                    useAsProxy = true,
                    proxyRequestPathTemplate = "mirror/%host%%path%",
                ),
                testServer(
                    host = "cache-low.example.net",
                    type = "SteamCache",
                    weightedLoad = 10f,
                    numEntriesInClientList = 2,
                ),
                testServer(
                    host = "cdn-high.example.net",
                    type = "CDN",
                    weightedLoad = 20f,
                    numEntriesInClientList = 1,
                ),
                testServer(
                    host = "wrong-app.example.net",
                    type = "SteamCache",
                    weightedLoad = 1f,
                    numEntriesInClientList = 1,
                    allowedAppIds = listOf(2u),
                ),
                testServer(
                    host = "ignored.example.net",
                    type = "Proxy",
                    weightedLoad = 1f,
                    numEntriesInClientList = 3,
                ),
            ),
        )

        assertThat(pool.proxyServer?.host).isEqualTo("proxy.example.net")
        assertThat(pool.downloadServers.map(CdnServer::host)).containsExactly(
            "cache-low.example.net",
            "cache-low.example.net",
            "cdn-high.example.net",
        ).inOrder()
    }

    @Test
    fun `buildRequestUrl rewrites requests through proxy template`() {
        val origin = testServer(
            host = "origin.example.net",
            vHost = "origin-vhost.example.net",
            httpsSupport = "mandatory",
        )
        val proxy = testServer(
            host = "proxy.example.net",
            vHost = "proxy-vhost.example.net",
            type = "Proxy",
            httpsSupport = "",
            useAsProxy = true,
            proxyRequestPathTemplate = "mirror/%host%%path%",
        )

        val url = transport.buildRequestUrl(
            server = origin,
            endpoint = CdnRequestEndpoint(scheme = "https", port = 443),
            path = "depot/646570/manifest/1/5/999",
            query = "?token=abc",
            proxyServer = proxy,
        )

        assertThat(url.scheme).isEqualTo("http")
        assertThat(url.host).isEqualTo("proxy-vhost.example.net")
        assertThat(url.port).isEqualTo(80)
        assertThat(url.encodedPath).isEqualTo("/mirror/origin-vhost.example.net/depot/646570/manifest/1/5/999")
        assertThat(url.encodedQuery).isEqualTo("token=abc")
    }

    private fun testServer(
        host: String,
        vHost: String = host,
        type: String = "SteamCache",
        weightedLoad: Float = 0f,
        numEntriesInClientList: Int = 1,
        useAsProxy: Boolean = false,
        proxyRequestPathTemplate: String? = null,
        httpsSupport: String = "",
        allowedAppIds: List<UInt> = emptyList(),
    ) = CdnServer(
        type = type,
        sourceId = 1,
        cellId = 1,
        load = 0,
        weightedLoad = weightedLoad,
        numEntriesInClientList = numEntriesInClientList,
        steamChinaOnly = false,
        host = host,
        vHost = vHost,
        useAsProxy = useAsProxy,
        proxyRequestPathTemplate = proxyRequestPathTemplate,
        httpsSupport = httpsSupport,
        allowedAppIds = allowedAppIds,
        priorityClass = 0u,
    )
}
