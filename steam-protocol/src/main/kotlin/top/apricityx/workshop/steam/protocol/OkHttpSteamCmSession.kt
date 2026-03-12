package top.apricityx.workshop.steam.protocol

import top.apricityx.workshop.steam.proto.CContentServerDirectory_GetCDNAuthToken_Response
import top.apricityx.workshop.steam.proto.CContentServerDirectory_GetManifestRequestCode_Response
import top.apricityx.workshop.steam.proto.CContentServerDirectory_GetServersForSteamPipe_Response
import top.apricityx.workshop.steam.proto.CMsgClientGetDepotDecryptionKey
import top.apricityx.workshop.steam.proto.CMsgClientGetDepotDecryptionKeyResponse
import top.apricityx.workshop.steam.proto.CMsgClientHeartBeat
import top.apricityx.workshop.steam.proto.CMsgClientHello
import top.apricityx.workshop.steam.proto.CMsgClientLogon
import top.apricityx.workshop.steam.proto.CMsgClientLogonResponse
import top.apricityx.workshop.steam.proto.CMsgProtoBufHeader
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class OkHttpSteamCmSession(
    private val client: OkHttpClient = OkHttpClient(),
) : SteamCmSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<Long, PendingRequest<out MessageLite>>()
    private val _currentSession = MutableStateFlow<SessionContext?>(null)
    private val nextJobId = AtomicLong(1L)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var heartbeatJob: Job? = null

    override val currentSession: StateFlow<SessionContext?> = _currentSession

    override suspend fun connectAnonymous(servers: List<CmServer>): SessionContext {
        require(servers.isNotEmpty()) { "No Steam CM servers available" }

        var lastError: Throwable? = null
        for (server in servers) {
            try {
                return connectSingleServer(server)
            } catch (error: Throwable) {
                lastError = error
                close()
            }
        }

        throw SteamProtocolException("Unable to connect to any Steam CM websocket", lastError)
    }

    private suspend fun connectSingleServer(server: CmServer): SessionContext {
        val deferred = CompletableDeferred<SessionContext>()
        val request = Request.Builder().url(server.websocketUri).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@OkHttpSteamCmSession.webSocket = webSocket
                sendHello()
                sendAnonymousLogon()
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                handleIncomingPacket(bytes.toByteArray(), deferred)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                pendingRequests.values.forEach { it.fail(t) }
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val failure = IOException("Steam websocket closed: $code $reason")
                pendingRequests.values.forEach { it.fail(failure) }
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(failure)
                }
            }
        }

        webSocket = client.newWebSocket(request, listener)
        return withTimeout(20_000) { deferred.await() }
    }

    private fun sendHello() {
        val body = CMsgClientHello.newBuilder()
            .setProtocolVersion(SteamPacketCodec.clientLogonProtocol)
            .build()

        val packet = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgClientHello,
            header = CMsgProtoBufHeader.getDefaultInstance(),
            body = body,
        )
        check(webSocket?.send(packet.toByteString()) == true) { "Failed to send Steam ClientHello" }
    }

    private fun sendAnonymousLogon() {
        val header = CMsgProtoBufHeader.newBuilder()
            .setClientSessionid(0)
            .setSteamid(anonymousSteamId())
            .build()

        val body = CMsgClientLogon.newBuilder()
            .setProtocolVersion(SteamPacketCodec.clientLogonProtocol)
            .setClientOsType(-500)
            .setClientLanguage("english")
            .setCellId(0)
            .setClientPackageVersion(1771)
            .setMachineName("Android Workshop Demo")
            .setMachineId(ByteString.copyFrom(machineId()))
            .build()

        val packet = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgClientLogon,
            header = header,
            body = body,
        )
        check(webSocket?.send(packet.toByteString()) == true) { "Failed to send Steam anonymous logon" }
    }

    private fun handleIncomingPacket(rawPacket: ByteArray, deferred: CompletableDeferred<SessionContext>) {
        val packet = SteamPacketCodec.decode(rawPacket)
        if (packet.emsg == SteamPacketCodec.emsgMulti) {
            SteamPacketCodec.expandMulti(packet).forEach { nested ->
                handleIncomingPacket(nested, deferred)
            }
            return
        }

        val targetJobId = packet.header.jobidTarget
        if (targetJobId > 0L) {
            @Suppress("UNCHECKED_CAST")
            val request = pendingRequests[targetJobId] as PendingRequest<MessageLite>?
            if (request != null && request.accepts(packet.emsg)) {
                pendingRequests.remove(targetJobId)
                request.complete(packet)
                return
            }
        }

        when (packet.emsg) {
            SteamPacketCodec.emsgClientLogOnResponse -> {
                val response = CMsgClientLogonResponse.parseFrom(packet.body)
                if (response.eresult != 1) {
                    deferred.completeExceptionally(
                        SteamProtocolException("Steam anonymous logon failed with EResult=${response.eresult}"),
                    )
                    return
                }
                val session = SessionContext(
                    sessionId = packet.header.clientSessionid,
                    steamId = packet.header.steamid,
                    cellId = response.cellId.toUInt(),
                    heartbeatSeconds = response.legacyOutOfGameHeartbeatSeconds.takeIf { it > 0 }
                        ?: response.heartbeatSeconds.takeIf { it > 0 }
                        ?: 30,
                )
                _currentSession.value = session
                startHeartbeat(session.heartbeatSeconds)
                deferred.complete(session)
            }

            SteamPacketCodec.emsgClientLoggedOff -> {
                val failure = SteamProtocolException("Steam session logged off by remote server")
                pendingRequests.values.forEach { it.fail(failure) }
                pendingRequests.clear()
            }
        }
    }

    private fun startHeartbeat(intervalSeconds: Int) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(intervalSeconds.coerceAtLeast(10).toLong() * 1_000L)
                val session = _currentSession.value ?: break
                val packet = SteamPacketCodec.encode(
                    emsg = SteamPacketCodec.emsgClientHeartBeat,
                    header = CMsgProtoBufHeader.newBuilder()
                        .setClientSessionid(session.sessionId)
                        .setSteamid(session.steamId)
                        .build(),
                    body = CMsgClientHeartBeat.getDefaultInstance(),
                )
                webSocket?.send(packet.toByteString())
            }
        }
    }

    override suspend fun <T : MessageLite> callServiceMethod(
        methodName: String,
        request: MessageLite,
        parser: Parser<T>,
    ): T {
        val session = currentSession.value
            ?: throw SteamProtocolException("Steam CM session is not connected")
        val sourceJobId = nextJobId.getAndIncrement()
        val response = CompletableDeferred<T>()
        pendingRequests[sourceJobId] = PendingRequest(
            expectedEmsg = SteamPacketCodec.emsgServiceMethodResponse,
            parser = parser,
            deferred = response,
        )

        val packet = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgServiceMethodCallFromClient,
            header = CMsgProtoBufHeader.newBuilder()
                .setClientSessionid(session.sessionId)
                .setSteamid(session.steamId)
                .setJobidSource(sourceJobId)
                .setTargetJobName(methodName)
                .build(),
            body = request,
        )

        if (webSocket?.send(packet.toByteString()) != true) {
            pendingRequests.remove(sourceJobId)
            throw SteamProtocolException("Failed to send Steam service request: $methodName")
        }

        return withTimeout(20_000) { response.await() }
    }

    override suspend fun requestDepotDecryptionKey(appId: UInt, depotId: UInt): ByteArray {
        val session = currentSession.value
            ?: throw SteamProtocolException("Steam CM session is not connected")
        val sourceJobId = nextJobId.getAndIncrement()
        val response = CompletableDeferred<CMsgClientGetDepotDecryptionKeyResponse>()
        pendingRequests[sourceJobId] = PendingRequest(
            expectedEmsg = SteamPacketCodec.emsgClientGetDepotDecryptionKeyResponse,
            parser = CMsgClientGetDepotDecryptionKeyResponse.parser(),
            deferred = response,
        )

        val packet = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgClientGetDepotDecryptionKey,
            header = CMsgProtoBufHeader.newBuilder()
                .setClientSessionid(session.sessionId)
                .setSteamid(session.steamId)
                .setJobidSource(sourceJobId)
                .build(),
            body = CMsgClientGetDepotDecryptionKey.newBuilder()
                .setAppId(appId.toInt())
                .setDepotId(depotId.toInt())
                .build(),
        )

        if (webSocket?.send(packet.toByteString()) != true) {
            pendingRequests.remove(sourceJobId)
            throw SteamProtocolException("Failed to request depot decryption key for depot=$depotId")
        }

        val body = withTimeout(20_000) { response.await() }
        if (body.eresult != 1) {
            throw SteamProtocolException(
                "Steam depot key request failed for depot=$depotId app=$appId with EResult=${body.eresult}",
            )
        }
        if (body.depotId.toUInt() != depotId) {
            throw SteamProtocolException(
                "Steam depot key response depot mismatch: expected=$depotId actual=${body.depotId.toUInt()}",
            )
        }
        val key = body.depotEncryptionKey.toByteArray()
        if (key.isEmpty()) {
            throw SteamProtocolException("Steam returned an empty depot key for depot=$depotId")
        }
        return key
    }

    override fun close() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _currentSession.value = null
        val failure = SteamProtocolException("Steam CM session closed")
        pendingRequests.values.forEach { it.fail(failure) }
        pendingRequests.clear()
        webSocket?.close(1000, "closed")
        webSocket = null
    }

    private fun anonymousSteamId(): Long {
        val universe = 1L
        val accountType = 10L
        return (universe shl 56) or (accountType shl 52)
    }

    private fun machineId(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest("android-workshop-demo".toByteArray())
    }

    private class PendingRequest<T : MessageLite>(
        private val expectedEmsg: Int,
        private val parser: Parser<T>,
        private val deferred: CompletableDeferred<T>,
    ) {
        fun accepts(emsg: Int): Boolean = emsg == expectedEmsg

        fun complete(packet: SteamPacket) {
            if (!deferred.isCompleted) {
                deferred.complete(parser.parseFrom(packet.body))
            }
        }

        fun fail(error: Throwable) {
            if (!deferred.isCompleted) {
                deferred.completeExceptionally(error)
            }
        }
    }
}
