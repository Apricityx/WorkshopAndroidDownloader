package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.apricityx.workshop.workshop.DownloadState

class DownloadForegroundNotificationStateTest {
    @Test
    fun snapshot_isInactive_whenNoQueuedOrRunningTasksRemain() {
        val state = DownloadCenterUiState(
            tasks = listOf(
                buildTask(id = "paused", status = DownloadCenterTaskStatus.Paused),
                buildTask(id = "success", status = DownloadCenterTaskStatus.Success),
            ),
        )

        val snapshot = state.toForegroundNotificationSnapshot()

        assertThat(snapshot.isActive).isFalse()
    }

    @Test
    fun snapshot_usesRunningTaskProgress_forForegroundNotification() {
        val state = DownloadCenterUiState(
            tasks = listOf(
                buildTask(
                    id = "running",
                    itemTitle = "Main Mod",
                    status = DownloadCenterTaskStatus.Running,
                    phase = DownloadState.Downloading,
                    progress = DownloadCenterProgressSnapshot(
                        writtenBytes = 512L,
                        totalBytes = 1024L,
                    ),
                ),
            ),
        )

        val snapshot = state.toForegroundNotificationSnapshot()

        assertThat(snapshot.isActive).isTrue()
        assertThat(snapshot.title).isEqualTo("后台下载中")
        assertThat(snapshot.text).isEqualTo("Game / Main Mod")
        assertThat(snapshot.subText).isEqualTo("运行中 1 个")
        assertThat(snapshot.progressIndeterminate).isFalse()
        assertThat(snapshot.progress).isEqualTo(50)
        assertThat(snapshot.lines).containsExactly("Main Mod · 下载中 · 50.0% · 512 B / 1.0 KB · 账号 匿名")
    }

    @Test
    fun snapshot_summarizesMultipleForegroundTasks_andIgnoresPausedOnes() {
        val state = DownloadCenterUiState(
            tasks = listOf(
                buildTask(
                    id = "queued",
                    itemTitle = "Queued Mod",
                    status = DownloadCenterTaskStatus.Queued,
                ),
                buildTask(
                    id = "running",
                    itemTitle = "Running Mod",
                    status = DownloadCenterTaskStatus.Running,
                    phase = DownloadState.Connecting,
                ),
                buildTask(
                    id = "paused",
                    itemTitle = "Paused Mod",
                    status = DownloadCenterTaskStatus.Paused,
                ),
            ),
        )

        val snapshot = state.toForegroundNotificationSnapshot()

        assertThat(snapshot.isActive).isTrue()
        assertThat(snapshot.title).isEqualTo("后台下载中（2 个任务）")
        assertThat(snapshot.text).isEqualTo("Game / Running Mod")
        assertThat(snapshot.subText).isEqualTo("运行中 1 个 · 排队 1 个")
        assertThat(snapshot.progressIndeterminate).isTrue()
        assertThat(snapshot.lines).containsExactly(
            "Running Mod · 连接内容服务器 · 账号 匿名",
            "Queued Mod · 排队中，等待开始下载 · 账号 匿名",
        ).inOrder()
    }

    private fun buildTask(
        id: String,
        itemTitle: String = id,
        status: DownloadCenterTaskStatus,
        phase: DownloadState = DownloadState.Idle,
        progress: DownloadCenterProgressSnapshot = DownloadCenterProgressSnapshot(),
    ): DownloadCenterTaskUiState =
        DownloadCenterTaskUiState(
            id = id,
            appId = 646570u,
            publishedFileId = id.length.toULong(),
            gameTitle = "Game",
            itemTitle = itemTitle,
            status = status,
            phase = phase,
            progress = progress,
        )
}
