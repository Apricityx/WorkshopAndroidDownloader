package top.apricityx.workshop

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ParsedJwtInfo(
    val steamId: Long? = null,
    val tokenId: ULong? = null,
    val expiresAtEpochSeconds: Long? = null,
)

fun buildSteamLoginSecureCookie(
    steamId: Long,
    accessToken: String,
): String = "steamLoginSecure=${steamId}||$accessToken"

fun parseSteamJwtInfo(token: String): ParsedJwtInfo {
    val parts = token.split('.')
    if (parts.size < 2) {
        return ParsedJwtInfo()
    }
    val payload = runCatching {
        val normalized = parts[1]
            .replace('-', '+')
            .replace('_', '/')
            .padEnd((parts[1].length + 3) / 4 * 4, '=')
        Base64.getDecoder().decode(normalized).decodeToString()
    }.getOrNull() ?: return ParsedJwtInfo()

    val json = Json.Default.parseToJsonElement(payload).jsonObject
    return ParsedJwtInfo(
        steamId = json["sub"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        tokenId = json["jti"]?.jsonPrimitive?.contentOrNull?.toULongOrNull(),
        expiresAtEpochSeconds = json["exp"]?.jsonPrimitive?.longOrNull,
    )
}
