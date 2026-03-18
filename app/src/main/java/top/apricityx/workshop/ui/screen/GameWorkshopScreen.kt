package top.apricityx.workshop.ui.screen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.apricityx.workshop.GameWorkshopUiState
import top.apricityx.workshop.WorkshopBrowseSortOption
import top.apricityx.workshop.WorkshopBrowseTimeWindow
import top.apricityx.workshop.displayName
import top.apricityx.workshop.formatBinaryFileSize
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.MetricPill
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopGlassIconButton
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopButton
import top.apricityx.workshop.ui.component.WorkshopOutlinedButton
import top.apricityx.workshop.ui.component.WorkshopOutlinedTextField
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.theme.workshopListContentPadding

@Composable
fun GameWorkshopScreen(
    state: GameWorkshopUiState,
    downloadedItemIds: Set<ULong>,
    pendingDownloadItemIds: Set<ULong>,
    activeDownloadItemIds: Set<ULong>,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionSelected: (WorkshopBrowseSortOption) -> Unit,
    onTimeWindowSelected: (WorkshopBrowseTimeWindow) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItemDetail: (WorkshopBrowseItem) -> Unit,
    onDownloadSingleItem: (WorkshopBrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberSaveable(
        state.game.appId.toString(),
        saver = LazyListState.Saver,
    ) {
        LazyListState()
    }
    var directPublishedFileIdText by rememberSaveable(state.game.appId.toString()) {
        mutableStateOf("")
    }
    val showingRefreshState = state.isLoading && state.items.isNotEmpty()
    val directPublishedFileId = directPublishedFileIdText.toULongOrNull()
    val canDirectDownload =
        directPublishedFileIdText.isNotBlank() &&
            directPublishedFileIdText != "0" &&
            directPublishedFileId != null
    val directDownloadState = resolveWorkshopDownloadActionState(
        publishedFileId = directPublishedFileId,
        pendingDownloadItemIds = pendingDownloadItemIds,
        activeDownloadItemIds = activeDownloadItemIds,
    )
    val shouldShowDirectDownloadCard = !state.showConnectionErrorState

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = workshopListContentPadding(topExtra = 20.dp, bottomExtra = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenSummaryCard(
                title = state.game.name,
                subtitle = state.game.shortDescription.ifBlank { "这个游戏支持 Steam 创意工坊。" },
                metrics = buildList {
                    add("AppID ${state.game.appId}")
                    add("已加载 ${state.items.size} 个模组")
                    add("排序 ${state.selectedSortOption.displayName()}")
                    if (state.selectedSortOption.supportsTimeWindow) {
                        add("范围 ${state.selectedTimeWindow.displayName()}")
                    }
                    if (state.searchQuery.isNotBlank()) {
                        add("搜索中")
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                AsyncImage(
                    model = state.game.headerImageUrl.ifBlank { state.game.capsuleImageUrl },
                    contentDescription = state.game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WorkshopOutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text("搜索模组") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f),
                    )
                    WorkshopButton(onClick = onSearch, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }

                WorkshopBrowseSortControls(
                    state = state,
                    onSortOptionSelected = onSortOptionSelected,
                    onTimeWindowSelected = onTimeWindowSelected,
                )
            }
        }

        if (shouldShowDirectDownloadCard) {
            item {
                DirectPublishedIdDownloadCard(
                    directPublishedFileIdText = directPublishedFileIdText,
                    downloadActionState = directDownloadState,
                    canDirectDownload = canDirectDownload,
                    onPublishedFileIdChange = { value ->
                        directPublishedFileIdText = value.filter(Char::isDigit)
                    },
                    onDownload = {
                        val publishedFileId = directPublishedFileId ?: return@DirectPublishedIdDownloadCard
                        onDownloadSingleItem(
                            WorkshopBrowseItem(
                                appId = state.game.appId,
                                publishedFileId = publishedFileId,
                                title = "Workshop $publishedFileId",
                                authorName = "",
                                previewImageUrl = "",
                                descriptionSnippet = "",
                            ),
                        )
                    },
                )
            }
        }

        item {
            WorkshopPanelCard {
                WorkshopOutlinedButton(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("刷新列表")
                }
            }
        }

        if (state.showConnectionErrorState) {
            item {
                WorkshopCenteredState(
                    title = "啊哦，加载超时",
                    message = state.message
                        ?: "啊哦，加载超时，您的网络环境可能不支持直连创意工坊，请开启加速器加速 steam 或科学上网后重试。",
                    actionLabel = "重试",
                    onAction = if (state.retryLoadMoreOnError) onLoadMore else onSearch,
                )
            }
        } else {
            state.message?.let { message ->
                item {
                    WorkshopMessageBanner(
                        message = message,
                        tone = if (message.contains("失败")) MessageTone.Error else MessageTone.Info,
                    )
                }
            }

            item {
                SectionHeading(
                    title = "工坊模组",
                    subtitle = if (state.searchQuery.isBlank()) {
                        "浏览当前游戏的公开创意工坊条目。"
                    } else {
                        "当前搜索：${state.searchQuery}"
                    },
                )
            }

            if (state.isLoading && state.items.isEmpty()) {
                item {
                    WorkshopLoadingBlock(label = "正在加载创意工坊列表。")
                }
            } else if (state.items.isEmpty()) {
                item {
                    WorkshopCenteredState(
                        title = if (state.searchQuery.isBlank()) "没有可显示的模组" else "没有找到结果",
                        message = if (state.searchQuery.isBlank()) {
                            state.message ?: "这个游戏当前没有抓取到公开模组。"
                        } else {
                            state.message ?: "换个关键词再试试。"
                        },
                    )
                }
            } else {
                if (showingRefreshState) {
                    item {
                        WorkshopMessageBanner(
                            message = "正在刷新当前列表，已保留你上一次浏览的位置。",
                            tone = MessageTone.Info,
                        )
                    }
                }

                items(state.items, key = { it.publishedFileId.toString() }) { item ->
                    val downloadActionState = resolveWorkshopDownloadActionState(
                        publishedFileId = item.publishedFileId,
                        pendingDownloadItemIds = pendingDownloadItemIds,
                        activeDownloadItemIds = activeDownloadItemIds,
                    )
                    WorkshopItemCard(
                        item = item,
                        isDownloaded = item.publishedFileId in downloadedItemIds,
                        downloadActionState = downloadActionState,
                        onOpenDetail = { onOpenItemDetail(item) },
                        onDownload = { onDownloadSingleItem(item) },
                    )
                }
            }

            if (state.isLoadingMore) {
                item {
                    WorkshopLoadingBlock(label = "正在加载更多模组。")
                }
            } else if (state.hasNextPage) {
                item {
                    WorkshopOutlinedButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("加载更多")
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectPublishedIdDownloadCard(
    directPublishedFileIdText: String,
    downloadActionState: WorkshopDownloadActionState,
    canDirectDownload: Boolean,
    onPublishedFileIdChange: (String) -> Unit,
    onDownload: () -> Unit,
) {
    WorkshopPanelCard {
        Text(
            text = "直接填写 publishedID",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "如果你已经知道这个工坊物品的 publishedID，可以直接发起下载。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            WorkshopOutlinedTextField(
                value = directPublishedFileIdText,
                onValueChange = onPublishedFileIdChange,
                label = { Text("直接填写 publishedID") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            WorkshopButton(
                onClick = {
                    if (downloadActionState == WorkshopDownloadActionState.Idle) {
                        onDownload()
                    }
                },
                enabled = downloadActionState != WorkshopDownloadActionState.Idle || canDirectDownload,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                when (downloadActionState) {
                    WorkshopDownloadActionState.Idle -> Text("下载")
                    WorkshopDownloadActionState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(" 准备下载…")
                    }

                    WorkshopDownloadActionState.Downloading -> {
                        DownloadingAnimatedIcon()
                        Text(" 下载中…")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkshopBrowseSortControls(
    state: GameWorkshopUiState,
    onSortOptionSelected: (WorkshopBrowseSortOption) -> Unit,
    onTimeWindowSelected: (WorkshopBrowseTimeWindow) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            WorkshopBrowseSortOption.entries.forEach { option ->
                WorkshopBrowseSelectionChip(
                    label = option.displayName(),
                    selected = option == state.selectedSortOption,
                    onClick = { onSortOptionSelected(option) },
                )
            }
        }

        if (state.selectedSortOption.supportsTimeWindow) {
            Text(
                text = "热门范围",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorkshopBrowseTimeWindow.entries.forEach { option ->
                    WorkshopBrowseSelectionChip(
                        label = option.displayName(),
                        selected = option == state.selectedTimeWindow,
                        onClick = { onTimeWindowSelected(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkshopBrowseSelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val containerColor = when {
        selected && isDark -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isDark -> MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val borderColor = when {
        selected && isDark -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        isDark -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    }
    val contentColor = when {
        selected && isDark -> Color(0xFFEAF4FF)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}

@Composable
private fun WorkshopItemCard(
    item: WorkshopBrowseItem,
    isDownloaded: Boolean,
    downloadActionState: WorkshopDownloadActionState,
    onOpenDetail: () -> Unit,
    onDownload: () -> Unit,
) {
    val sizeLabel = item.fileSizeBytes?.let { sizeBytes ->
        "大小 ${formatBinaryFileSize(sizeBytes)}"
    }

    WorkshopPanelCard(
        modifier = Modifier.clickable(onClick = onOpenDetail),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AsyncImage(
                model = item.previewImageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(104.dp)
                    .clip(MaterialTheme.shapes.medium),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "by ${item.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sizeLabel?.let { label ->
                    MetricPill(text = label)
                }
                if (item.descriptionSnippet.isNotBlank()) {
                    Text(
                        text = item.descriptionSnippet,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            WorkshopDownloadActionButton(
                actionState = downloadActionState,
                isDownloaded = isDownloaded,
                onClick = onDownload,
                modifier = Modifier.align(Alignment.Top),
            )
        }
    }
}

@Composable
private fun WorkshopDownloadActionButton(
    actionState: WorkshopDownloadActionState,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idleImageVector = if (isDownloaded) Icons.Default.Refresh else Icons.Default.Download
    val contentDescription = when (actionState) {
        WorkshopDownloadActionState.Idle -> if (isDownloaded) "重新下载" else "下载"
        WorkshopDownloadActionState.Loading -> "准备下载"
        WorkshopDownloadActionState.Downloading -> "下载中"
    }

    WorkshopGlassIconButton(
        onClick = onClick,
        imageVector = if (actionState == WorkshopDownloadActionState.Idle) idleImageVector else Icons.Default.Sync,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = actionState == WorkshopDownloadActionState.Idle,
        content = when (actionState) {
            WorkshopDownloadActionState.Idle -> null
            WorkshopDownloadActionState.Loading -> {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            WorkshopDownloadActionState.Downloading -> {
                {
                    DownloadingAnimatedIcon()
                }
            }
        },
    )
}
