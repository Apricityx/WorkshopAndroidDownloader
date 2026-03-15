package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.apricityx.workshop.workshop.DownloadState

class DownloadCenterTaskFileCleanupTest {
    @Test
    fun clearExportedFilesForMod_clearsOnlyMatchingTasksForSameVersion() {
        val matchingTask = DownloadCenterTaskUiState(
            id = "task-1",
            appId = 646570u,
            publishedFileId = 3677098410uL,
            gameTitle = "Slay the Spire",
            itemTitle = "Skip The Spire",
            status = DownloadCenterTaskStatus.Success,
            phase = DownloadState.Success,
            files = listOf(sampleFile("content://downloads/1")),
        )
        val differentVersionTask = DownloadCenterTaskUiState(
            id = "task-2",
            appId = 646570u,
            publishedFileId = 3677098410uL,
            gameTitle = "Slay the Spire",
            itemTitle = "Skip The Spire",
            status = DownloadCenterTaskStatus.Success,
            phase = DownloadState.Success,
            files = listOf(sampleFile("content://downloads/9", versionId = "updated-1772900999")),
        )
        val otherTask = DownloadCenterTaskUiState(
            id = "task-3",
            appId = 480u,
            publishedFileId = 123uL,
            gameTitle = "Spacewar",
            itemTitle = "Other",
            status = DownloadCenterTaskStatus.Success,
            phase = DownloadState.Success,
            files = listOf(sampleFile("content://downloads/2")),
        )

        val entry = DownloadedModEntry(
            appId = 646570u,
            publishedFileId = 3677098410uL,
            gameTitle = "Slay the Spire",
            itemTitle = "Skip The Spire",
            versionId = "updated-1772900923",
            versionUpdatedAtMillis = 1_772_900_923_000L,
            storedAtMillis = 1234L,
            files = matchingTask.files,
        )

        val updated = DownloadCenterUiState(tasks = listOf(matchingTask, differentVersionTask, otherTask))
            .clearExportedFilesForMod(entry)

        assertThat(updated.tasks.first { it.id == "task-1" }.files).isEmpty()
        assertThat(updated.tasks.first { it.id == "task-2" }.files).hasSize(1)
        assertThat(updated.tasks.first { it.id == "task-3" }.files).hasSize(1)
    }

    private fun sampleFile(
        contentUri: String,
        versionId: String = "updated-1772900923",
    ) = ExportedDownloadFile(
        relativePath = "Skip The Spire.jar",
        sizeBytes = 42L,
        modifiedEpochMillis = 1234L,
        contentUri = contentUri,
        userVisiblePath = "Download/workshop/Slay the Spire/Skip The Spire/$versionId/Skip The Spire.jar",
    )
}
