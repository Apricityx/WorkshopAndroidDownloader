package top.apricityx.workshop.steam.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SteamDirectoryClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `loadServers filters websocket entries`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "response": {
                    "serverlist": [
                      {"endpoint":"cm1.example.net:27020","type":"websockets"},
                      {"endpoint":"cm2.example.net:27017","type":"tcp"}
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val client = SteamDirectoryClient(
            client = OkHttpClient(),
            apiBaseUrl = server.url("/"),
        )

        val result = client.loadServers()

        assertThat(result).hasSize(1)
        assertThat(result.first().endpoint).isEqualTo("cm1.example.net:27020")
        assertThat(result.first().websocketUri).isEqualTo("wss://cm1.example.net:27020/cmsocket/")
    }

    @Test
    fun `loadContentServers maps response fields`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "response": {
                    "servers": [
                      {
                        "type":"SteamCache",
                        "source_id":123,
                        "cell_id":33,
                        "load":70,
                        "weighted_load":72.5,
                        "num_entries_in_client_list":4,
                        "steam_china_only":false,
                        "host":"cache.example.net",
                        "vhost":"cache.example.net",
                        "use_as_proxy":false,
                        "proxy_request_path_template":"",
                        "https_support":"mandatory",
                        "allowed_app_ids":[4000],
                        "priority_class":3
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val client = SteamDirectoryClient(
            client = OkHttpClient(),
            apiBaseUrl = server.url("/"),
        )

        val result = client.loadContentServers()

        assertThat(result).hasSize(1)
        assertThat(result.first().host).isEqualTo("cache.example.net")
        assertThat(result.first().secureScheme).isEqualTo("https")
        assertThat(result.first().allowedAppIds).containsExactly(4000u)
    }

    @Test
    fun `loadContentServers tolerates missing cell id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "response": {
                    "servers": [
                      {
                        "type":"SteamCache",
                        "source_id":123,
                        "load":70,
                        "weighted_load":72.5,
                        "num_entries_in_client_list":4,
                        "steam_china_only":false,
                        "host":"cache.example.net",
                        "vhost":"cache.example.net",
                        "use_as_proxy":false,
                        "proxy_request_path_template":"",
                        "https_support":"mandatory",
                        "allowed_app_ids":[4000],
                        "priority_class":3
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val client = SteamDirectoryClient(
            client = OkHttpClient(),
            apiBaseUrl = server.url("/"),
        )

        val result = client.loadContentServers(cellId = 33u)

        assertThat(result).hasSize(1)
        assertThat(result.first().cellId).isEqualTo(33)
        assertThat(result.first().host).isEqualTo("cache.example.net")
    }
}
