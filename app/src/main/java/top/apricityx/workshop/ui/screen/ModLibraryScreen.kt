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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.component.formatModLibraryTimestamp
import top.apricityx.workshop.versionCount
import top.apricityx.workshop.versionLabel

private val OverviewCheckingBorderColor = Color(0xFFF59E0B)
private val OverviewUpdateAvailableBorderColor = Color(0xFF22C55E)

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
    modifier: Modifier = Modifier,
) {
    val visibleItems = sortModLibraryGroups(
        items = filterModLibraryGroups(
            items = state.items,
            filterState = state.filterState,
        ),
        sortOption = state.sortOption,
    )

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
            visibleItems = visibleItems,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
            onOpenModDetail = onOpenModDetail,
            onOpenPrimaryFile = onOpenPrimaryFile,
            onSharePrimaryFile = onSharePrimaryFile,
            modifier = modifier,
        )

        else -> ListModLibraryContent(
            state = state,
            visibleItems = visibleItems,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
            onOpenModDetail = onOpenModDetail,
            onOpenPrimaryFile = onOpenPrimaryFile,
            onSharePrimaryFile = onSharePrimaryFile,
            modifier = modifier,
        )
    }
}

@Composable
private fun ListModLibraryContent(
    state: ModLibraryUiState,
    visibleItems: List<DownloadedModGroup>,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
    onOpenModDetail: (DownloadedModGroup) -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        modLibraryHeaderItems(
            state = state,
            visibleItems = visibleItems,
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
                    )

                    ModLibraryDisplayMode.CompactList -> CompactListModLibraryCard(
                        group = group,
                        updateResult = latestUpdateResult(group, state),
                        onOpenDetail = { onOpenModDetail(group) },
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
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
    onOpenModDetail: (DownloadedModGroup) -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
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
            visibleItems = visibleItems,
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
                )
            }
        }
    }
}

private fun LazyListScope.modLibraryHeaderItems(
    state: ModLibraryUiState,
    visibleItems: List<DownloadedModGroup>,
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
            visibleItems = visibleItems,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
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
    visibleItems: List<DownloadedModGroup>,
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
            visibleItems = visibleItems,
            onCheckUpdates = onCheckUpdates,
            onToggleFilterPanel = onToggleFilterPanel,
            onSearchQueryChange = onSearchQueryChange,
            onGameFilterSelected = onGameFilterSelected,
            onSortOptionSelected = onSortOptionSelected,
            onClearFilters = onClearFilters,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModLibrarySummaryCard(
    state: ModLibraryUiState,
    visibleItems: List<DownloadedModGroup>,
    onCheckUpdates: () -> Unit,
    onToggleFilterPanel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGameFilterSelected: (String?) -> Unit,
    onSortOptionSelected: (ModLibrarySortOption) -> Unit,
    onClearFilters: () -> Unit,
) {
    val availableGames = availableModLibraryGames(state.items)
    val totalMods = state.items.size
    val totalVersions = state.totalVersionCount()
    val totalFiles = state.totalFileCount()
    val totalUpdateAvailable = state.latestUpdateAvailableCount()
    val visibleVersions = visibleItems.sumOf(DownloadedModGroup::versionCount)
    val visibleFiles = visibleItems.sumOf(DownloadedModGroup::totalFileCount)
    val visibleUpdateAvailable = visibleItems.count {
        latestUpdateResult(it, state)?.status == ModUpdateCheckStatus.UpdateAvailable
    }

    ScreenSummaryCard(
        title = "模组库",
        subtitle = state.displayMode.screenSubtitle(),
        metrics = listOf(
            "模组 ${filteredMetricText(visibleItems.size, totalMods)}",
            "版本 ${filteredMetricText(visibleVersions, totalVersions)}",
            "文件 ${filteredMetricText(visibleFiles, totalFiles)}",
            "可更新 ${filteredMetricText(visibleUpdateAvailable, totalUpdateAvailable)}",
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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleFilterPanel),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    OutlinedTextField(
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
                            FilterChip(
                                selected = sortOption == state.sortOption,
                                onClick = { onSortOptionSelected(sortOption) },
                                label = { Text(sortOption.displayName()) },
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
                        FilterChip(
                            selected = state.filterState.selectedGameTitle == null,
                            onClick = { onGameFilterSelected(null) },
                            label = { Text("全部游戏") },
                        )
                        availableGames.forEach { gameTitle ->
                            FilterChip(
                                selected = state.filterState.selectedGameTitle == gameTitle,
                                onClick = { onGameFilterSelected(gameTitle) },
                                label = { Text(gameTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    text = "当前筛选后显示 ${visibleItems.size} 个模组。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearFilters) {
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

private fun modLibrarySummaryTone(state: ModLibraryUiState): MessageTone =
    if (state.updateCheckState.results.values.any { it.status == ModUpdateCheckStatus.Failed }) {
        MessageTone.Error
    } else {
        MessageTone.Success
    }

@Composable
private fun LargePreviewModLibraryCard(
    group: DownloadedModGroup,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
    onOpenPrimaryFile: (ExportedDownloadFile) -> Unit,
    onSharePrimaryFile: (ExportedDownloadFile) -> Unit,
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
            OutlinedButton(
                onClick = onOpenDetail,
                modifier = Modifier.weight(1f),
            ) {
                Text("查看详情")
            }
            primaryFile?.let { file ->
                OutlinedButton(
                    onClick = { onOpenPrimaryFile(file) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("打开最新")
                }
                OutlinedButton(
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

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("查看详情") },
                    onClick = {
                        menuExpanded = false
                        onOpenDetail()
                    },
                )
                primaryFile?.let { file ->
                    DropdownMenuItem(
                        text = { Text("打开最新主文件") },
                        onClick = {
                            menuExpanded = false
                            onOpenPrimaryFile(file)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("分享最新主文件") },
                        onClick = {
                            menuExpanded = false
                            onSharePrimaryFile(file)
                        },
                    )
                }
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
    group: DownloadedModGroup,
    updateResult: ModUpdateCheckResult?,
    onOpenDetail: () -> Unit,
) {
    val latestVersion = group.latestVersion()
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
    state.updateCheckState.results[group.latestVersion().modLibraryKey()]

private fun ModLibraryUiState.totalVersionCount(): Int =
    items.sumOf { it.versionCount() }

private fun ModLibraryUiState.totalFileCount(): Int =
    items.sumOf { it.totalFileCount() }

private fun ModLibraryUiState.latestUpdateAvailableCount(): Int =
    items.count {
        it.latestUpdateStatus(updateCheckState.results) == ModUpdateCheckStatus.UpdateAvailable
    }

private fun filteredMetricText(
    visibleCount: Int,
    totalCount: Int,
): String =
    if (visibleCount == totalCount) {
        totalCount.toString()
    } else {
        "$visibleCount/$totalCount"
    }
