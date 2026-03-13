package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.ExportedDownloadFile
import top.apricityx.workshop.ModLibraryDisplayMode
import top.apricityx.workshop.ModLibraryUiState
import top.apricityx.workshop.ModUpdateCheckResult
import top.apricityx.workshop.ModUpdateCheckStatus
import top.apricityx.workshop.modLibraryKey
import top.apricityx.workshop.primaryFile
import top.apricityx.workshop.ui.component.ModPreviewImage
import top.apricityx.workshop.ui.component.ModUpdateStatusText
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.buildModEntryMetrics

@Composable
fun ModLibraryScreen(
    state: ModLibraryUiState,
    onRetry: () -> Unit,
    onOpenModDetail: (DownloadedModEntry) -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onRemoveMod: (DownloadedModEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && state.items.isEmpty() -> WorkshopLoadingBlock(
            label = "正在同步本地模组库。",
            modifier = modifier,
        )

        state.errorMessage != null && state.items.isEmpty() -> WorkshopCenteredState(
            title = "模组库同步失败",
            message = state.errorMessage,
            actionLabel = "重试",
            onAction = onRetry,
            modifier = modifier,
        )

        state.items.isEmpty() -> WorkshopCenteredState(
            title = "模组库还是空的",
            message = state.message ?: "下载一个模组后，会自动同步到这里。",
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenSummaryCard(
                    title = "模组库",
                    subtitle = when (state.displayMode) {
                        ModLibraryDisplayMode.LargePreview ->
                            "当前为大图显示，这里展示已经导出到本地的模组文件，会在启动和下载完成后自动同步。"
                        ModLibraryDisplayMode.CompactList ->
                            "当前为精简列表显示，这里展示已经导出到本地的模组文件，会在启动和下载完成后自动同步。"
                    },
                    metrics = listOf(
                        "模组 ${state.items.size}",
                        "文件 ${state.items.sumOf { it.files.size }}",
                        "可更新 ${state.updateCheckState.results.values.count { it.status == ModUpdateCheckStatus.UpdateAvailable }}",
                    ),
                )
            }

            if (state.updateCheckState.isChecking) {
                item {
                    WorkshopMessageBanner(
                        message = "正在检查 ${state.items.size} 个模组的创意工坊更新。",
                        tone = MessageTone.Info,
                    )
                }
            }

            state.updateCheckState.summaryMessage?.let { summaryMessage ->
                item {
                    WorkshopMessageBanner(
                        message = summaryMessage,
                        tone = if (state.updateCheckState.results.values.any { it.status == ModUpdateCheckStatus.Failed }) {
                            MessageTone.Error
                        } else {
                            MessageTone.Success
                        },
                    )
                }
            }

            if (state.isLoading) {
                item {
                    WorkshopMessageBanner(message = "正在刷新本地模组库", tone = MessageTone.Info)
                }
            }

            state.errorMessage?.let { errorMessage ->
                item {
                    WorkshopMessageBanner(message = errorMessage, tone = MessageTone.Error)
                }
            }

            state.message?.let { message ->
                item {
                    WorkshopMessageBanner(message = message, tone = MessageTone.Info)
                }
            }

            item {
                SectionHeading(
                    title = "已下载模组",
                    subtitle = "列表操作默认作用于主文件，进入详情后可以逐个文件处理。",
                )
            }

            items(state.items, key = { "${it.appId}-${it.publishedFileId}" }) { entry ->
                when (state.displayMode) {
                    ModLibraryDisplayMode.LargePreview -> LargePreviewModLibraryCard(
                        entry = entry,
                        updateResult = state.updateCheckState.results[entry.modLibraryKey()],
                        onOpenDetail = { onOpenModDetail(entry) },
                        onOpenPrimaryFile = { onOpenPrimaryFile(it) },
                        onSharePrimaryFile = { onSharePrimaryFile(it) },
                        onRemoveMod = { onRemoveMod(entry) },
                    )

                    ModLibraryDisplayMode.CompactList -> CompactListModLibraryCard(
                        entry = entry,
                        updateResult = state.updateCheckState.results[entry.modLibraryKey()],
                        onOpenDetail = { onOpenModDetail(entry) },
                        onOpenPrimaryFile = { onOpenPrimaryFile(it) },
                        onSharePrimaryFile = { onSharePrimaryFile(it) },
                        onRemoveMod = { onRemoveMod(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LargePreviewModLibraryCard(
    entry: DownloadedModEntry,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onRemoveMod: () -> Unit,
) {
    val primaryFile = entry.primaryFile()
    WorkshopPanelCard(
        modifier = Modifier.clickable(onClick = onOpenDetail),
    ) {
        ModPreviewImage(
            previewImagePath = entry.previewImagePath,
            contentDescription = entry.itemTitle,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = entry.itemTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.gameTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetricFlow(
                metrics = buildModEntryMetrics(entry),
            )
            ModUpdateStatusText(result = updateResult)
            primaryFile?.let { file ->
                Text(
                    text = file.userVisiblePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primaryFile?.let { file ->
                OutlinedButton(
                    onClick = { onOpenPrimaryFile(file) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("打开")
                }
                OutlinedButton(
                    onClick = { onSharePrimaryFile(file) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("分享")
                }
            }
            OutlinedButton(
                onClick = onRemoveMod,
                modifier = Modifier.weight(1f),
            ) {
                Text("删除")
            }
        }
    }
}

@Composable
private fun CompactListModLibraryCard(
    entry: DownloadedModEntry,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onRemoveMod: () -> Unit,
) {
    val primaryFile = entry.primaryFile()
    WorkshopPanelCard(
        modifier = Modifier.clickable(onClick = onOpenDetail),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.itemTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.gameTitle} · ${entry.files.size} 个文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ModUpdateStatusText(result = updateResult)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primaryFile?.let { file ->
                OutlinedButton(
                    onClick = { onOpenPrimaryFile(file) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("打开")
                }
                OutlinedButton(
                    onClick = { onSharePrimaryFile(file) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("分享")
                }
            }
            OutlinedButton(
                onClick = onRemoveMod,
                modifier = Modifier.weight(1f),
            ) {
                Text("删除")
            }
        }
    }
}
