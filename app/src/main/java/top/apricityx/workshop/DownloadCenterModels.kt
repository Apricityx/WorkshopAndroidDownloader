package top.apricityx.workshop

import java.text.DecimalFormat
import kotlinx.serialization.Serializable
import top.apricityx.workshop.workshop.DownloadState

@Serializable
enum class DownloadCenterTaskStatus {
    Queued,
    Running,
    Paused,
    Success,
    Failed,
}

@Serializable
data class DownloadCenterProgressSnapshot(
    val writtenBytes: Long = 0L,
    val totalBytes: Long? = null,
    val completedChunks: Int = 0,
    val totalChunks: Int? = null,
    val completedFiles: Int = 0,
    val totalFiles: Int? = null,
    val speedBytesPerSecond: Long? = null,
)

@Serializable
data class DownloadCenterTaskUiState(
    val id: String,
    val appId: UInt,
    val publishedFileId: ULong,
    val gameTitle: String,
    val itemTitle: String,
    val status: DownloadCenterTaskStatus = DownloadCenterTaskStatus.Queued,
    val phase: DownloadState = DownloadState.Idle,
    val logs: List<String> = emptyList(),
    val files: List<ExportedDownloadFile> = emptyList(),
    val progress: DownloadCenterProgressSnapshot = DownloadCenterProgressSnapshot(),
    val errorMessage: String? = null,
    val enqueuedAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = enqueuedAtMillis,
) {
    val title: String
        get() = "$gameTitle / $itemTitle"
}

data class DownloadCenterUiState(
    val tasks: List<DownloadCenterTaskUiState> = emptyList(),
) {
    val displayTasks: List<DownloadCenterTaskUiState>
        get() = tasks.sortedWith(
            compareBy<DownloadCenterTaskUiState> { it.status.sortOrder() }
                .thenByDescending { if (it.status == DownloadCenterTaskStatus.Running) it.updatedAtMillis else it.enqueuedAtMillis }
                .thenByDescending(DownloadCenterTaskUiState::updatedAtMillis),
        )

    val queuedCount: Int
        get() = tasks.count { it.status == DownloadCenterTaskStatus.Queued }

    val runningCount: Int
        get() = tasks.count { it.status == DownloadCenterTaskStatus.Running }

    val pausedCount: Int
        get() = tasks.count { it.status == DownloadCenterTaskStatus.Paused }

    val finishedCount: Int
        get() = tasks.count { it.status == DownloadCenterTaskStatus.Success || it.status == DownloadCenterTaskStatus.Failed }

    val activeCount: Int
        get() = queuedCount + runningCount

    val activeTasks: List<DownloadCenterTaskUiState>
        get() = displayTasks.filter {
            it.status == DownloadCenterTaskStatus.Running ||
                it.status == DownloadCenterTaskStatus.Queued ||
                it.status == DownloadCenterTaskStatus.Paused
        }

    val historyTasks: List<DownloadCenterTaskUiState>
        get() = displayTasks.filter {
            it.status == DownloadCenterTaskStatus.Success ||
                it.status == DownloadCenterTaskStatus.Failed
        }
}

fun DownloadCenterTaskUiState.canPause(): Boolean =
    status == DownloadCenterTaskStatus.Queued || status == DownloadCenterTaskStatus.Running

fun DownloadCenterTaskUiState.canResume(): Boolean =
    status == DownloadCenterTaskStatus.Paused || status == DownloadCenterTaskStatus.Failed

fun DownloadCenterTaskUiState.removeActionLabel(): String =
    when (status) {
        DownloadCenterTaskStatus.Queued,
        DownloadCenterTaskStatus.Running,
        DownloadCenterTaskStatus.Paused,
        -> "取消任务"

        DownloadCenterTaskStatus.Success,
        DownloadCenterTaskStatus.Failed,
        -> "删除任务"
    }

fun DownloadCenterTaskUiState.hasDeterminateProgress(): Boolean =
    status == DownloadCenterTaskStatus.Success ||
        progress.totalBytes != null ||
        progress.totalChunks != null ||
        progress.totalFiles != null

fun DownloadCenterTaskUiState.shouldAnimateProgress(): Boolean =
    status == DownloadCenterTaskStatus.Running && !hasDeterminateProgress()

