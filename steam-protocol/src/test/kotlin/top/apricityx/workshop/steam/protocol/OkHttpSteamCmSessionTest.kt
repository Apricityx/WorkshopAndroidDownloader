package top.apricityx.workshop.steam.protocol

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Test
import top.apricityx.workshop.steam.proto.CMsgClientLogonResponse
import top.apricityx.workshop.steam.proto.CMsgProtoBufHeader

class OkHttpSteamCmSessionTest {
    private val servers = mutableListOf<MockWebServer>()
    private val client = OkHttpClient()

    @After
    fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        servers.forEach { it.close() }
    }

    @Test
    fun `reachable later candidate wins without waiting for stalled first endpoint`() = runBlocking {
        val stalled = server(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).build())
        val healthy = server(logonResponse())
        val logs = mutableListOf<String>()
        session(logger = { synchronized(logs) { logs += it } }).use { session ->
            val context = withTimeout(1_500) {
                session.connectAnonymous(listOf(stalled.cm(), healthy.cm()))
            }
            assertThat(context.cellId).isEqualTo(160u)
            // Losing sockets are cancelled. Their late onFailure callbacks must
            // not clear the winning socket/session.
            delay(150)
            assertThat(session.currentSession.value).isEqualTo(context)
            assertThat(logs.any { it.contains("selected endpoint=${healthy.cm().endpoint}") }).isTrue()
        }
    }

    @Test
    fun `failed handshake batch can be retried on the same session`() = runBlocking {
        val stalled = server(MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).build())
        val healthy = server(logonResponse())
        session(timeout = 150).use { session ->
            val error = runCatching { session.connect(listOf(stalled.cm())) }.exceptionOrNull()
            assertThat(error).isInstanceOf(SteamProtocolException::class.java)
            val context = withTimeout(1_500) { session.connectAnonymous(listOf(healthy.cm())) }
            delay(200)
            assertThat(session.currentSession.value).isEqualTo(context)
        }
    }

    @Test
    fun `cancelling a connection race releases attempts and does not poison next connection`() = runBlocking {
        val stalled = server(MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).build())
        val healthy = server(logonResponse())
        session().use { session ->
            val job = async { session.connectAnonymous(listOf(stalled.cm())) }
            withTimeout(1_000) {
                while (stalled.requestCount == 0) delay(10)
            }
            job.cancelAndJoin()
            assertThat(job.isCancelled).isTrue()
            val context = withTimeout(1_500) { session.connectAnonymous(listOf(healthy.cm())) }
            delay(100)
            assertThat(session.currentSession.value).isEqualTo(context)
        }
    }

    @Test
    fun `remote close during logon switches to a different candidate`() = runBlocking {
        val closing = server(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (SteamPacketCodec.decode(bytes.toByteArray()).emsg == SteamPacketCodec.emsgClientLogon) {
                    webSocket.cancel()
                }
            }
        }).build())
        val healthy = server(logonResponse())
        session(stagger = 100).use { session ->
            session.connect(listOf(closing.cm()))
            val context = withTimeout(2_000) { session.connectAnonymous(listOf(closing.cm(), healthy.cm())) }
            assertThat(context.cellId).isEqualTo(160u)
        }
    }

    @Test
    fun `unresponsive logon is bounded and retries another candidate`() = runBlocking {
        val silent = server(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {}).build())
        val healthy = server(logonResponse())
        session(stagger = 100, logonTimeout = 200).use { session ->
            session.connect(listOf(silent.cm()))
            val context = withTimeout(2_000) { session.connectAnonymous(listOf(silent.cm(), healthy.cm())) }
            assertThat(context.cellId).isEqualTo(160u)
        }
    }

    @Test
    fun `invalid refresh token is not retried against other servers`() = runBlocking {
        val rejected = server(logonResponse(result = 5))
        val otherLogons = AtomicInteger()
        val healthy = server(logonResponse(logons = otherLogons))
        session(stagger = 100).use { session ->
            // Pin the transport before logon. A connection race may legitimately
            // pick the healthy server first, which would never test rejection.
            session.connect(listOf(rejected.cm()))
            val error = runCatching {
                session.connectWithRefreshToken(
                    listOf(rejected.cm(), healthy.cm()),
                    SteamAccountSession("test", 123L, "not-a-real-token"),
                )
            }.exceptionOrNull()
            assertThat(error).isInstanceOf(SteamAuthenticationException::class.java)
            assertThat(otherLogons.get()).isEqualTo(0)
        }
    }

    private fun session(
        timeout: Long = 1_000L,
        stagger: Long = 25L,
        logonTimeout: Long = 1_000L,
        logger: (String) -> Unit = {},
    ) = OkHttpSteamCmSession(
        client = client,
        connectionTimeoutMillis = timeout,
        connectionStaggerMillis = stagger,
        logonTimeoutMillis = logonTimeout,
        diagnosticLogger = logger,
    )

    private fun server(response: MockResponse): MockWebServer = MockWebServer().also {
        it.start()
        it.enqueue(response)
        servers += it
    }

    private fun MockWebServer.cm() = CmServer(
        endpoint = "localhost:$port",
        type = "websockets",
        websocketUri = url("/cmsocket/").toString().replace("http://", "ws://"),
    )

    private fun logonResponse(result: Int = 1, logons: AtomicInteger = AtomicInteger()) =
        MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val packet = SteamPacketCodec.decode(bytes.toByteArray())
                if (packet.emsg == SteamPacketCodec.emsgClientLogon) {
                    logons.incrementAndGet()
                    webSocket.send(SteamPacketCodec.encode(
                        SteamPacketCodec.emsgClientLogOnResponse,
                        CMsgProtoBufHeader.newBuilder().setClientSessionid(123).setSteamid(456L).build(),
                        CMsgClientLogonResponse.newBuilder().setEresult(result).setCellId(160)
                            .setHeartbeatSeconds(30).build(),
                    ).toByteString())
                }
            }
        }).build()
}
