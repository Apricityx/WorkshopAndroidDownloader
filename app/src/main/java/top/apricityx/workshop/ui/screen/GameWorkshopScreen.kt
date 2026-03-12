package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.apricityx.workshop.GameWorkshopUiState
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.SectionHeading
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard

@Composable
fun GameWorkshopScreen(
    state: GameWorkshopUiState,
    onSearchQueryChange: (String) -> Unit,
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
    val showingRefreshState = state.isLoading && state.items.isNotEmpty()

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenSummaryCard(
                title = state.game.name,
                subtitle = state.game.shortDescription.ifBlank { "这个游戏支持 Steam 创意工坊。" },
                metrics = listOf(
                    "AppID ${state.game.appId}",
                    "已加载 ${state.items.size} 个模组",
                    if (state.searchQuery.isBlank()) "当前排序 热门" else "搜索中",
                ),
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
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text("搜索模组") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onSearch, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }
            }
        }

        item {
            WorkshopPanelCard {
                OutlinedButton(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("刷新列表")
                }
            }
        }

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
                    title = "没有可显示的模组",
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
                WorkshopItemCard(
                    item = item,
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
                OutlinedButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("加载更多")
                }
            }
        }
    }
}

@Composable
private fun WorkshopItemCard(
    item: WorkshopBrowseItem,
    onOpenDetail: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDetail,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AsyncImage(
                model = item.previewImageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(92.dp)
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
                if (item.descriptionSnippet.isNotBlank()) {
                    Text(
                        text = item.descriptionSnippet,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            TextButton(
                onClick = onDownload,
                modifier = Modifier.align(Alignment.Top),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text(" 下载")
            }
        }
    }
}
