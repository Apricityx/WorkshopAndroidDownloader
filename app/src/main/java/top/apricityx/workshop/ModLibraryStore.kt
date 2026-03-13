package top.apricityx.workshop

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModLibraryStore(
    private val file: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
) {
    fun loadEntries(): List<DownloadedModEntry> {
        if (!file.isFile) {
            return emptyList()
        }

        return runCatching {
            json.decodeFromString(PersistedModLibrary.serializer(), file.readText()).entries
        }.getOrDefault(emptyList())
    }

    fun saveEntries(entries: List<DownloadedModEntry>) {
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(
            json.encodeToString(
                PersistedModLibrary.serializer(),
                PersistedModLibrary(entries = entries),
            ),
        )

        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    @kotlinx.serialization.Serializable
    private data class PersistedModLibrary(
        val entries: List<DownloadedModEntry> = emptyList(),
    )
}
