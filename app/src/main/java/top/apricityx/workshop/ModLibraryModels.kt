package top.apricityx.workshop

import kotlinx.serialization.Serializable

@Serializable
data class DownloadedModEntry(
    val appId: UInt,
    val publishedFileId: ULong,
    val gameTitle: String,
    val itemTitle: String,
    val previewImagePath: String? = null,
    val versionId: String = LEGACY_MOD_VERSION_ID,
    val versionUpdatedAtMillis: Long? = null,
    val storedAtMillis: Long,
    val files: List<ExportedDownloadFile>,
)

data class DownloadedModGroup(
    val appId: UInt,
    val publishedFileId: ULong,
    val gameTitle: String,
    val itemTitle: String,
    val previewImagePath: String? = null,
    val versions: List<DownloadedModEntry>,
)

fun DownloadedModEntry.primaryFile(): ExportedDownloadFile? =
    files.sortedBy(ExportedDownloadFile::relativePath).firstOrNull()

fun DownloadedModEntry.modGroupKey(): String =
    "${appId}-${publishedFileId}"

fun DownloadedModGroup.modGroupKey(): String =
    "${appId}-${publishedFileId}"

fun DownloadedModGroup.latestVersion(): DownloadedModEntry =
    versions.first()

fun DownloadedModGroup.primaryFile(): ExportedDownloadFile? =
    latestVersion().primaryFile()

fun DownloadedModGroup.versionCount(): Int =
    versions.size

fun DownloadedModGroup.totalFileCount(): Int =
    versions.sumOf { it.files.size }

fun DownloadedModGroup.matches(
    appId: UInt,
    publishedFileId: ULong,
): Boolean =
    this.appId == appId && this.publishedFileId == publishedFileId

fun DownloadedModGroup.matches(other: DownloadedModGroup): Boolean =
    matches(
        appId = other.appId,
        publishedFileId = other.publishedFileId,
    )

fun DownloadedModEntry.matches(
    appId: UInt,
    publishedFileId: ULong,
    versionId: String = LEGACY_MOD_VERSION_ID,
): Boolean =
    this.appId == appId &&
        this.publishedFileId == publishedFileId &&
        normalizeModVersionId(this.versionId) == normalizeModVersionId(versionId)

fun DownloadedModEntry.matches(other: DownloadedModEntry): Boolean =
    matches(
        appId = other.appId,
        publishedFileId = other.publishedFileId,
        versionId = other.versionId,
    )

fun DownloadedModEntry.versionLabel(): String =
    formatModVersionLabel(
        versionId = versionId,
        updatedAtMillis = versionUpdatedAtMillis,
    )

fun List<DownloadedModEntry>.groupedForDisplay(): List<DownloadedModGroup> =
    groupBy(DownloadedModEntry::modGroupKey)
        .values
        .map { versions ->
            val sortedVersions = versions.sortedWith(downloadedModEntryDisplayComparator)
            val latestVersion = sortedVersions.first()
            DownloadedModGroup(
                appId = latestVersion.appId,
                publishedFileId = latestVersion.publishedFileId,
                gameTitle = latestVersion.gameTitle,
                itemTitle = latestVersion.itemTitle,
                previewImagePath = sortedVersions
                    .mapNotNull { it.previewImagePath?.takeIf(String::isNotBlank) }
                    .firstOrNull(),
                versions = sortedVersions,
            )
        }
        .sortedWith(downloadedModGroupDisplayComparator)

private val downloadedModEntryDisplayComparator =
    compareByDescending<DownloadedModEntry> { it.storedAtMillis }
        .thenByDescending { it.versionUpdatedAtMillis ?: Long.MIN_VALUE }
        .thenBy { it.gameTitle.lowercase() }
        .thenBy { it.itemTitle.lowercase() }

private val downloadedModGroupDisplayComparator =
    compareByDescending<DownloadedModGroup> { it.latestVersion().storedAtMillis }
        .thenByDescending { it.latestVersion().versionUpdatedAtMillis ?: Long.MIN_VALUE }
        .thenBy { it.gameTitle.lowercase() }
        .thenBy { it.itemTitle.lowercase() }
