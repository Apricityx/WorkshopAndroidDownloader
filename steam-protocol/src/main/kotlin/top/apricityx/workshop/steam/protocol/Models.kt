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

data class SteamAccountSession(
    val accountName: String,
    val steamId: Long,
    val refreshToken: String,
    val shouldRememberPassword: Boolean = true,
    val machineName: String = DEFAULT_MACHINE_NAME,
)

data class SteamAuthSessionDetails(
    val username: String,
    val password: String,
    val guardData: String? = null,
    val isPersistentSession: Boolean = true,
    val deviceFriendlyName: String = DEFAULT_MACHINE_NAME,
    val websiteId: String = DEFAULT_WEBSITE_ID,
    val clientOsType: Int = DEFAULT_CLIENT_OS_TYPE,
)

data class SteamGuardChallenge(
    val type: SteamGuardChallengeType,
    val message: String? = null,
)

enum class SteamGuardChallengeType {
    None,
    EmailCode,
    DeviceCode,
    DeviceConfirmation,
    EmailConfirmation,
    MachineToken,
    LegacyMachineAuth,
    Unknown,
}

data class SteamAuthPollResult(
    val steamId: Long,
    val accountName: String,
    val refreshToken: String,
    val accessToken: String,
    val newGuardData: String? = null,
)

data class SteamWebAccessTokens(
    val accessToken: String,
    val refreshToken: String? = null,
)

data class SteamPacket(
    val emsg: Int,
    val header: top.apricityx.workshop.steam.proto.CMsgProtoBufHeader,
    val body: ByteArray,
)

open class SteamProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SteamServiceMethodException(
    val methodName: String,
    val resultCode: Int,
    val steamMessage: String?,
    cause: Throwable? = null,
) : SteamProtocolException(
    message = buildString {
        append("Steam service request failed: ")
        append(methodName)
        append(" EResult=")
        append(resultCode)
        if (!steamMessage.isNullOrBlank()) {
            append(" (")
            append(steamMessage)
            append(")")
        }
    },
    cause = cause,
)

class SteamAuthenticationException(
    val resultCode: Int,
    message: String,
    cause: Throwable? = null,
) : SteamProtocolException(message, cause)

interface SteamCmSession : Closeable {
    suspend fun connect(servers: List<CmServer>)
    suspend fun connectAnonymous(servers: List<CmServer>): SessionContext
    suspend fun connectWithRefreshToken(
        servers: List<CmServer>,
        account: SteamAccountSession,
    ): SessionContext
    suspend fun <T : com.google.protobuf.MessageLite> callServiceMethod(
        methodName: String,
        request: com.google.protobuf.MessageLite,
        parser: com.google.protobuf.Parser<T>,
    ): T
    suspend fun requestDepotDecryptionKey(appId: UInt, depotId: UInt): ByteArray

    val currentSession: kotlinx.coroutines.flow.StateFlow<SessionContext?>
}

const val DEFAULT_MACHINE_NAME = "Android Workshop"
const val DEFAULT_WEBSITE_ID = "Client"
const val DEFAULT_CLIENT_OS_TYPE = -500
