package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.ExportedDownloadFile
import top.apricityx.workshop.ModLibraryDisplayMode
import top.apricityx.workshop.ModLibraryUiState
import top.apricityx.workshop.ModUpdateCheckResult
import top.apricityx.workshop.ModUpdateCheckStatus
import top.apricityx.workshop.modLibraryKey
import top.apricityx.workshop.primaryFile
import top.apricityx.workshop.screenSubtitle
import top.apricityx.workshop.sectionSubtitle
import top.apricityx.workshop.ui.component.ModPreviewImage
import top.apricityx.workshop.ui.component.ModUpdateStatusText
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.component.buildModEntryMetrics

private val OverviewCheckingBorderColor = Color(0xFFF59E0B)
private val OverviewUpdateAvailableBorderColor = Color(0xFF22C55E)

@Composable
fun ModLibraryScreen(
    state: ModLibraryUiState,
    onRetry: () -> Unit,
    onCheckUpdates: () -> Unit,
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

        state.displayMode == ModLibraryDisplayMode.Overview -> OverviewModLibraryGrid(
            state = state,
            onCheckUpdates = onCheckUpdates,
            onOpenModDetail = onOpenModDetail,
            modifier = modifier,
        )

        else -> ListModLibraryContent(
            state = state,
            onCheckUpdates = onCheckUpdates,
            onOpenModDetail = onOpenModDetail,
            onOpenPrimaryFile = onOpenPrimaryFile,
            onSharePrimaryFile = onSharePrimaryFile,
            onRemoveMod = onRemoveMod,
            modifier = modifier,
        )
    }
}

@Composable
private fun ListModLibraryContent(
    state: ModLibraryUiState,
    onCheckUpdates: () -> Unit,
    onOpenModDetail: (DownloadedModEntry) -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onRemoveMod: (DownloadedModEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        modLibraryHeaderItems(
            state = state,
            onCheckUpdates = onCheckUpdates,
        )

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
                )

                ModLibraryDisplayMode.Overview -> Unit
            }
        }
    }
}

@Composable
private fun OverviewModLibraryGrid(
    state: ModLibraryUiState,
    onCheckUpdates: () -> Unit,
    onOpenModDetail: (DownloadedModEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        modLibraryHeaderItems(
            state = state,
            onCheckUpdates = onCheckUpdates,
        )

        gridItems(state.items, key = { "${it.appId}-${it.publishedFileId}" }) { entry ->
            OverviewModLibraryTile(
                entry = entry,
                updateResult = state.updateCheckState.results[entry.modLibraryKey()],
                onOpenDetail = { onOpenModDetail(entry) },
            )
        }
    }
}

private fun LazyListScope.modLibraryHeaderItems(
    state: ModLibraryUiState,
    onCheckUpdates: () -> Unit,
) {
    item {
        ModLibrarySummaryCard(
            state = state,
            onCheckUpdates = onCheckUpdates,
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
                tone = modLibrarySummaryTone(state),
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
        ModLibrarySectionHeading(displayMode = state.displayMode)
    }
}

private fun LazyGridScope.modLibraryHeaderItems(
    state: ModLibraryUiState,
    onCheckUpdates: () -> Unit,
) {
    fullSpanItem {
        ModLibrarySummaryCard(
            state = state,
            onCheckUpdates = onCheckUpdates,
        )
    }

    if (state.updateCheckState.isChecking) {
        fullSpanItem {
            WorkshopMessageBanner(
                message = "正在检查 ${state.items.size} 个模组的创意工坊更新。",
                tone = MessageTone.Info,
            )
        }
    }

    state.updateCheckState.summaryMessage?.let { summaryMessage ->
        fullSpanItem {
            WorkshopMessageBanner(
                message = summaryMessage,
                tone = modLibrarySummaryTone(state),
            )
        }
    }

    if (state.isLoading) {
        fullSpanItem {
            WorkshopMessageBanner(message = "正在刷新本地模组库", tone = MessageTone.Info)
        }
    }

    state.errorMessage?.let { errorMessage ->
        fullSpanItem {
            WorkshopMessageBanner(message = errorMessage, tone = MessageTone.Error)
        }
    }

    state.message?.let { message ->
        fullSpanItem {
            WorkshopMessageBanner(message = message, tone = MessageTone.Info)
        }
    }

    fullSpanItem {
        ModLibrarySectionHeading(displayMode = state.displayMode)
    }
}

private fun LazyGridScope.fullSpanItem(
    content: @Composable () -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        content()
    }
}

