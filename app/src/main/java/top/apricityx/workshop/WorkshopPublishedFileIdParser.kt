package top.apricityx.workshop

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object WorkshopPublishedFileIdParser {
    fun parse(input: String): ULong? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return null
        }

        trimmed.toULongOrNull()
            ?.takeIf { it > 0uL }
            ?.let { return it }

        val parsedUrl = normalizeUrl(trimmed).toHttpUrlOrNull() ?: return null
        val normalizedHost = parsedUrl.host.removePrefix("www.")
        val normalizedPath = parsedUrl.encodedPath.trimEnd('/')
        if (!normalizedHost.equals(steamCommunityHost, ignoreCase = true)) {
            return null
        }
        if (normalizedPath !in supportedDetailPaths) {
            return null
        }
        return parsedUrl.queryParameter("id")
            ?.toULongOrNull()
            ?.takeIf { it > 0uL }
    }

    private fun normalizeUrl(input: String): String {
        val sanitized = input.replace("&amp;", "&")
        return when {
            sanitized.startsWith("//") -> "https:$sanitized"
            sanitized.startsWith("steamcommunity.com/", ignoreCase = true) -> "https://$sanitized"
            sanitized.startsWith("www.steamcommunity.com/", ignoreCase = true) -> "https://$sanitized"
            else -> sanitized
        }
    }

    private const val steamCommunityHost = "steamcommunity.com"
    private val supportedDetailPaths = setOf(
        "/sharedfiles/filedetails",
        "/workshop/filedetails",
    )
}
