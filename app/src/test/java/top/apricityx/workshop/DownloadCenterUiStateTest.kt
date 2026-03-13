package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadCenterUiStateTest {
    @Test
    fun counts_areDerivedFromTaskStatuses() {
        val state = DownloadCenterUiState(
            tasks = listOf(
                DownloadCenterTaskUiState(
                    id = "1",
                    appId = 646570u,
                    publishedFileId = 1uL,
                    gameTitle = "A",
                    itemTitle = "Queued",
                    status = DownloadCenterTaskStatus.Queued,
                ),
                DownloadCenterTaskUiState(
                    id = "2",
                    appId = 646570u,
                    publishedFileId = 2uL,
                    gameTitle = "A",
                    itemTitle = "Running",
                    status = DownloadCenterTaskStatus.Running,
                ),
                DownloadCenterTaskUiState(
                    id = "3",
                    appId = 646570u,
                    publishedFileId = 3uL,
                    gameTitle = "A",
                    itemTitle = "Paused",
                    status = DownloadCenterTaskStatus.Paused,
                ),
                DownloadCenterTaskUiState(
                    id = "4",
                    appId = 646570u,
                    publishedFileId = 4uL,
                    gameTitle = "A",
                    itemTitle = "Success",
                    status = DownloadCenterTaskStatus.Success,
                ),
                DownloadCenterTaskUiState(
                    id = "5",
                    appId = 646570u,
                    publishedFileId = 5uL,
                    gameTitle = "A",
                    itemTitle = "Failed",
                    status = DownloadCenterTaskStatus.Failed,
                ),
            ),
        )

        assertThat(state.queuedCount).isEqualTo(1)
        assertThat(state.runningCount).isEqualTo(1)
        assertThat(state.pausedCount).isEqualTo(1)
        assertThat(state.finishedCount).isEqualTo(2)
        assertThat(state.activeCount).isEqualTo(2)
    }

    @Test
    fun failedTask_usesRetryActionLabel() {
        val failedTask = DownloadCenterTaskUiState(
            id = "failed",
            appId = 431960u,
            publishedFileId = 3679122549uL,
            gameTitle = "Wallpaper Engine",
            itemTitle = "Need Login",
            status = DownloadCenterTaskStatus.Failed,
        )
        val pausedTask = failedTask.copy(
            id = "paused",
            status = DownloadCenterTaskStatus.Paused,
        )

        assertThat(failedTask.resumeActionLabel()).isEqualTo("重试下载")
        assertThat(pausedTask.resumeActionLabel()).isEqualTo("继续下载")
    }
}