@Composable
private fun ModLibrarySummaryCard(
    state: ModLibraryUiState,
    onCheckUpdates: () -> Unit,
) {
    ScreenSummaryCard(
        title = "模组库",
        subtitle = state.displayMode.screenSubtitle(),
        metrics = listOf(
            "模组 ${state.items.size}",
            "文件 ${state.items.sumOf { it.files.size }}",
            "可更新 ${state.updateCheckState.results.values.count { it.status == ModUpdateCheckStatus.UpdateAvailable }}",
        ),
    ) {
        OutlinedButton(
            onClick = onCheckUpdates,
            enabled = !state.updateCheckState.isChecking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "检查模组更新",
            )
            Text(
                text = if (state.updateCheckState.isChecking) "正在检查更新" else "检查模组更新",
            )
        }
    }
}

@Composable
private fun ModLibrarySectionHeading(
    displayMode: ModLibraryDisplayMode,
) {
    SectionHeading(
        title = "已下载模组",
        subtitle = displayMode.sectionSubtitle(),
    )
}

private fun modLibrarySummaryTone(state: ModLibraryUiState): MessageTone =
    if (state.updateCheckState.results.values.any { it.status == ModUpdateCheckStatus.Failed }) {
        MessageTone.Error
    } else {
        MessageTone.Success
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
private fun OverviewModLibraryTile(
    entry: DownloadedModEntry,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
) {
    val borderColor = overviewBorderColor(updateResult)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onOpenDetail),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(2.dp, borderColor),
        tonalElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!entry.previewImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = entry.previewImagePath,
                    contentDescription = entry.itemTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                            ),
                        ),
                ) {
                    Text(
                        text = entry.itemTitle,
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f),
                            ),
                        ),
                    ),
            )

            OverviewTileBadge(
                text = "${entry.files.size} 文件",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )

            overviewStatusLabel(updateResult)?.let { label ->
                OverviewTileBadge(
                    text = label,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    containerColor = borderColor.copy(alpha = 0.92f),
                    contentColor = overviewBadgeContentColor(updateResult),
                )
            }
        }
    }
}

@Composable
private fun OverviewTileBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun overviewBorderColor(result: ModUpdateCheckResult?): Color =
    when (result?.status) {
        ModUpdateCheckStatus.UpdateAvailable -> OverviewUpdateAvailableBorderColor
        ModUpdateCheckStatus.Failed -> MaterialTheme.colorScheme.error
        ModUpdateCheckStatus.Checking -> OverviewCheckingBorderColor
        ModUpdateCheckStatus.UpToDate -> MaterialTheme.colorScheme.outlineVariant
        ModUpdateCheckStatus.Unknown,
        null,
        -> MaterialTheme.colorScheme.outlineVariant
    }

@Composable
private fun overviewBadgeContentColor(result: ModUpdateCheckResult?): Color =
    when (result?.status) {
        ModUpdateCheckStatus.Failed -> MaterialTheme.colorScheme.onError
        ModUpdateCheckStatus.UpdateAvailable -> Color.White
        ModUpdateCheckStatus.Checking -> Color.White
        ModUpdateCheckStatus.UpToDate -> MaterialTheme.colorScheme.onTertiary
        ModUpdateCheckStatus.Unknown,
        null,
        -> MaterialTheme.colorScheme.onSurface
    }

private fun overviewStatusLabel(result: ModUpdateCheckResult?): String? =
    when (result?.status) {
        ModUpdateCheckStatus.UpdateAvailable -> "更新"
        ModUpdateCheckStatus.Failed -> "失败"
        ModUpdateCheckStatus.Checking -> "检查中"
        ModUpdateCheckStatus.UpToDate,
        ModUpdateCheckStatus.Unknown,
        null,
        -> null
    }

@Composable
private fun CompactListModLibraryCard(
    entry: DownloadedModEntry,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
) {
    WorkshopPanelCard(
        modifier = Modifier.clickable(onClick = onOpenDetail),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
