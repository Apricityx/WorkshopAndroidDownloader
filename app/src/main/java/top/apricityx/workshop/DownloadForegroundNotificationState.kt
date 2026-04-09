package top.apricityx.workshop

import kotlin.math.roundToInt

data class DownloadForegroundNotificationSnapshot(
    val isActive: Boolean,
    val title: String = "",
    val text: String = "",
    val subText: String? = null,
    val lines: List<String> = emptyList(),
    val progress: Int = 0,
    val progressMax: Int = 100,
    val progressIndeterminate: Boolean = true,
)

fun DownloadCenterUiState.toForegroundNotificationSnapshot(): DownloadForegroundNotificationSnapshot {
    val foregroundTasks = displayTasks.filter {
        it.status == DownloadCenterTaskStatus.Running || it.status == DownloadCenterTaskStatus.Queued
    }
    if (foregroundTasks.isEmpty()) {
        return DownloadForegroundNotificationSnapshot(isActive = false)
    }

    val primaryTask = foregroundTasks.firstOrNull { it.status == DownloadCenterTaskStatus.Running } ?: foregroundTasks.first()
    val indeterminate = primaryTask.status == DownloadCenterTaskStatus.Queued || !primaryTask.hasDeterminateProgress()
    val progress = if (indeterminate) {
        0
    } else {
        (primaryTask.progressFraction() * 100f).roundToInt().coerceIn(0, 100)
    }
    val lines = buildList {
        foregroundTasks.take(MAX_EXPANDED_LINES).forEach { task ->
            add("${task.itemTitle} · ${task.summaryText()}")
        }
        val remaining = foregroundTasks.size - MAX_EXPANDED_LINES
        if (remaining > 0) {
            add("还有 $remaining 个任务等待处理")
        }
    }

    return DownloadForegroundNotificationSnapshot(
        isActive = true,
        title = if (foregroundTasks.size == 1) {
            "后台下载中"
        } else {
            "后台下载中（${foregroundTasks.size} 个任务）"
        },
        text = primaryTask.title,
        subText = buildForegroundCountSummary(
            runningCount = foregroundTasks.count { it.status == DownloadCenterTaskStatus.Running },
            queuedCount = foregroundTasks.count { it.status == DownloadCenterTaskStatus.Queued },
        ),
        lines = lines,
        progress = progress,
        progressIndeterminate = indeterminate,
    )
}

private fun buildForegroundCountSummary(
    runningCount: Int,
    queuedCount: Int,
): String =
    listOfNotNull(
        runningCount.takeIf { it > 0 }?.let { "运行中 $it 个" },
        queuedCount.takeIf { it > 0 }?.let { "排队 $it 个" },
    ).joinToString(" · ").ifBlank { "准备开始下载" }

private const val MAX_EXPANDED_LINES = 4
