package top.apricityx.workshop.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.apricityx.workshop.DownloadedModGroup
import top.apricityx.workshop.ExportedDownloadFile
import top.apricityx.workshop.ModLibraryDisplayMode
import top.apricityx.workshop.ModLibrarySortOption
import top.apricityx.workshop.ModLibraryUiState
import top.apricityx.workshop.ModUpdateCheckResult
import top.apricityx.workshop.ModUpdateCheckStatus
import top.apricityx.workshop.availableModLibraryGames
import top.apricityx.workshop.displayName
import top.apricityx.workshop.filterModLibraryGroups
import top.apricityx.workshop.hasActiveFilters
import top.apricityx.workshop.latestUpdateStatus
import top.apricityx.workshop.latestVersion
import top.apricityx.workshop.modGroupKey
import top.apricityx.workshop.modLibraryKey
import top.apricityx.workshop.primaryFile
import top.apricityx.workshop.screenSubtitle
import top.apricityx.workshop.sectionSubtitle
import top.apricityx.workshop.sortModLibraryGroups
import top.apricityx.workshop.totalFileCount
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ModPreviewImage
import top.apricityx.workshop.ui.component.ModUpdateStatusText
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopGlassSurface
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopOutlinedButton
import top.apricityx.workshop.ui.component.WorkshopPopupMenu
import top.apricityx.workshop.ui.component.WorkshopPopupMenuItem
import top.apricityx.workshop.ui.component.WorkshopOutlinedTextField
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.component.WorkshopTextButton
import top.apricityx.workshop.ui.component.formatModLibraryTimestamp
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled
import top.apricityx.workshop.ui.theme.workshopChromePadding
import top.apricityx.workshop.ui.theme.workshopListContentPadding
import top.apricityx.workshop.versionCount
import top.apricityx.workshop.versionLabel

private val OverviewCheckingBorderColor = Color(0xFFF59E0B)
private val OverviewUpdateAvailableBorderColor = Color(0xFF22C55E)

private enum class ModLibraryUpdateCardTone {
    Normal,
    Checking,
    Failed,
}

private data class ModLibraryUpdateFeedback(
    val message: String,
    val tone: ModLibraryUpdateCardTone,
)

private data class ModLibrarySummaryData(
    val availableGames: List<String>,
    val totalMods: Int,
    val visibleMods: Int,
    val totalVersions: Int,
    val totalFiles: Int,
    val totalUpdateAvailable: Int,
    val visibleVersions: Int,
    val visibleFiles: Int,
    val visibleUpdateAvailable: Int,
)

