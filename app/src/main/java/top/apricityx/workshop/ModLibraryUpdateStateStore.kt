package top.apricityx.workshop

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModLibraryUpdateStateStore(
    private val file: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
) {
    fun loadState(): ModLibraryUpdateCheckState {
        if (!file.isFile) {
            return ModLibraryUpdateCheckState()
        }

        return runCatching {
            val persisted = json.decodeFromString(PersistedModLibraryUpdateState.serializer(), file.readText())
            persisted.state.normalizedForPersistence()
        }.getOrDefault(ModLibraryUpdateCheckState())
    }

    fun saveState(state: ModLibraryUpdateCheckState) {
        val normalized = state.normalizedForPersistence()
        val parent = file.parentFile ?: error("Mod update state path must have a parent directory.")
        parent.mkdirs()
        val tempFile = File.createTempFile(tempPrefix(file), ".tmp", parent)
        try {
            tempFile.writeText(
                json.encodeToString(
                    PersistedModLibraryUpdateState.serializer(),
                    PersistedModLibraryUpdateState(state = normalized),
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

    @kotlinx.serialization.Serializable
    private data class PersistedModLibraryUpdateState(
        val state: ModLibraryUpdateCheckState = ModLibraryUpdateCheckState(),
    )

    companion object {
        private fun tempPrefix(file: File): String =
            file.nameWithoutExtension.ifBlank { file.name }.padEnd(3, '_')
    }
}

private fun ModLibraryUpdateCheckState.normalizedForPersistence(): ModLibraryUpdateCheckState {
    val stableResults = results.filterValues { result ->
        result.status != ModUpdateCheckStatus.Checking && result.status != ModUpdateCheckStatus.Unknown
    }
    return copy(
        isChecking = false,
        summaryMessage = stableResults.values.takeIf { it.isNotEmpty() }?.let(::buildModUpdateCheckSummary),
        results = stableResults,
    )
}
