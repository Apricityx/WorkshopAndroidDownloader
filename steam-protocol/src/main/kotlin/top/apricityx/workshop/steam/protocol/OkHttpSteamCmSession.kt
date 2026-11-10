package top.apricityx.workshop.steam.protocol

import top.apricityx.workshop.steam.proto.CContentServerDirectory_GetCDNAuthToken_Response
import top.apricityx.workshop.steam.proto.CContentServerDirectory_GetManifestRequestCode_Response
import top.apricityx.workshop.steam.proto.CContentServerDirectory_GetServersForSteamPipe_Response
import top.apricityx.workshop.steam.proto.CMsgClientGetDepotDecryptionKey
import top.apricityx.workshop.steam.proto.CMsgClientGetDepotDecryptionKeyResponse
import top.apricityx.workshop.steam.proto.CMsgClientHeartBeat
import top.apricityx.workshop.steam.proto.CMsgClientHello
import top.apricityx.workshop.steam.proto.CMsgClientLoggedOff
import top.apricityx.workshop.steam.proto.CMsgClientLogon
import top.apricityx.workshop.steam.proto.CMsgClientLogonResponse
import top.apricityx.workshop.steam.proto.CMsgIPAddress
import top.apricityx.workshop.steam.proto.CMsgProtoBufHeader
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