fun DownloadCenterTaskUiState.progressFraction(): Float =
    when (status) {
        DownloadCenterTaskStatus.Queued -> 0f
        DownloadCenterTaskStatus.Success -> 1f
        DownloadCenterTaskStatus.Running,
        DownloadCenterTaskStatus.Paused,
        DownloadCenterTaskStatus.Failed,
        -> progress.fraction ?: 0f
    }

fun DownloadCenterTaskUiState.summaryText(): String =
    when (status) {
        DownloadCenterTaskStatus.Queued -> "排队中，等待开始下载"
        DownloadCenterTaskStatus.Running -> listOfNotNull(
            phase.displayName(),
            progress.percentText(),
            progress.bytesText(),
        ).joinToString(" · ").ifBlank { "正在下载" }

        DownloadCenterTaskStatus.Paused -> listOfNotNull(
            "已暂停",
            progress.percentText(),
            progress.bytesText(),
        ).joinToString(" · ").ifBlank { "已暂停，可继续下载" }

        DownloadCenterTaskStatus.Success -> "已完成，点击查看日志和导出文件"
        DownloadCenterTaskStatus.Failed -> errorMessage ?: "下载失败，可继续下载"
    }

fun DownloadCenterTaskUiState.statusLabel(): String =
    status.displayName()

fun DownloadCenterTaskUiState.phaseLabel(): String =
    phase.displayName()

fun DownloadCenterTaskUiState.progressDetails(): List<String> =
    buildList {
        progress.percentText()?.let { add("总进度 $it") }
        progress.bytesText()?.let { add("数据 $it") }
        progress.chunkText()?.let { add("分块 $it") }
        progress.fileText()?.let { add("文件 $it") }
        progress.speedText()?.let { add("速度 $it") }
    }

private val DownloadCenterProgressSnapshot.fraction: Float?
    get() = when {
        totalBytes != null && totalBytes > 0L -> (writtenBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        totalChunks != null && totalChunks > 0 -> (completedChunks.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
        totalFiles != null && totalFiles > 0 -> (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        else -> null
    }

private fun DownloadCenterProgressSnapshot.percentText(): String? =
    fraction?.let { value ->
        "${DecimalFormat("0.0").format(value * 100f)}%"
    }

private fun DownloadCenterProgressSnapshot.bytesText(): String? =
    when {
        writtenBytes > 0L && totalBytes != null && totalBytes > 0L ->
            "${formatBytes(writtenBytes)} / ${formatBytes(totalBytes)}"

        writtenBytes > 0L -> formatBytes(writtenBytes)
        totalBytes != null && totalBytes > 0L -> "0 B / ${formatBytes(totalBytes)}"
        else -> null
    }

private fun DownloadCenterProgressSnapshot.chunkText(): String? =
    totalChunks?.let { total -> "${completedChunks.coerceAtMost(total)} / $total" }

private fun DownloadCenterProgressSnapshot.fileText(): String? =
    totalFiles?.let { total -> "${completedFiles.coerceAtMost(total)} / $total" }

private fun DownloadCenterProgressSnapshot.speedText(): String? =
    speedBytesPerSecond?.takeIf { it > 0L }?.let { speed ->
        "${formatBytes(speed)}/s"
    }

private fun DownloadState.displayName(): String =
    when (this) {
        DownloadState.Idle -> "等待中"
        DownloadState.Resolving -> "解析元数据"
        DownloadState.Connecting -> "连接内容服务器"
        DownloadState.Downloading -> "下载中"
        DownloadState.Paused -> "已暂停"
        DownloadState.Success -> "已完成"
        DownloadState.Failed -> "失败"
    }

fun DownloadCenterTaskStatus.displayName(): String =
    when (this) {
        DownloadCenterTaskStatus.Queued -> "排队中"
        DownloadCenterTaskStatus.Running -> "下载中"
        DownloadCenterTaskStatus.Paused -> "已暂停"
        DownloadCenterTaskStatus.Success -> "已完成"
        DownloadCenterTaskStatus.Failed -> "失败"
    }

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) {
        return "$bytes B"
    }

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "${DecimalFormat("0.0").format(value)} ${units[unitIndex.coerceAtLeast(0)]}"
}

private fun DownloadCenterTaskStatus.sortOrder(): Int =
    when (this) {
        DownloadCenterTaskStatus.Running -> 0
        DownloadCenterTaskStatus.Queued -> 1
        DownloadCenterTaskStatus.Paused -> 2
        DownloadCenterTaskStatus.Failed -> 3
        DownloadCenterTaskStatus.Success -> 4
    }
