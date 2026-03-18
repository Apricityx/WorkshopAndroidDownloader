package top.apricityx.workshop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SteamWebLoginContext(
    val steamId: Long,
    val accessToken: String,
    val sessionId: String,
)

@Serializable
internal data class SteamFinalizeLoginResponse(
    @SerialName("steamID")
    val steamId: String? = null,
    @SerialName("primary_domain")
    val primaryDomain: String? = null,
    @SerialName("transfer_info")
    val transferInfo: List<SteamTransferInfo> = emptyList(),
)

@Serializable
internal data class SteamTransferInfo(
    val url: String,
    val params: Map<String, String> = emptyMap(),
)

private val steamWebTransferJson = Json { ignoreUnknownKeys = true }

internal fun parseSteamFinalizeLoginResponse(payload: String): SteamFinalizeLoginResponse {
    val root = steamWebTransferJson.parseToJsonElement(payload).jsonObject
    val transferInfo = root["transfer_info"]
        ?.jsonArray
        ?.mapNotNull { transferElement ->
            val transferObject = transferElement.jsonObject
            val url = transferObject.stringField("url") ?: return@mapNotNull null
            SteamTransferInfo(
                url = url,
                params = transferObject["params"]
                    ?.jsonObject
                    ?.mapNotNull { (key, value) ->
                        value.jsonPrimitive.contentOrNull?.let { content -> key to content }
                    }
                    ?.toMap()
                    .orEmpty(),
            )
        }
        .orEmpty()
    return SteamFinalizeLoginResponse(
        steamId = root.stringField("steamID", "steamid", "steamId"),
        primaryDomain = root.stringField("primary_domain", "primaryDomain"),
        transferInfo = transferInfo,
    )
}

internal fun parseSteamSetTokenResult(payload: String): Int? {
    if (payload.isBlank()) {
        return null
    }
    return runCatching {
        steamWebTransferJson.parseToJsonElement(payload)
            .jsonObject["result"]
            ?.jsonPrimitive
            ?.intOrNull
    }.getOrNull()
}

internal fun sanitizeSteamTransferLoginRedirect(url: String): String =
    url.toHttpUrlOrNull()
        ?.newBuilder()
        ?.apply { removeAllQueryParameters("need_password") }
        ?.build()
        ?.toString()
        ?: url

internal fun summarizeSteamFinalizeLoginPayload(payload: String): String =
    runCatching {
        val root = steamWebTransferJson.parseToJsonElement(payload).jsonObject
        val topLevelKeys = root.keys.sorted()
        val transferInfoCount = root["transfer_info"]?.jsonArray?.size ?: 0
        val steamIdFields = listOf("steamID", "steamid", "steamId")
            .filter { key -> root[key]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }
        val message = root["message"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.replace(Regex("\\s+"), " ")
            ?.take(120)
        buildString {
            append("keys=")
            append(topLevelKeys.joinToString(","))
            append(" transferInfoCount=")
            append(transferInfoCount)
            append(" steamIdFields=")
            append(if (steamIdFields.isEmpty()) "-" else steamIdFields.joinToString(","))
            if (!message.isNullOrBlank()) {
                append(" message=")
                append(message)
            }
        }
    }.getOrElse {
        val preview = payload.replace(Regex("\\s+"), " ").take(160)
        "non-json payload=\"$preview\""
    }

private fun kotlinx.serialization.json.JsonObject.stringField(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }
