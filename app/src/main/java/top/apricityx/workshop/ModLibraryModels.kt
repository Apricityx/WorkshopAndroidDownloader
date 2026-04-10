package top.apricityx.workshop

import kotlinx.serialization.Serializable
import kotlin.LazyThreadSafetyMode

@Serializable
data class DownloadedModEntry(
    val appId: UInt,
    val publishedFileId: ULong,
    val gameTitle: String,
    val itemTitle: String,
    val description: String = "",
    val changeNotes: String = "",
    val previewImagePath: String? = null,
    val versionId: String = LEGACY_MOD_VERSION_ID,
    val versionUpdatedAtMillis: Long? = null,
    val storedAtMillis: Long,
    val files: List<ExportedDownloadFile>,
) {
    internal val cachedVersionLabel: String by lazy(LazyThreadSafetyMode.NONE) {
        formatModVersionLabel(
            versionId = versionId,
            updatedAtMillis = versionUpdatedAtMillis,
        )
    }
    internal val normalizedVersionId: String by lazy(LazyThreadSafetyMode.NONE) {
        versionId.lowercase()
    }
    internal val normalizedVersionLabel: String by lazy(LazyThreadSafetyMode.NONE) {
        cachedVersionLabel.lowercase()
    }
}

data class DownloadedModGroup(
    val appId: UInt,
    val publishedFileId: ULong,
    val gameTitle: String,
    val itemTitle: String,
    val description: String = "",
    val changeNotes: String = "",
    val previewImagePath: String? = null,
    val versions: List<DownloadedModEntry>,
) {
    internal val cachedLatestVersion: DownloadedModEntry by lazy(LazyThreadSafetyMode.NONE) {
        versions.first()
    }
    internal val cachedLatestVersionKey: String by lazy(LazyThreadSafetyMode.NONE) {
        cachedLatestVersion.modLibraryKey()
    }
    internal val normalizedGameTitle: String by lazy(LazyThreadSafetyMode.NONE) {
        gameTitle.lowercase()
    }
    internal val normalizedItemTitle: String by lazy(LazyThreadSafetyMode.NONE) {
        itemTitle.lowercase()
    }
    internal val cachedTotalFileCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        versions.sumOf { it.files.size }
    }
    internal val cachedSearchIndex: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString {
            append(normalizedItemTitle)
            append('\n')
            append(normalizedGameTitle)
            append('\n')
            append(appId)
            append('\n')
            append(publishedFileId)
            versions.forEach { version ->
                append('\n')
                append(version.normalizedVersionId)
                append('\n')
                append(version.normalizedVersionLabel)
            }
        }
    }
}

fun DownloadedModEntry.primaryFile(): ExportedDownloadFile? =
    files.sortedBy(ExportedDownloadFile::relativePath).firstOrNull()

fun DownloadedModEntry.modGroupKey(): String =
    "${appId}-${publishedFileId}"

fun DownloadedModGroup.modGroupKey(): String =
    "${appId}-${publishedFileId}"

fun DownloadedModGroup.latestVersion(): DownloadedModEntry =
    cachedLatestVersion

fun DownloadedModGroup.primaryFile(): ExportedDownloadFile? =
    latestVersion().primaryFile()

fun DownloadedModGroup.versionCount(): Int =
    versions.size

fun DownloadedModGroup.totalFileCount(): Int =
    cachedTotalFileCount

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
    cachedVersionLabel

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
                description = sortedVersions
                    .mapNotNull { it.description.takeIf(String::isNotBlank) }
                    .firstOrNull()
                    .orEmpty(),
                changeNotes = sortedVersions
                    .mapNotNull { it.changeNotes.takeIf(String::isNotBlank) }
                    .firstOrNull()
                    .orEmpty(),
                previewImagePath = sortedVersions
                    .mapNotNull { it.previewImagePath?.takeIf(String::isNotBlank) }
                    .firstOrNull(),
                versions = sortedVersions,
            )
        }
        .sortedWith(downloadedModGroupDisplayComparator)

fun List<DownloadedModGroup>.latestVersionsForUpdateCheck(): List<DownloadedModEntry> =
    map(DownloadedModGroup::latestVersion)

fun List<DownloadedModGroup>.downloadedPublishedFileIds(appId: UInt? = null): Set<ULong> =
    asSequence()
        .filter { group -> appId == null || group.appId == appId }
        .map(DownloadedModGroup::publishedFileId)
        .toSet()

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
