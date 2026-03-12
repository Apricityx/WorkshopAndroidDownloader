package top.apricityx.workshop

import kotlinx.serialization.Serializable

@Serializable
data class ExportedDownloadFile(
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedEpochMillis: Long,
    val contentUri: String,
    val userVisiblePath: String,
)
