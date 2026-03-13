package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.apricityx.workshop.workshop.DownloadState
import java.io.File
import java.nio.file.Files

class DownloadCenterStoreTest {
    @Test
    fun saveAndLoad_roundTripsTasks() {
        val tempDir = Files.createTempDirectory("download-center-store").toFile()
        val store = DownloadCenterStore(File(tempDir, "tasks.json"))
        val tasks = listOf(
            DownloadCenterTaskUiState(
                id = "task-1",
                appId = 646570u,
                publishedFileId = 3677098410uL,
                gameTitle = "Slay the Spire",
                itemTitle = "Skip The Spire",
                status = DownloadCenterTaskStatus.Success,
                phase = DownloadState.Success,
                logs = listOf("done"),
                files = listOf(
                    ExportedDownloadFile(
                        relativePath = "Skip The Spire.jar",
                        sizeBytes = 42L,
                        modifiedEpochMillis = 1234L,
                        contentUri = "content://downloads/1",
                        userVisiblePath = "Download/workshop/Slay the Spire/Skip The Spire/Skip The Spire.jar",
                    ),
                ),
                progress = DownloadCenterProgressSnapshot(
                    writtenBytes = 42L * 1024L * 1024L,
                    totalBytes = 42L * 1024L * 1024L,
                    completedFiles = 1,
                    totalFiles = 1,
                ),
                enqueuedAtMillis = 1L,
                updatedAtMillis = 2L,
            ),
        )

        store.saveTasks(tasks)

        assertThat(store.loadTasks()).isEqualTo(tasks)
        tempDir.deleteRecursively()
    }

    @Test
    fun loadTasks_returnsEmptyList_whenFileMissing() {
        val tempDir = Files.createTempDirectory("download-center-store-empty").toFile()
        val store = DownloadCenterStore(File(tempDir, "missing.json"))

        assertThat(store.loadTasks()).isEmpty()
        tempDir.deleteRecursively()
    }
}
