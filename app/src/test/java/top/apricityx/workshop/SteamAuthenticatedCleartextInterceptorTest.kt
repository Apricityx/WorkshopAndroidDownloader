package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test

class SteamAuthenticatedCleartextInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun blocks_authenticated_cleartext_requests_when_setting_is_disabled() {
        val client = testClient(
            hasAuthenticatedSteamSession = true,
            allowAuthenticatedCleartextHttp = false,
        )

        val failure = runCatching {
            client.newCall(Request.Builder().url(steamHttpUrl("api.steampowered.com")).build()).execute()
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SteamAuthenticatedCleartextBlockedException::class.java)
        assertThat(failure?.message).contains("api.steampowered.com")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun allows_authenticated_cleartext_requests_when_setting_is_enabled() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        val client = testClient(
            hasAuthenticatedSteamSession = true,
            allowAuthenticatedCleartextHttp = true,
        )

        client.newCall(Request.Builder().url(steamHttpUrl("api.steampowered.com")).build()).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
        }

        val request = server.takeRequest()
        assertThat(request.url.host).isEqualTo("api.steampowered.com")
    }

    @Test
    fun allows_anonymous_cleartext_requests_even_when_setting_is_disabled() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        val client = testClient(
            hasAuthenticatedSteamSession = false,
            allowAuthenticatedCleartextHttp = false,
        )

        client.newCall(Request.Builder().url(steamHttpUrl("dl.steam.clngaa.com")).build()).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
        }

        val request = server.takeRequest()
        assertThat(request.url.host).isEqualTo("dl.steam.clngaa.com")
    }

    @Test
    fun allows_cdn_cleartext_requests_when_authenticated_session_is_present() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())
        val client = testClient(
            hasAuthenticatedSteamSession = true,
            allowAuthenticatedCleartextHttp = false,
        )

        client.newCall(Request.Builder().url(steamHttpUrl("st.dl.eccdnx.com")).build()).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
        }

        val request = server.takeRequest()
        assertThat(request.url.host).isEqualTo("st.dl.eccdnx.com")
    }

    private fun testClient(
        hasAuthenticatedSteamSession: Boolean,
        allowAuthenticatedCleartextHttp: Boolean,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .dns(
                Dns { hostname ->
                    listOf(InetAddress.getByName("127.0.0.1"))
                },
            )
            .addInterceptor(
                SteamAuthenticatedCleartextInterceptor(
                    hasAuthenticatedSteamSession = { hasAuthenticatedSteamSession },
                    allowAuthenticatedCleartextHttpProvider = { allowAuthenticatedCleartextHttp },
                ),
            )
            .build()

    private fun steamHttpUrl(host: String) =
        server.url("/").newBuilder()
            .scheme("http")
            .host(host)
            .port(server.port)
            .build()
}
