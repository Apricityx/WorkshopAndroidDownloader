package top.apricityx.workshop

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModLibraryStore(
    private val file: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
) {
    private val fileMutex = mutexFor(file)

    fun loadEntries(): List<DownloadedModEntry> {
        if (!file.isFile) {
            return emptyList()
        }

        return runCatching {
            json.decodeFromString(PersistedModLibrary.serializer(), file.readText()).entries
        }.getOrDefault(emptyList())
    }

    fun saveEntries(entries: List<DownloadedModEntry>) {
        val parent = file.parentFile ?: error("Mod library index path must have a parent directory.")
        parent.mkdirs()
        val tempFile = File.createTempFile(tempPrefix(file), ".tmp", parent)
        try {
            tempFile.writeText(
                json.encodeToString(
                    PersistedModLibrary.serializer(),
                    PersistedModLibrary(entries = entries),
                ),
            )
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun <T> withFileLock(action: suspend () -> T): T =
        fileMutex.withLock { action() }

    @kotlinx.serialization.Serializable
    private data class PersistedModLibrary(
        val entries: List<DownloadedModEntry> = emptyList(),
    )

    companion object {
        private val mutexRegistry = ConcurrentHashMap<String, Mutex>()

        private fun mutexFor(file: File): Mutex =
            mutexRegistry.getOrPut(file.absolutePath) { Mutex() }

        private fun tempPrefix(file: File): String =
            file.nameWithoutExtension.ifBlank { file.name }.padEnd(3, '_')
    }
}
