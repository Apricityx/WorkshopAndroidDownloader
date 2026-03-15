package top.apricityx.workshop

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WorkshopDownloadMetadata(
    val title: String,
    val filename: String,
    val previewImageUrl: String,
    val timeUpdatedEpochSeconds: Long?,
)

fun readWorkshopDownloadMetadata(stagingDir: File): WorkshopDownloadMetadata? {
    val metadataFile = File(stagingDir, "metadata.json")
    if (!metadataFile.isFile) {
        return null
    }

    return runCatching {
        val details = Json.parseToJsonElement(metadataFile.readText())
            .jsonObject["response"]
            ?.jsonObject
            ?.get("publishedfiledetails")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: error("Workshop download metadata was empty")
        WorkshopDownloadMetadata(
            title = details["title"]?.jsonPrimitive?.content.orEmpty().trim(),
            filename = details["filename"]?.jsonPrimitive?.content.orEmpty().trim(),
            previewImageUrl = details["preview_url"]?.jsonPrimitive?.content.orEmpty().trim(),
            timeUpdatedEpochSeconds = details["time_updated"]?.jsonPrimitive?.content?.toLongOrNull(),
        )
    }.getOrNull()
}
