package top.apricityx.workshop.steam.protocol

import java.io.Closeable
import java.time.Instant

data class CmServer(
    val endpoint: String,
    val type: String,
    val websocketUri: String = SteamPacketCodec.buildWebSocketUri(endpoint),
)

data class CdnServer(
    val type: String,
    val sourceId: Int,
    val cellId: Int,
    val load: Int,
    val weightedLoad: Float,
    val numEntriesInClientList: Int,
    val steamChinaOnly: Boolean,
    val host: String,
    val vHost: String,
    val useAsProxy: Boolean,
    val proxyRequestPathTemplate: String?,
    val httpsSupport: String,
    val allowedAppIds: List<UInt>,
    val priorityClass: UInt,
) {
    val port: Int = if (httpsSupport == "mandatory") 443 else 80
    val secureScheme: String = if (httpsSupport == "mandatory") "https" else "http"
}

data class SessionContext(
    val sessionId: Int,
    val steamId: Long,
    val cellId: UInt,
    val heartbeatSeconds: Int,
)

data class CdnAuthToken(
    val token: String,
    val expiration: Instant,
)

data class SteamPacket(
    val emsg: Int,
    val header: top.apricityx.workshop.steam.proto.CMsgProtoBufHeader,
    val body: ByteArray,
)

class SteamProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface SteamCmSession : Closeable {
    suspend fun connectAnonymous(servers: List<CmServer>): SessionContext
    suspend fun <T : com.google.protobuf.MessageLite> callServiceMethod(
        methodName: String,
        request: com.google.protobuf.MessageLite,
        parser: com.google.protobuf.Parser<T>,
    ): T
    suspend fun requestDepotDecryptionKey(appId: UInt, depotId: UInt): ByteArray

    val currentSession: kotlinx.coroutines.flow.StateFlow<SessionContext?>
}
