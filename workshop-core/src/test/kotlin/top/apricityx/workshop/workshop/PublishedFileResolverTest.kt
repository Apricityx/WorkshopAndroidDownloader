package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class PublishedFileResolverTest {
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
    fun `resolve prefers direct file_url when both values exist`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "response": {
                    "publishedfiledetails": [
                      {
                        "result": 1,
                        "title": "Direct Item",
                        "filename": "mods/example.zip",
                        "file_type": 0,
                        "file_url": "https://cdn.example.com/example.zip",
                        "file_size": 1234,
                        "hcontent_file": 999999,
                        "consumer_app_id": 480
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val resolver = PublishedFileResolver(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
        )

        val result = resolver.resolve(480u, 100u)

        assertThat(result).isInstanceOf(ResolvedWorkshopItem.DirectUrlItem::class.java)
        val direct = result as ResolvedWorkshopItem.DirectUrlItem
        assertThat(direct.fileUrl).isEqualTo("https://cdn.example.com/example.zip")
        assertThat(direct.fileName).isEqualTo("example.zip")
    }

    @Test
    fun `resolve falls back to UGC manifest when file_url is missing`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "response": {
                    "publishedfiledetails": [
                      {
                        "result": 1,
                        "title": "UGC Item",
                        "filename": "",
                        "file_type": 0,
                        "hcontent_file": 888777666,
                        "consumer_app_id": 550
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val resolver = PublishedFileResolver(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
        )

        val result = resolver.resolve(550u, 100u)

        assertThat(result).isInstanceOf(ResolvedWorkshopItem.UgcManifestItem::class.java)
        val ugc = result as ResolvedWorkshopItem.UgcManifestItem
        assertThat(ugc.manifestId).isEqualTo(888777666uL)
        assertThat(ugc.depotId).isEqualTo(550u)
    }

    @Test
    fun `resolve treats missing file_type as community`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "response": {
                    "publishedfiledetails": [
                      {
                        "result": 1,
                        "title": "Implicit Community Item",
                        "filename": "",
                        "hcontent_file": 508233140162973776,
                        "consumer_app_id": 646570
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val resolver = PublishedFileResolver(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
        )

        val result = resolver.resolve(646570u, 3677098410uL)

        assertThat(result).isInstanceOf(ResolvedWorkshopItem.UgcManifestItem::class.java)
        val ugc = result as ResolvedWorkshopItem.UgcManifestItem
        assertThat(ugc.manifestId).isEqualTo(508233140162973776uL)
        assertThat(ugc.depotId).isEqualTo(646570u)
    }

    @Test(expected = WorkshopDownloadException::class)
    fun `resolve rejects collections`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "response": {
                    "publishedfiledetails": [
                      {
                        "result": 1,
                        "title": "Collection",
                        "filename": "",
                        "file_type": 2
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val resolver = PublishedFileResolver(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
        )

        resolver.resolve(550u, 100u)
    }
}