class OkHttpSteamCmSession(
    private val client: OkHttpClient = newDefaultOkHttpClient(),
    private val machineName: String = DEFAULT_MACHINE_NAME,
    private val machineId: ByteArray = defaultSteamMachineId(),
    private val connectionTimeoutMillis: Long = 8_000L,
    private val connectionStaggerMillis: Long = 250L,
    private val logonTimeoutMillis: Long = 15_000L,
    private val diagnosticLogger: (String) -> Unit = {},
) : SteamCmSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<Long, PendingRequest<out MessageLite>>()
    private val _currentSession = MutableStateFlow<SessionContext?>(null)
    private val nextJobId = AtomicLong(1L)
    private val transportLock = Any()
    private val connectionAttempts = ConcurrentHashMap.newKeySet<ConnectionAttempt>()
    private val failedServers = ConcurrentHashMap.newKeySet<String>()
    private var transportGeneration = 0L

    init {
        require(connectionTimeoutMillis > 0)
        require(connectionStaggerMillis >= 0)
        require(logonTimeoutMillis > 0)
    }

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var currentServer: CmServer? = null

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var pendingLogon = CompletableDeferred<SessionContext>()

    @Volatile
    private var reconnectPlan: ReconnectPlan? = null

    override val currentSession: StateFlow<SessionContext?> = _currentSession

    override suspend fun connect(servers: List<CmServer>) {
        reconnectPlan = ReconnectPlan.TransportOnly(servers.toList())
        try {
            ensureConnected(servers)
        } catch (error: Throwable) {
            closeTransport()
            throw error
        }
    }

    override suspend fun connectAnonymous(servers: List<CmServer>): SessionContext {
        currentSession.value?.let { return it }
        reconnectPlan = ReconnectPlan.Anonymous(servers.toList())
        return connectAndLogon(servers) {
            startLogon(
                requestLabel = "anonymous",
                buildBody = {
                    CMsgClientLogon.newBuilder()
                        .setProtocolVersion(SteamPacketCodec.clientLogonProtocol)
                        .setClientOsType(DEFAULT_CLIENT_OS_TYPE)
                        .setClientLanguage("english")
                        .setCellId(0)
                        .setClientPackageVersion(1771)
                        .setObfuscatedPrivateIp(defaultObfuscatedPrivateIp())
                        .setDeprecatedObfustucatedPrivateIp(defaultObfuscatedPrivateIp().v4)
                        .setMachineName(machineName)
                        .setMachineId(ByteString.copyFrom(machineId))
                        .setSupportsRateLimitResponse(true)
                        .build()
                },
                headerSteamId = anonymousSteamId(),
            )
        }
    }

    override suspend fun connectWithRefreshToken(
        servers: List<CmServer>,
        account: SteamAccountSession,
    ): SessionContext {
        currentSession.value?.let { return it }
        reconnectPlan = ReconnectPlan.RefreshToken(
            servers = servers.toList(),
            account = account,
        )
        return connectAndLogon(servers) {
            startLogon(
                requestLabel = "refresh token",
                buildBody = {
                    CMsgClientLogon.newBuilder()
                        .setProtocolVersion(SteamPacketCodec.clientLogonProtocol)
                        .setClientOsType(DEFAULT_CLIENT_OS_TYPE)
                        .setClientLanguage("english")
                        .setCellId(0)
                        .setClientPackageVersion(1771)
                        .setObfuscatedPrivateIp(defaultObfuscatedPrivateIp())
                        .setDeprecatedObfustucatedPrivateIp(defaultObfuscatedPrivateIp().v4)
                        .setMachineName(account.machineName.ifBlank { machineName })
                        .setMachineId(ByteString.copyFrom(machineId))
                        .setSupportsRateLimitResponse(true)
                        .setAccountName(account.accountName)
                        .setShouldRememberPassword(account.shouldRememberPassword)
                        .setAccessToken(account.refreshToken)
                        .build()
                },
                headerSteamId = account.steamId,
            )
        }
    }

    private suspend fun connectAndLogon(
        servers: List<CmServer>,
        logon: suspend () -> SessionContext,
    ): SessionContext {
        val remaining = servers.distinctBy(CmServer::endpoint).toMutableList()
        var lastError: Throwable? = null
        // Only retry transport failures. Invalid credentials and rate limits
        // must not be replayed against every CM server.
        repeat(MAX_LOGON_ATTEMPTS) {
            var selected: CmServer? = null
            try {
                ensureConnected(remaining)
                selected = currentServer
                return logon()
            } catch (error: Throwable) {
                closeTransport()
                currentCoroutineContext().ensureActive()
                if (selected == null) throw error
                if (error is SteamAuthenticationException || !isRecoverableConnectionFailure(error)) throw error
                lastError = error
                failedServers += selected.endpoint
                remaining.removeAll { it.endpoint == selected.endpoint }
                if (remaining.isEmpty()) throw error
            }
        }
        throw SteamProtocolException("Steam CM logon failed after $MAX_LOGON_ATTEMPTS servers", lastError)
    }

    private suspend fun ensureConnected(servers: List<CmServer>) = coroutineScope {
        if (webSocket != null) {
            return@coroutineScope
        }
        require(servers.isNotEmpty()) { "No Steam CM servers available" }
        val generation = synchronized(transportLock) { transportGeneration }
        val candidates = servers.distinctBy(CmServer::endpoint)
            .sortedBy { it.endpoint in failedServers }
            .take(MAX_CONNECTION_CANDIDATES)
        val results = Channel<Result<ConnectionAttempt>>(Channel.UNLIMITED)
        val attempts = candidates.map(::ConnectionAttempt)
        connectionAttempts.addAll(attempts)
        val jobs = attempts.mapIndexed { index, attempt ->
            launch {
                delay(index * connectionStaggerMillis)
                val startedAt = System.nanoTime()
                diagnosticLogger("Steam CM connecting endpoint=${attempt.server.endpoint} timeoutMs=$connectionTimeoutMillis")
                try {
                    openConnectionAttempt(attempt, generation)
                    results.send(Result.success(attempt))
                } catch (error: Throwable) {
                    currentCoroutineContext().ensureActive()
                    diagnosticLogger("Steam CM connection failed endpoint=${attempt.server.endpoint} elapsedMs=${(System.nanoTime() - startedAt) / 1_000_000L}: ${error.message}")
                    failedServers += attempt.server.endpoint
                    results.send(Result.failure(SteamProtocolException(
                        "Steam CM websocket failed: ${attempt.server.endpoint}: ${error.message}", error,
                    )))
                }
            }
        }
        var selected: ConnectionAttempt? = null
        var connected = false
        try {
            var lastError: Throwable? = null
            repeat(attempts.size) {
                val result = results.receive()
                val attempt = result.getOrNull()
                if (attempt != null) {
                    synchronized(transportLock) {
                        check(transportGeneration == generation) { "Steam CM connection cancelled" }
                        if (attempt.failure == null) {
                            selected = attempt
                            webSocket = attempt.socket
                            currentServer = attempt.server
                            failedServers -= attempt.server.endpoint
                        } else {
                            lastError = attempt.failure
                        }
                    }
                    if (selected != null) {
                        sendHello()
                        connected = true
                        diagnosticLogger("Steam CM selected endpoint=${attempt.server.endpoint}")
                        return@coroutineScope
                    }
                } else {
                    lastError = result.exceptionOrNull()
                }
            }
            throw SteamProtocolException("Unable to connect to any Steam CM websocket", lastError)
        } finally {
            jobs.forEach { it.cancel() }
            synchronized(transportLock) {
                if (!connected) {
                    if (webSocket === selected?.socket) webSocket = null
                }
                attempts.filter { !connected || it !== selected }.forEach {
                    it.abandoned = true
                    it.socket?.cancel()
                }
            }
            connectionAttempts.removeAll(attempts.toSet())
            results.close()
        }
    }

    private suspend fun openConnectionAttempt(attempt: ConnectionAttempt, generation: Long) {
        val request = Request.Builder().url(attempt.server.websocketUri).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt.opened.completeIfNeeded(Unit)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                synchronized(transportLock) {
                    if (generation == transportGeneration && this@OkHttpSteamCmSession.webSocket === webSocket) {
                        handleIncomingPacket(bytes.toByteArray())
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failAttempt(webSocket, t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failAttempt(webSocket, IOException("Steam websocket closed: $code $reason"))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                failAttempt(webSocket, IOException("Steam websocket closing: $code $reason"))
                webSocket.close(code, reason)
            }

            private fun failAttempt(socket: WebSocket, error: Throwable) {
                synchronized(transportLock) {
                    attempt.failure = error
                    if (generation == transportGeneration && webSocket === socket) {
                        failedServers += attempt.server.endpoint
                        webSocket = null
                        failActiveState(error)
                    }
                    attempt.opened.completeExceptionallyIfNeeded(error)
                }
            }
        }

        // Limit the handshake independently of the longer service-call timeout.
        // All candidates race with staggered starts, so a black-holed first
        // endpoint does not hold up a reachable later endpoint for 30 seconds.
        val connectionClient = client.newBuilder()
            .connectTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        synchronized(transportLock) {
            check(generation == transportGeneration) { "Steam CM connection cancelled" }
            check(!attempt.abandoned) { "Steam CM connection attempt cancelled" }
            attempt.socket = connectionClient.newWebSocket(request, listener)
        }
        withTimeout(connectionTimeoutMillis) { attempt.opened.await() }
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

    private suspend fun startLogon(
        requestLabel: String,
        buildBody: () -> CMsgClientLogon,
        headerSteamId: Long,
    ): SessionContext {
        val packet = SteamPacketCodec.encode(
            emsg = SteamPacketCodec.emsgClientLogon,
            header = CMsgProtoBufHeader.newBuilder()
                .setClientSessionid(0)
                .setSteamid(headerSteamId)
                .build(),
            body = buildBody(),
        )
        synchronized(transportLock) {
            pendingLogon = CompletableDeferred()
            if (webSocket?.send(packet.toByteString()) != true) {
                val error = SteamProtocolException("Failed to send Steam $requestLabel logon")
                pendingLogon.completeExceptionallyIfNeeded(error)
                throw error
            }
        }
        return withTimeout(logonTimeoutMillis) { pendingLogon.await() }
    }

    private fun handleIncomingPacket(rawPacket: ByteArray) {
        if (!SteamPacketCodec.isProtoPacket(rawPacket)) {
            handleLegacyPacket(rawPacket)
            return
        }

        val packet = SteamPacketCodec.decode(rawPacket)
        if (packet.emsg == SteamPacketCodec.emsgMulti) {
            SteamPacketCodec.expandMulti(packet).forEach { nested ->
                handleIncomingPacket(nested)
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
                    pendingLogon.completeExceptionallyIfNeeded(
                        SteamAuthenticationException(
                            resultCode = response.eresult,
                            message = buildSteamAuthenticationErrorMessage(
                                prefix = "Steam 会话登录失败",
                                resultCode = response.eresult,
                            ),
                        ),
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
                pendingLogon.completeIfNeeded(session)
            }

            SteamPacketCodec.emsgClientLoggedOff -> {
                val body = CMsgClientLoggedOff.parseFrom(packet.body)
                val failure = SteamProtocolException(
                    "Steam session logged off by remote server EResult=${body.eresult}",
                )
                failActiveState(failure)
            }
        }
    }

    private fun handleLegacyPacket(rawPacket: ByteArray) {
        val packet = SteamPacketCodec.decodeLegacyPacket(rawPacket)
        when (packet.emsg) {
            SteamPacketCodec.emsgClientLoggedOff -> {
                val body = SteamPacketCodec.decodeLegacyLoggedOffBody(packet)
                failActiveState(
                    SteamProtocolException(
                        "Steam session logged off by remote server EResult=${body.resultCode} " +
                            "(legacy minReconnect=${body.minReconnectHintSeconds}s maxReconnect=${body.maxReconnectHintSeconds}s)",
                    ),
                )
            }

            SteamPacketCodec.emsgClientServerUnavailable -> {
                val body = SteamPacketCodec.decodeLegacyServerUnavailableBody(packet)
                failActiveState(
                    SteamProtocolException(
                        "Steam server unavailable for request EMsg=${body.emsgSent} " +
                            "job=${body.jobIdSent} serverType=${body.serverTypeUnavailable}",
                    ),
                )
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
    ): T = retryRecoverableRequest {
        if (webSocket == null) {
            throw SteamProtocolException("Steam CM session is not connected")
        }
        val session = currentSession.value
        val sourceJobId = nextJobId.getAndIncrement()
        val response = CompletableDeferred<T>()
        pendingRequests[sourceJobId] = PendingRequest(
            methodName = methodName,
            expectedEmsg = SteamPacketCodec.emsgServiceMethodResponse,
            parser = parser,
            deferred = response,
        )

        val packet = SteamPacketCodec.encode(
            emsg = if (session == null) {
                SteamPacketCodec.emsgServiceMethodCallFromClientNonAuthed
            } else {
                SteamPacketCodec.emsgServiceMethodCallFromClient
            },
            header = CMsgProtoBufHeader.newBuilder()
                .apply {
                    if (session != null) {
                        setClientSessionid(session.sessionId)
                        setSteamid(session.steamId)
                    }
                }
                .setJobidSource(sourceJobId)
                .setTargetJobName(methodName)
                .build(),
            body = request,
        )

        if (webSocket?.send(packet.toByteString()) != true) {
            pendingRequests.remove(sourceJobId)
            throw SteamProtocolException("Failed to send Steam service request: $methodName")
        }

        try {
            withTimeout(REQUEST_TIMEOUT_MS) { response.await() }
        } catch (error: Throwable) {
            pendingRequests.remove(sourceJobId)
            throw error
        }
    }

    override suspend fun requestDepotDecryptionKey(appId: UInt, depotId: UInt): ByteArray = retryRecoverableRequest {
        val session = currentSession.value
            ?: throw SteamProtocolException("Steam CM session is not connected")
        val sourceJobId = nextJobId.getAndIncrement()
        val response = CompletableDeferred<CMsgClientGetDepotDecryptionKeyResponse>()
        pendingRequests[sourceJobId] = PendingRequest(
            methodName = "ClientGetDepotDecryptionKey",
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

        val body = withTimeout(REQUEST_TIMEOUT_MS) { response.await() }
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
        key
    }

    override fun close() {
        closeTransport()
        scope.cancel()
    }

    private fun closeTransport() {
        synchronized(transportLock) {
            transportGeneration++
            heartbeatJob?.cancel()
            heartbeatJob = null
            val failure = SteamProtocolException("Steam CM session closed")
            failActiveState(failure)
            val previous = webSocket
            webSocket = null
            previous?.cancel()
            connectionAttempts.forEach {
                it.opened.completeExceptionallyIfNeeded(failure)
                it.socket?.cancel()
            }
        }
    }

    private fun anonymousSteamId(): Long {
        val universe = 1L
        val accountType = 10L
        return (universe shl 56) or (accountType shl 52)
    }

    private fun defaultObfuscatedPrivateIp(): CMsgIPAddress {
        val ipv4 = detectLocalIpv4Address()
        val asUInt = ipv4.toUnsignedInt()
        val obfuscated = asUInt xor OBFUSCATION_MASK
        return CMsgIPAddress.newBuilder()
            .setV4(obfuscated.toInt())
            .build()
    }

    private fun detectLocalIpv4Address(): ByteArray =
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address) {
                        return@runCatching address.address
                    }
                }
            }
            null
        }.getOrNull()
            ?: byteArrayOf(127.toByte(), 0, 0, 1)

    private fun failActiveState(error: Throwable) {
        _currentSession.value = null
        currentServer = null
        pendingRequests.values.forEach { it.fail(error) }
        pendingRequests.clear()
        pendingLogon.completeExceptionallyIfNeeded(error)
    }

    private suspend fun reconnectForRetry() {
        val plan = reconnectPlan ?: throw SteamProtocolException("No saved Steam connection plan to retry request")
        currentServer?.let { failedServers += it.endpoint }
        closeTransport()
        when (plan) {
            is ReconnectPlan.TransportOnly -> connect(plan.servers)
            is ReconnectPlan.Anonymous -> connectAnonymous(plan.servers)
            is ReconnectPlan.RefreshToken -> connectWithRefreshToken(plan.servers, plan.account)
        }
    }

    private suspend fun <T> retryRecoverableRequest(block: suspend () -> T): T {
        var shouldRetry = true
        while (true) {
            try {
                return block()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                if (!shouldRetry || !isRecoverableConnectionFailure(error)) {
                    throw error
                }
                shouldRetry = false
                reconnectForRetry()
            }
        }
    }

    private fun isRecoverableConnectionFailure(error: Throwable): Boolean =
        when (error) {
            is SteamServiceMethodException -> false
            is SteamAuthenticationException -> false
            is TimeoutCancellationException -> true
            is IOException -> true
            is SteamProtocolException -> true
            else -> false
        }

    private class ConnectionAttempt(val server: CmServer) {
        val opened = CompletableDeferred<Unit>()
        var abandoned = false // guarded by transportLock
        @Volatile var socket: WebSocket? = null
        @Volatile var failure: Throwable? = null
    }

    private sealed interface ReconnectPlan {
        val servers: List<CmServer>

        data class TransportOnly(
            override val servers: List<CmServer>,
        ) : ReconnectPlan

        data class Anonymous(
            override val servers: List<CmServer>,
        ) : ReconnectPlan

        data class RefreshToken(
            override val servers: List<CmServer>,
            val account: SteamAccountSession,
        ) : ReconnectPlan
    }

    private class PendingRequest<T : MessageLite>(
        private val methodName: String,
        private val expectedEmsg: Int,
        private val parser: Parser<T>,
        private val deferred: CompletableDeferred<T>,
    ) {
        fun accepts(emsg: Int): Boolean = emsg == expectedEmsg

        fun complete(packet: SteamPacket) {
            if (packet.header.hasEresult() && packet.header.eresult != 1) {
                deferred.completeExceptionallyIfNeeded(
                    SteamServiceMethodException(
                        methodName = methodName,
                        resultCode = packet.header.eresult,
                        steamMessage = if (packet.header.hasErrorMessage()) packet.header.errorMessage else null,
                    ),
                )
                return
            }
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

    private fun ByteArray.toUnsignedInt(): Int {
        require(size == 4) { "IPv4 address must contain exactly 4 bytes" }
        return ((this[0].toInt() and 0xFF) shl 24) or
            ((this[1].toInt() and 0xFF) shl 16) or
            ((this[2].toInt() and 0xFF) shl 8) or
            (this[3].toInt() and 0xFF)
    }

    private companion object {
        private const val OBFUSCATION_MASK = 0xBAADF00D.toInt()
        private const val REQUEST_TIMEOUT_MS = DEFAULT_HTTP_TIMEOUT_SECONDS * 1_000L
        private const val MAX_CONNECTION_CANDIDATES = 12
        private const val MAX_LOGON_ATTEMPTS = 2
    }
}

private fun <T> CompletableDeferred<T>.completeIfNeeded(value: T) {
    if (!isCompleted) {
        complete(value)
    }
}

private fun <T> CompletableDeferred<T>.completeExceptionallyIfNeeded(error: Throwable) {
    if (!isCompleted) {
        completeExceptionally(error)
    }
}
