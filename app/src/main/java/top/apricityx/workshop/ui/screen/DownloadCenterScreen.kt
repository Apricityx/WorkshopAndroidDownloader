package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.DownloadCenterTaskStatus
import top.apricityx.workshop.DownloadCenterTaskUiState
import top.apricityx.workshop.DownloadCenterUiState
import top.apricityx.workshop.hasDeterminateProgress
import top.apricityx.workshop.progressDetails
import top.apricityx.workshop.progressFraction
import top.apricityx.workshop.removeActionLabel
import top.apricityx.workshop.shouldAnimateProgress
import top.apricityx.workshop.statusLabel
import top.apricityx.workshop.summaryText
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopPanelCard

@Composable
fun DownloadCenterScreen(
    state: DownloadCenterUiState,
    onClearFinished: () -> Unit,
    onOpenTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onOpenFile: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenSummaryCard(
                title = "下载中心",
                subtitle = "所有下载任务都会在这里排队、执行和保留历史记录，不会影响前台浏览。",
                metrics = listOf(
                    "运行中 ${state.runningCount}",
                    "排队 ${state.queuedCount}",
                    "暂停 ${state.pausedCount}",
                    "历史 ${state.finishedCount}",
                ),
            )
        }

        if (state.tasks.isEmpty()) {
            item {
                WorkshopCenteredState(
                    title = "下载中心还是空的",
                    message = "去创意工坊里选择模组后，任务会出现在这里。",
                )
            }
        } else {
            if (state.activeTasks.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "正在进行",
                        subtitle = "包含运行中、排队中和已暂停的任务。",
                    )
                }

                items(state.activeTasks, key = { "active-${it.id}" }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onOpenTask = { onOpenTask(task.id) },
                        onRemoveTask = { onRemoveTask(task.id) },
                        onOpenFile = { contentUri -> onOpenFile(task.id, contentUri) },
                    )
                }
            }

            if (state.historyTasks.isNotEmpty()) {
                item {
                    SectionHeading(
                        title = "历史记录",
                        subtitle = "已完成或失败的任务会保留在这里。",
                    )
                }

                items(state.historyTasks, key = { "history-${it.id}" }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onOpenTask = { onOpenTask(task.id) },
                        onRemoveTask = { onRemoveTask(task.id) },
                        onOpenFile = { contentUri -> onOpenFile(task.id, contentUri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadCenterTaskUiState,
    onOpenTask: () -> Unit,
    onRemoveTask: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    WorkshopPanelCard(
        modifier = Modifier.clickable(onClick = onOpenTask),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = task.status.icon(),
                    contentDescription = null,
                    tint = task.status.tint(),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = task.itemTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.gameTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.summaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            MetricFlow(
                metrics = listOf(task.statusLabel()) + task.progressDetails().take(2),
            )

            if (task.hasDeterminateProgress()) {
                LinearProgressIndicator(
                    progress = { task.progressFraction() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (task.shouldAnimateProgress()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(
                    progress = { task.progressFraction() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRemoveTask) {
                    Text(task.removeActionLabel())
                }
                task.files.firstOrNull()?.let { file ->
                    TextButton(onClick = { onOpenFile(file.contentUri) }) {
                        Text("打开文件")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCenterTaskStatus.tint() =
    when (this) {
        DownloadCenterTaskStatus.Queued -> MaterialTheme.colorScheme.primary
        DownloadCenterTaskStatus.Running -> MaterialTheme.colorScheme.tertiary
        DownloadCenterTaskStatus.Paused -> MaterialTheme.colorScheme.secondary
        DownloadCenterTaskStatus.Success -> MaterialTheme.colorScheme.primary
        DownloadCenterTaskStatus.Failed -> MaterialTheme.colorScheme.error
    }

private fun DownloadCenterTaskStatus.icon(): ImageVector =
    when (this) {
        DownloadCenterTaskStatus.Queued -> Icons.Default.Download
        DownloadCenterTaskStatus.Running -> Icons.Default.Sync
        DownloadCenterTaskStatus.Paused -> Icons.Default.PauseCircle
        DownloadCenterTaskStatus.Success -> Icons.Default.CheckCircle
        DownloadCenterTaskStatus.Failed -> Icons.Default.ErrorOutline
    }
