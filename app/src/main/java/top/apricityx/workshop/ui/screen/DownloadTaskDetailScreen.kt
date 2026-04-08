package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
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
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.WorkshopGlassSurface
import top.apricityx.workshop.ui.component.WorkshopOutlinedButton
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled
import top.apricityx.workshop.ui.theme.workshopChromePadding

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
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .workshopChromePadding(topExtra = 8.dp, bottomExtra = 16.dp),
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
                    WorkshopOutlinedButton(onClick = onPauseTask) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                        )
                        Text(" 暂停")
                    }
                }
                if (task.canResume()) {
                    WorkshopOutlinedButton(onClick = onResumeTask) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Text(" ${task.resumeActionLabel()}")
                    }
                }
                WorkshopOutlinedButton(onClick = onRemoveTask) {
                    Text(task.removeActionLabel())
                }
            }
        }

        task.errorMessage?.let {
            DownloadTaskFailureCard(
                message = it,
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
                    text = "系统层面上的诊断日志。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WorkshopOutlinedButton(onClick = onShareDebugLog) {
                    Text("分享调试日志")
                }
                WorkshopOutlinedButton(onClick = onShareRuntimeLog) {
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
private fun DownloadTaskFailureCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val containerColor = if (isDark) {
        Color(0xFF4A1F24).copy(alpha = 0.8f)
    } else {
        Color(0xFFFFE0E0)
    }
    val borderColor = if (isDark) {
        Color(0xFFFF9B9B).copy(alpha = 0.34f)
    } else {
        Color(0xFFF0A5A5)
    }
    val contentColor = if (isDark) {
        Color(0xFFFFE0E0)
    } else {
        Color(0xFF7F2A2A)
    }

    if (isLiquidGlassFrontendEnabled()) {
        WorkshopGlassSurface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            blurRadius = 16.dp,
            lensHeight = 8.dp,
            lensAmount = 10.dp,
            surfaceColor = containerColor.copy(alpha = 0.22f),
            borderColor = borderColor.copy(alpha = 0.24f),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "下载失败",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor.copy(alpha = if (isDark) 0.42f else 0.5f),
        border = BorderStroke(1.dp, borderColor.copy(alpha = if (isDark) 0.3f else 0.42f)),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "下载失败",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
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
