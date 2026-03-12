package top.apricityx.workshop.update

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.LinkedHashSet
import java.util.Locale

enum class UpdateSource(
    val id: String,
    val displayName: String,
    private val proxyPrefix: String?,
    val supportsMetadataCheck: Boolean,
    val supportsDownloadProxy: Boolean,
    val userSelectable: Boolean,
) {
    GH_PROXY_COM(
        id = "gh_proxy_com",
        displayName = "gh-proxy.com",
        proxyPrefix = "https://gh-proxy.com/",
        supportsMetadataCheck = false,
        supportsDownloadProxy = true,
        userSelectable = true,
    ),
    GH_PROXY_VIP(
        id = "ghproxy_vip",
        displayName = "ghproxy.vip",
        proxyPrefix = "https://ghproxy.vip/",
        supportsMetadataCheck = true,
        supportsDownloadProxy = true,
        userSelectable = true,
    ),
    GH_LLKK(
        id = "gh_llkk",
        displayName = "gh.llkk.cc",
        proxyPrefix = "https://gh.llkk.cc/",
        supportsMetadataCheck = true,
        supportsDownloadProxy = true,
        userSelectable = true,
    ),
    GH_PROXY_NET(
        id = "ghproxy_net",
        displayName = "ghproxy.net",
        proxyPrefix = "https://ghproxy.net/",
        supportsMetadataCheck = false,
        supportsDownloadProxy = true,
        userSelectable = false,
    ),
    OFFICIAL(
        id = "official",
        displayName = "GitHub",
        proxyPrefix = null,
        supportsMetadataCheck = true,
        supportsDownloadProxy = true,
        userSelectable = false,
    ),
    ;

    fun buildUrl(targetUrl: String): String =
        proxyPrefix?.plus(targetUrl) ?: targetUrl

    companion object {
        val DEFAULT_PREFERRED_USER_SOURCE: UpdateSource = GH_PROXY_COM

        fun fromPersistedValue(value: String?): UpdateSource? =
            entries.firstOrNull { it.id == value }

        fun userSelectableSources(): List<UpdateSource> =
            entries.filter { it.userSelectable }

        fun normalizePreferredUserSource(value: String?): UpdateSource {
            val stored = fromPersistedValue(value)
            return if (stored != null && stored.userSelectable) {
                stored
            } else {
                DEFAULT_PREFERRED_USER_SOURCE
            }
        }

        fun metadataCandidates(preferredUserSource: UpdateSource): List<UpdateSource> {
            val preferred = normalizePreferredUserSource(preferredUserSource.id)
            val ordered = LinkedHashSet<UpdateSource>()
            ordered += preferred
            userSelectableSources()
                .filter { it != preferred && it.supportsMetadataCheck }
                .forEach(ordered::add)
            ordered += OFFICIAL
            return ordered.filter { it.supportsMetadataCheck }
        }

        fun downloadCandidates(
            preferredUserSource: UpdateSource,
            metadataSource: UpdateSource,
        ): List<UpdateSource> {
            val preferred = normalizePreferredUserSource(preferredUserSource.id)
            val ordered = LinkedHashSet<UpdateSource>()
            if (preferred.supportsDownloadProxy) {
                ordered += preferred
            }
            if (metadataSource.supportsDownloadProxy) {
                ordered += metadataSource
            }
            userSelectableSources()
                .filter { it.supportsDownloadProxy && it != preferred }
                .forEach(ordered::add)
            ordered += GH_PROXY_NET
            ordered += OFFICIAL
            return ordered.filter { it.supportsDownloadProxy }
        }
    }
}

data class UpdateReleaseInfo(
    val rawTagName: String,
    val normalizedVersion: String,
    val publishedAtRaw: String?,
    val publishedAtDisplayText: String,
    val notesText: String,
    val assetName: String,
    val assetDownloadUrl: String,
)

data class UpdateDownloadResolution(
    val source: UpdateSource,
    val resolvedUrl: String,
)

sealed interface UpdateCheckExecutionResult {
    data class Success(
        val currentVersion: String,
        val release: UpdateReleaseInfo,
        val metadataSource: UpdateSource,
        val downloadResolution: UpdateDownloadResolution?,
        val hasUpdate: Boolean,
    ) : UpdateCheckExecutionResult

    data class Failure(
        val errorSummary: String,
        val release: UpdateReleaseInfo? = null,
        val metadataSource: UpdateSource? = null,
    ) : UpdateCheckExecutionResult
}

enum class UpdateUiMessage {
    LATEST,
    FAILURE,
}

data class UpdateUiDecision(
    val showPrompt: Boolean,
    val message: UpdateUiMessage?,
)

object WorkshopUpdateUiReducer {
    fun reduce(
        result: UpdateCheckExecutionResult,
        userInitiated: Boolean,
    ): UpdateUiDecision {
        return when (result) {
            is UpdateCheckExecutionResult.Success -> {
                if (result.hasUpdate) {
                    UpdateUiDecision(showPrompt = true, message = null)
                } else {
                    UpdateUiDecision(
                        showPrompt = false,
                        message = if (userInitiated) UpdateUiMessage.LATEST else null,
                    )
                }
            }

            is UpdateCheckExecutionResult.Failure -> UpdateUiDecision(
                showPrompt = false,
                message = if (userInitiated) UpdateUiMessage.FAILURE else null,
            )
        }
    }
}

object WorkshopUpdateVersioning {
    private val releaseVersionPattern =
        Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-hotfix(\d+))?$""")
    private val publishedAtFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

    fun normalizeVersionTag(value: String): String =
        value.trim().removePrefix("v").removePrefix("V")

    fun isRemoteNewer(currentVersion: String, remoteVersionTag: String): Boolean {
        val normalizedCurrent = normalizeVersionTag(currentVersion)
        val normalizedRemote = normalizeVersionTag(remoteVersionTag)
        val currentParsed = parseReleaseVersion(normalizedCurrent)
        val remoteParsed = parseReleaseVersion(normalizedRemote)
        if (currentParsed != null && remoteParsed != null) {
            return currentParsed < remoteParsed
        }
        return normalizedCurrent != normalizedRemote
    }

    fun formatPublishedAt(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return ""
        }
        return runCatching {
            Instant.parse(normalized)
                .atZone(ZoneId.systemDefault())
                .format(publishedAtFormatter)
        }.getOrElse { normalized }
    }

    fun normalizeReleaseNotesText(value: String?): String {
        return value
            .orEmpty()
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
    }

    private data class ParsedReleaseVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val hotfix: Int,
    ) : Comparable<ParsedReleaseVersion> {
        override fun compareTo(other: ParsedReleaseVersion): Int {
            return compareValuesBy(
                this,
                other,
                ParsedReleaseVersion::major,
                ParsedReleaseVersion::minor,
                ParsedReleaseVersion::patch,
                ParsedReleaseVersion::hotfix,
            )
        }
    }

    private fun parseReleaseVersion(value: String): ParsedReleaseVersion? {
        val match = releaseVersionPattern.matchEntire(value) ?: return null
        return ParsedReleaseVersion(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toIntOrNull() ?: 0,
            patch = match.groupValues[3].toIntOrNull() ?: 0,
            hotfix = match.groupValues[4].toIntOrNull() ?: 0,
        )
    }
}
