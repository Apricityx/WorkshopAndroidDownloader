package top.apricityx.workshop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DownloadCenterStore(
    private val file: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
) {
    fun loadTasks(): List<DownloadCenterTaskUiState> {
        if (!file.isFile) {
            return emptyList()
        }

        return runCatching {
            json.decodeFromString(PersistedDownloadCenter.serializer(), file.readText()).tasks
        }.getOrDefault(emptyList())
    }

    fun saveTasks(tasks: List<DownloadCenterTaskUiState>) {
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(
            json.encodeToString(
                PersistedDownloadCenter.serializer(),
                PersistedDownloadCenter(tasks = tasks),
            ),
        )

        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    @kotlinx.serialization.Serializable
    private data class PersistedDownloadCenter(
        val tasks: List<DownloadCenterTaskUiState> = emptyList(),
    )
}