@Composable
fun ModLibraryScreen(
    state: ModLibraryUiState,
    onRetry: () -> Unit,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
    onOpenModDetail: (DownloadedModGroup) -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onViewChangeNotes: (DownloadedModGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val updateResults = state.updateCheckState.results
    val visibleItems = remember(state.items, state.filterState, state.sortOption) {
        sortModLibraryGroups(
            items = filterModLibraryGroups(
                items = state.items,
                filterState = state.filterState,
            ),
            sortOption = state.sortOption,
        )
    }
    val summaryData = remember(state.items, visibleItems, updateResults) {
        buildModLibrarySummaryData(
            items = state.items,
            visibleItems = visibleItems,
            updateResults = updateResults,
        )
    }
    val updateFeedback = remember(state.updateCheckState, state.items.size) {
        currentModLibraryUpdateFeedback(state)
    }

    when {
        state.isLoading && state.items.isEmpty() -> WorkshopLoadingBlock(
            label = "正在同步本地模组库。",
            modifier = modifier.workshopChromePadding(topExtra = 24.dp, bottomExtra = 24.dp),
        )

        state.errorMessage != null && state.items.isEmpty() -> WorkshopCenteredState(
            title = "模组库同步失败",
            message = state.errorMessage,
            actionLabel = "重试",
            onAction = onRetry,
            modifier = modifier.workshopChromePadding(topExtra = 24.dp, bottomExtra = 24.dp),
        )

        state.items.isEmpty() -> WorkshopCenteredState(
            title = "模组库还是空的",
            message = state.message ?: "下载一个模组后，会自动同步到这里。",
            modifier = modifier.workshopChromePadding(topExtra = 24.dp, bottomExtra = 24.dp),
        )

        state.displayMode == ModLibraryDisplayMode.Overview -> OverviewModLibraryGrid(
            state = state,
            visibleItems = visibleItems,
            summaryData = summaryData,
            updateFeedback = updateFeedback,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
            onOpenModDetail = onOpenModDetail,
            onOpenPrimaryFile = onOpenPrimaryFile,
            onSharePrimaryFile = onSharePrimaryFile,
            onViewChangeNotes = onViewChangeNotes,
            modifier = modifier,
        )

        else -> ListModLibraryContent(
            state = state,
            visibleItems = visibleItems,
            summaryData = summaryData,
            updateFeedback = updateFeedback,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
            onOpenModDetail = onOpenModDetail,
            onOpenPrimaryFile = onOpenPrimaryFile,
            onSharePrimaryFile = onSharePrimaryFile,
            onViewChangeNotes = onViewChangeNotes,
            modifier = modifier,
        )
    }
}

@Composable
private fun ListModLibraryContent(
    state: ModLibraryUiState,
    visibleItems: List<DownloadedModGroup>,
    summaryData: ModLibrarySummaryData,
    updateFeedback: ModLibraryUpdateFeedback?,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
    onOpenModDetail: (DownloadedModGroup) -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onViewChangeNotes: (DownloadedModGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = workshopListContentPadding(topExtra = 20.dp, bottomExtra = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        modLibraryHeaderItems(
            state = state,
            summaryData = summaryData,
            updateFeedback = updateFeedback,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
        )

        if (visibleItems.isEmpty()) {
            item {
                FilteredModLibraryEmptyState(onClearFilters = onClearFilters)
            }
        } else {
            items(visibleItems, key = { it.modGroupKey() }) { group ->
                when (state.displayMode) {
                    ModLibraryDisplayMode.LargePreview -> LargePreviewModLibraryCard(
                        group = group,
                        updateResult = latestUpdateResult(group, state),
                        onOpenDetail = { onOpenModDetail(group) },
                        onOpenPrimaryFile = { onOpenPrimaryFile(it) },
                        onSharePrimaryFile = { onSharePrimaryFile(it) },
                        onViewChangeNotes = { onViewChangeNotes(group) },
                    )

                    ModLibraryDisplayMode.CompactList -> CompactListModLibraryCard(
                        group = group,
                        updateResult = latestUpdateResult(group, state),
                        onOpenDetail = { onOpenModDetail(group) },
                        onViewChangeNotes = { onViewChangeNotes(group) },
                    )

                    ModLibraryDisplayMode.Overview -> Unit
                }
            }
        }
    }
}

@Composable
private fun OverviewModLibraryGrid(
    state: ModLibraryUiState,
    visibleItems: List<DownloadedModGroup>,
    summaryData: ModLibrarySummaryData,
    updateFeedback: ModLibraryUpdateFeedback?,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
    onOpenModDetail: (DownloadedModGroup) -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onViewChangeNotes: (DownloadedModGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = workshopListContentPadding(topExtra = 20.dp, bottomExtra = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        modLibraryHeaderItems(
            state = state,
            summaryData = summaryData,
            updateFeedback = updateFeedback,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
        )

        if (visibleItems.isEmpty()) {
            fullSpanItem {
                FilteredModLibraryEmptyState(onClearFilters = onClearFilters)
            }
        } else {
            gridItems(visibleItems, key = { it.modGroupKey() }) { group ->
                OverviewModLibraryTile(
                    group = group,
                    updateResult = latestUpdateResult(group, state),
                    onOpenDetail = { onOpenModDetail(group) },
                    onOpenPrimaryFile = { onOpenPrimaryFile(it) },
                    onSharePrimaryFile = { onSharePrimaryFile(it) },
                    onViewChangeNotes = { onViewChangeNotes(group) },
                )
            }
        }
    }
}

private fun LazyListScope.modLibraryHeaderItems(
    state: ModLibraryUiState,
    summaryData: ModLibrarySummaryData,
    updateFeedback: ModLibraryUpdateFeedback?,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
) {
    item {
        ModLibrarySummaryCard(
            state = state,
            summaryData = summaryData,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
        )
    }

    updateFeedback?.let { feedback ->
        item {
            ModLibraryUpdateStatusCard(
                message = feedback.message,
                tone = feedback.tone,
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
    summaryData: ModLibrarySummaryData,
    updateFeedback: ModLibraryUpdateFeedback?,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
) {
    fullSpanItem {
        ModLibrarySummaryCard(
            state = state,
            summaryData = summaryData,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
        )
    }

    updateFeedback?.let { feedback ->
        fullSpanItem {
            ModLibraryUpdateStatusCard(
                message = feedback.message,
                tone = feedback.tone,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModLibrarySummaryCard(
    state: ModLibraryUiState,
    summaryData: ModLibrarySummaryData,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
) {
    ScreenSummaryCard(
        title = "模组库",
        subtitle = state.displayMode.screenSubtitle(),
        metrics = listOf(
            "模组 ${filteredMetricText(summaryData.visibleMods, summaryData.totalMods)}",
            "版本 ${filteredMetricText(summaryData.visibleVersions, summaryData.totalVersions)}",
            "文件 ${filteredMetricText(summaryData.visibleFiles, summaryData.totalFiles)}",
            "可更新 ${filteredMetricText(summaryData.visibleUpdateAvailable, summaryData.totalUpdateAvailable)}",
        ),
    ) {
        WorkshopOutlinedButton(
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

        WorkshopPanelCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleFilterPanel),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "筛选与排序",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "搜索、排序和游戏筛选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (state.filterPanelExpanded) "收起筛选与排序" else "展开筛选与排序",
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (state.filterPanelExpanded) 90f else 0f
                    },
                )
            }

            if (state.filterPanelExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WorkshopOutlinedTextField(
                        value = state.filterState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text("搜索模组 / 游戏 / ID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "排序方式",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModLibrarySortOption.entries.forEach { sortOption ->
                            ModLibrarySelectionChip(
                                label = sortOption.displayName(),
                                selected = sortOption == state.sortOption,
                                onClick = { onSortOptionSelected(sortOption) },
                            )
                        }
                    }

                    Text(
                        text = "按游戏筛选",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModLibrarySelectionChip(
                            label = "全部游戏",
                            selected = state.filterState.selectedGameTitle == null,
                            onClick = { onGameFilterSelected(null) },
                        )
                        summaryData.availableGames.forEach { gameTitle ->
                            ModLibrarySelectionChip(
                                label = gameTitle,
                                selected = state.filterState.selectedGameTitle == gameTitle,
                                onClick = { onGameFilterSelected(gameTitle) },
                            )
                        }
                    }
                }
            }
        }

        if (state.filterState.hasActiveFilters()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "当前筛选后显示 ${summaryData.visibleMods} 个模组。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WorkshopTextButton(onClick = onClearFilters) {
                    Text("清空筛选")
                }
            }
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

@Composable
private fun FilteredModLibraryEmptyState(
    onClearFilters: () -> Unit,
) {
    WorkshopCenteredState(
        title = "没有符合条件的模组",
        message = "调整关键词或游戏筛选后再试。",
        actionLabel = "清空筛选",
        onAction = onClearFilters,
    )
}

@Composable
private fun ModLibrarySelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val surfaceColor = when {
        selected && isDark -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isDark -> MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
    }
    val borderColor = when {
        selected && isDark -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        isDark -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    }
    val contentColor = when {
        selected && isDark -> Color(0xFFEAF3FF)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    if (isLiquidGlassFrontendEnabled()) {
        WorkshopGlassSurface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            blurRadius = 14.dp,
            lensHeight = 6.dp,
            lensAmount = 8.dp,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun currentModLibraryUpdateFeedback(
    state: ModLibraryUiState,
): ModLibraryUpdateFeedback? =
    when {
        state.updateCheckState.isChecking -> ModLibraryUpdateFeedback(
            message = "正在检查 ${state.items.size} 个模组的创意工坊更新。",
            tone = ModLibraryUpdateCardTone.Checking,
        )

        state.updateCheckState.summaryMessage != null -> ModLibraryUpdateFeedback(
            message = state.updateCheckState.summaryMessage,
            tone = modLibraryUpdateCardTone(state),
        )

        else -> null
    }

private fun buildModLibrarySummaryData(
    items: List<DownloadedModGroup>,
    visibleItems: List<DownloadedModGroup>,
    updateResults: Map<String, ModUpdateCheckResult>,
): ModLibrarySummaryData =
    ModLibrarySummaryData(
        availableGames = availableModLibraryGames(items),
        totalMods = items.size,
        visibleMods = visibleItems.size,
        totalVersions = items.sumOf(DownloadedModGroup::versionCount),
        totalFiles = items.sumOf(DownloadedModGroup::totalFileCount),
        totalUpdateAvailable = items.count {
            it.latestUpdateStatus(updateResults) == ModUpdateCheckStatus.UpdateAvailable
        },
        visibleVersions = visibleItems.sumOf(DownloadedModGroup::versionCount),
        visibleFiles = visibleItems.sumOf(DownloadedModGroup::totalFileCount),
        visibleUpdateAvailable = visibleItems.count {
            it.latestUpdateStatus(updateResults) == ModUpdateCheckStatus.UpdateAvailable
        },
    )

private fun modLibraryUpdateCardTone(state: ModLibraryUiState): ModLibraryUpdateCardTone =
    if (state.updateCheckState.results.values.any { it.status == ModUpdateCheckStatus.Failed }) {
        ModLibraryUpdateCardTone.Failed
    } else {
        ModLibraryUpdateCardTone.Normal
    }

@Composable
private fun ModLibraryUpdateStatusCard(
    message: String,
    tone: ModLibraryUpdateCardTone,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val useTransparentCardStyle = isLiquidGlassFrontendEnabled()
    val containerColor = when (tone) {
        ModLibraryUpdateCardTone.Normal -> if (isDark) {
            Color(0xFF14314C).copy(alpha = 0.76f)
        } else {
            Color(0xFFDDEEFF)
        }

        ModLibraryUpdateCardTone.Checking -> if (isDark) {
            Color(0xFF4A3B14).copy(alpha = 0.8f)
        } else {
            Color(0xFFFFF0C7)
        }

        ModLibraryUpdateCardTone.Failed -> if (isDark) {
            Color(0xFF4A1F24).copy(alpha = 0.8f)
        } else {
            Color(0xFFFFE0E0)
        }
    }
    val borderColor = when (tone) {
        ModLibraryUpdateCardTone.Normal -> if (isDark) {
            Color(0xFF73B9FF).copy(alpha = 0.34f)
        } else {
            Color(0xFF9BC7F5)
        }

        ModLibraryUpdateCardTone.Checking -> if (isDark) {
            Color(0xFFF7C65B).copy(alpha = 0.38f)
        } else {
            Color(0xFFE3BF65)
        }

        ModLibraryUpdateCardTone.Failed -> if (isDark) {
            Color(0xFFFF9B9B).copy(alpha = 0.34f)
        } else {
            Color(0xFFF0A5A5)
        }
    }
    val title = when (tone) {
        ModLibraryUpdateCardTone.Normal -> "检查结果"
        ModLibraryUpdateCardTone.Checking -> "正在检查"
        ModLibraryUpdateCardTone.Failed -> "检查失败"
    }
    val contentColor = when (tone) {
        ModLibraryUpdateCardTone.Normal -> if (isDark) {
            Color(0xFFEAF4FF)
        } else {
            Color(0xFF17395B)
        }

        ModLibraryUpdateCardTone.Checking -> if (isDark) {
            Color(0xFFFFF1CC)
        } else {
            Color(0xFF6C5313)
        }

        ModLibraryUpdateCardTone.Failed -> if (isDark) {
            Color(0xFFFFE0E0)
        } else {
            Color(0xFF7F2A2A)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor.copy(
            alpha = if (useTransparentCardStyle) {
                if (isDark) 0.28f else 0.32f
            } else {
                if (isDark) 0.42f else 0.5f
            },
        ),
        border = BorderStroke(
            1.dp,
            borderColor.copy(
                alpha = if (useTransparentCardStyle) {
                    if (isDark) 0.28f else 0.36f
                } else {
                    if (isDark) 0.3f else 0.42f
                },
            ),
        ),
        tonalElevation = if (useTransparentCardStyle) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
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
private fun LargePreviewModLibraryCard(
    group: DownloadedModGroup,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onViewChangeNotes: () -> Unit,
) {
    val latestVersion = group.latestVersion()
    val primaryFile = group.primaryFile()
    WorkshopPanelCard(
        modifier = Modifier.clickable(onClick = onOpenDetail),
    ) {
        ModPreviewImage(
            previewImagePath = group.previewImagePath,
            contentDescription = group.itemTitle,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = group.itemTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.gameTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetricFlow(
                metrics = buildModGroupMetrics(group),
            )
            ModUpdateStatusText(result = updateResult)
            primaryFile?.let { file ->
                Text(
                    text = "最新主文件：${file.userVisiblePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (group.versionCount() > 1) {
                Text(
                    text = "当前已保存 ${group.versionCount()} 个版本，最新版本为 ${latestVersion.versionLabel()}。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkshopOutlinedButton(
                onClick = onOpenDetail,
                modifier = Modifier.weight(1f),
            ) {
                Text("查看详情")
            }
            WorkshopOutlinedButton(
                onClick = onViewChangeNotes,
                modifier = Modifier.weight(1f),
            ) {
                Text("查看更新日志")
            }
        }

        primaryFile?.let { file ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WorkshopOutlinedButton(
                    onClick = { onOpenPrimaryFile(file) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("打开最新")
                }
                WorkshopOutlinedButton(
                    onClick = { onSharePrimaryFile(file) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("分享最新")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverviewModLibraryTile(
    group: DownloadedModGroup,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    onViewChangeNotes: () -> Unit,
) {
    val primaryFile = group.primaryFile()
    val borderColor = overviewBorderColor(updateResult)
    var menuExpanded by remember(group.modGroupKey()) { mutableStateOf(false) }
    val interactionSource = remember(group.modGroupKey()) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "overviewTileScale",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 2.dp,
        animationSpec = spring(stiffness = 380f),
        label = "overviewTileElevation",
    )
    val animatedHighlightAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.1f else 0f,
        animationSpec = spring(stiffness = 500f),
        label = "overviewTileHighlight",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpenDetail,
                onLongClick = { menuExpanded = true },
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(2.dp, borderColor),
        tonalElevation = animatedElevation,
        shadowElevation = animatedElevation,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!group.previewImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = group.previewImagePath,
                    contentDescription = group.itemTitle,
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
                        text = group.itemTitle,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = animatedHighlightAlpha),
                    ),
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

            if (group.versionCount() > 1) {
                OverviewTileBadge(
                    text = "${group.versionCount()} 个版本",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }

            OverviewModLibraryContextMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                onOpenDetail = {
                    menuExpanded = false
                    onOpenDetail()
                },
                onViewChangeNotes = {
                    menuExpanded = false
                    onViewChangeNotes()
                },
                onOpenPrimaryFile = primaryFile?.let { file ->
                    {
                        menuExpanded = false
                        onOpenPrimaryFile(file)
                    }
                },
                onSharePrimaryFile = primaryFile?.let { file ->
                    {
                        menuExpanded = false
                        onSharePrimaryFile(file)
                    }
                },
            )
        }
    }
}

@Composable
private fun BoxScope.OverviewModLibraryContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpenDetail: () -> Unit,
    onViewChangeNotes: () -> Unit,
    onOpenPrimaryFile: (() -> Unit)?,
    onSharePrimaryFile: (() -> Unit)?,
) {
    WorkshopPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
    ) {
        WorkshopPopupMenuItem(
            text = { Text("查看详情") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                )
            },
            onClick = onOpenDetail,
        )
        WorkshopPopupMenuItem(
            text = { Text("查看更新日志") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                )
            },
            onClick = onViewChangeNotes,
        )
        onOpenPrimaryFile?.let { action ->
            WorkshopPopupMenuItem(
                text = { Text("打开最新主文件") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                    )
                },
                onClick = action,
            )
        }
        onSharePrimaryFile?.let { action ->
            WorkshopPopupMenuItem(
                text = { Text("分享最新主文件") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                    )
                },
                onClick = action,
            )
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
    group: DownloadedModGroup,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
    onViewChangeNotes: () -> Unit,
) {
    val latestVersion = group.latestVersion()
    WorkshopPanelCard(
        modifier = Modifier.clickable(onClick = onOpenDetail),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        text = group.itemTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${group.gameTitle} · ${group.versionCount()} 个版本 · ${group.totalFileCount()} 个文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "最新版本：${latestVersion.versionLabel()}",
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

            WorkshopTextButton(
                onClick = onViewChangeNotes,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看更新日志")
            }
        }
    }
}

private fun buildModGroupMetrics(group: DownloadedModGroup): List<String> {
    val latestVersion = group.latestVersion()
    return listOf(
        "版本 ${group.versionCount()}",
        "文件 ${group.totalFileCount()}",
        "同步 ${formatModLibraryTimestamp(latestVersion.storedAtMillis)}",
    )
}

private fun latestUpdateResult(
    group: DownloadedModGroup,
    state: ModLibraryUiState,
): ModUpdateCheckResult? =
    state.updateCheckState.results[group.cachedLatestVersionKey]

private fun filteredMetricText(
    visibleCount: Int,
    totalCount: Int,
): String =
    if (visibleCount == totalCount) {
        totalCount.toString()
    } else {
        "$visibleCount/$totalCount"
    }
