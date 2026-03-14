package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.DownloadCenterTaskStatus
import top.apricityx.workshop.DownloadCenterTaskUiState
import top.apricityx.workshop.canPause
import top.apricityx.workshop.canResume
import top.apricityx.workshop.hasDeterminateProgress
import top.apricityx.workshop.phaseLabel
import top.apricityx.workshop.progressDetails
import top.apricityx.workshop.progressFraction
import top.apricityx.workshop.removeActionLabel
import top.apricityx.workshop.resumeActionLabel
import top.apricityx.workshop.shouldAnimateProgress
import top.apricityx.workshop.statusLabel
import top.apricityx.workshop.summaryText
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard

@Composable
fun DownloadTaskDetailScreen(
    task: DownloadCenterTaskUiState,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit,
    onRemoveTask: () -> Unit,
    onShareDebugLog: () -> Unit,
    onShareRuntimeLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenSummaryCard(
            title = task.itemTitle,
            subtitle = task.gameTitle,
            metrics = listOf(task.statusLabel(), "阶段 ${task.phaseLabel()}", "账号 ${task.boundAccountName}"),
        ) {
            Text(
                text = task.summaryText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
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

            MetricFlow(metrics = task.progressDetails().take(4))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (task.canPause()) {
                    OutlinedButton(onClick = onPauseTask) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                        )
                        Text(" 暂停")
                    }
                }
                if (task.canResume()) {
                    OutlinedButton(onClick = onResumeTask) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Text(" ${task.resumeActionLabel()}")
                    }
                }
                OutlinedButton(onClick = onRemoveTask) {
                    Text(task.removeActionLabel())
                }
            }
        }

        task.errorMessage?.let {
            WorkshopMessageBanner(
                message = it,
                tone = MessageTone.Error,
            )
        }

        SectionCard(title = "下载进度") {
            task.progressDetails().forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (task.progressDetails().isEmpty()) {
                Text("当前还没有可展示的详细进度。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (task.status == DownloadCenterTaskStatus.Success) {
            SectionCard(title = "文件管理") {
                Text(
                    text = if (task.files.isEmpty()) {
                        "任务已经完成，导出结果会同步到模组库统一管理。"
                    } else {
                        "任务已经导出 ${task.files.size} 个文件。打开、分享或删除请前往模组库。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "日志") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "下载和导出关键步骤会额外写入独立调试日志。复现后可直接分享这份文件用于排查卓易通/鸿蒙兼容问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onShareDebugLog) {
                    Text("分享调试日志")
                }
                OutlinedButton(onClick = onShareRuntimeLog) {
                    Text("分享运行日志")
                }
                if (task.logs.isEmpty()) {
                    Text("暂无日志。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.logs.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    WorkshopPanelCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}
