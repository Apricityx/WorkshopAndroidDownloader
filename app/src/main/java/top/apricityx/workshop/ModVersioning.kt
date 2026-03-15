package top.apricityx.workshop

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val LEGACY_MOD_VERSION_ID = "legacy"

private val modVersionTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

data class WorkshopModVersion(
    val versionId: String,
    val updatedAtMillis: Long?,
)

fun resolveWorkshopModVersion(metadata: WorkshopDownloadMetadata?): WorkshopModVersion {
    val updatedAtMillis = metadata?.timeUpdatedEpochSeconds
        ?.takeIf { it > 0L }
        ?.times(1000L)
    return WorkshopModVersion(
        versionId = buildModVersionId(updatedAtMillis),
        updatedAtMillis = updatedAtMillis,
    )
}

fun buildModVersionId(updatedAtMillis: Long?): String =
    updatedAtMillis?.let { "updated-${it / 1000L}" } ?: LEGACY_MOD_VERSION_ID

fun parseModVersionUpdatedAtMillis(versionId: String): Long? =
    versionId.removePrefix("updated-")
        .takeIf { versionId.startsWith("updated-") }
        ?.toLongOrNull()
        ?.times(1000L)

fun normalizeModVersionId(versionId: String?): String =
    versionId?.trim().orEmpty().ifBlank { LEGACY_MOD_VERSION_ID }

fun formatModVersionLabel(
    versionId: String,
    updatedAtMillis: Long?,
): String =
    updatedAtMillis?.let { "工坊更新 ${formatModVersionTimestamp(it)}" }
        ?: if (normalizeModVersionId(versionId) == LEGACY_MOD_VERSION_ID) {
            "旧版导入"
        } else {
            versionId
        }

fun formatModVersionTimestamp(timestampMillis: Long): String =
    modVersionTimeFormatter.format(
        Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()),
    )
