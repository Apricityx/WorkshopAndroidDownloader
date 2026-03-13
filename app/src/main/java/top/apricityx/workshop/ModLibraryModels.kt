package top.apricityx.workshop

import kotlinx.serialization.Serializable

@Serializable
data class DownloadedModEntry(
    val appId: UInt,
    val publishedFileId: ULong,
    val gameTitle: String,
    val itemTitle: String,
    val previewImagePath: String? = null,
    val storedAtMillis: Long,
    val files: List<ExportedDownloadFile>,
)

fun DownloadedModEntry.primaryFile(): ExportedDownloadFile? =
    files.sortedBy(ExportedDownloadFile::relativePath).firstOrNull()

fun DownloadedModEntry.matches(
    appId: UInt,
    publishedFileId: ULong,
): Boolean = this.appId == appId && this.publishedFileId == publishedFileId
