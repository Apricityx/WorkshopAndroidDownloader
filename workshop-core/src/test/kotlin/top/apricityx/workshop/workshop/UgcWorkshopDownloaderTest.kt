package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import top.apricityx.workshop.steam.protocol.CmServer
import top.apricityx.workshop.steam.protocol.SessionContext
import top.apricityx.workshop.steam.protocol.SteamAccountSession
import top.apricityx.workshop.steam.protocol.SteamCmSession
import top.apricityx.workshop.steam.protocol.SteamDirectoryClient

class UgcWorkshopDownloaderTest {
    @Test
    fun `public fallback does not repeatedly reconnect failed CM for code servers and key`() = runBlocking {
        val session = UnavailableSession()
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            paths += path
            val (code, body) = when {
                path.contains("GetCMListForConnect") -> 200 to """
                    {"response":{"serverlist":[{"endpoint":"cm.test:443","type":"websockets"}]}}
                """.trimIndent()
                path.contains("GetServersForSteamPipe") -> 200 to """
                    {"response":{"servers":[{"type":"SteamCache","source_id":1,"host":"cdn.test",
                    "vhost":"cdn.test","https_support":"mandatory","num_entries_in_client_list":1}]}}
                """.trimIndent()
                else -> 401 to "Unauthorized"
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        val output = Files.createTempDirectory("workshop-fallback-test").toFile()
        try {
            val error = runCatching {
                UgcWorkshopDownloader(client, SteamDirectoryClient(client), sessionFactory = { session }).download(
                    WorkshopDownloadRequest(480u, 1uL, output),
                    ResolvedWorkshopItem.UgcManifestItem(123uL, 480u, "test", "{}"),
                    emit = {}, log = {},
                )
            }.exceptionOrNull()
            assertThat(error).isInstanceOf(WorkshopDownloadException::class.java)
            assertThat(session.connections).isEqualTo(1)
            assertThat(session.serviceRequests).isEqualTo(0)
            assertThat(session.keyRequests).isEqualTo(0)
            assertThat(session.closed).isTrue()
            assertThat(paths.any { it.contains("GetServersForSteamPipe") }).isTrue()
            assertThat(paths.any { it == "/depot/480/manifest/123/5" }).isTrue()
        } finally {
            output.deleteRecursively()
        }
    }

    @Test
    fun `cancelled CM connection never falls through to public CDN`() = runBlocking {
        val session = UnavailableSession(cancelled = true)
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            paths += chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"response":{"serverlist":[{"endpoint":"cm.test:443","type":"websockets"}]}}""".toResponseBody())
                .build()
        }.build()
        val output = Files.createTempDirectory("workshop-cancel-test").toFile()
        try {
            val error = runCatching {
                UgcWorkshopDownloader(client, SteamDirectoryClient(client), sessionFactory = { session }).download(
                    WorkshopDownloadRequest(480u, 1uL, output),
                    ResolvedWorkshopItem.UgcManifestItem(123uL, 480u, "test", "{}"),
                    emit = {}, log = {},
                )
            }.exceptionOrNull()
            assertThat(error).isInstanceOf(CancellationException::class.java)
            assertThat(paths).hasSize(1)
            assertThat(session.closed).isTrue()
        } finally {
            output.deleteRecursively()
        }
    }

    private class UnavailableSession(private val cancelled: Boolean = false) : SteamCmSession {
        var connections = 0
        var serviceRequests = 0
        var keyRequests = 0
        var closed = false
        override val currentSession = MutableStateFlow<SessionContext?>(null)
        override suspend fun connect(servers: List<CmServer>) = Unit
        override suspend fun connectAnonymous(servers: List<CmServer>): SessionContext {
            connections++
            if (cancelled) throw CancellationException("cancelled")
            throw IOException("CM unavailable")
        }
        override suspend fun connectWithRefreshToken(servers: List<CmServer>, account: SteamAccountSession) =
            connectAnonymous(servers)
        override suspend fun <T : MessageLite> callServiceMethod(methodName: String, request: MessageLite, parser: Parser<T>): T {
            serviceRequests++
            error("CM must not be retried during public fallback")
        }
        override suspend fun requestDepotDecryptionKey(appId: UInt, depotId: UInt): ByteArray {
            keyRequests++
            error("CM must not be retried during public fallback")
        }
        override fun close() { closed = true }
    }
}
