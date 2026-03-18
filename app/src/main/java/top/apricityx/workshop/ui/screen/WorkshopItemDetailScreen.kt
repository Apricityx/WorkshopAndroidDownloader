package top.apricityx.workshop.ui.screen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import top.apricityx.workshop.WorkshopItemDetailUiState
import top.apricityx.workshop.formatBinaryFileSize
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.data.WorkshopRequiredItem
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.MetricPill
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.WorkshopButton
import top.apricityx.workshop.ui.component.WorkshopCenteredState
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopOutlinedButton
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.theme.workshopChromePadding

@Composable
internal fun WorkshopItemDetailScreen(
    state: WorkshopItemDetailUiState,
    downloadedItemIds: Set<ULong>,
    downloadActionState: WorkshopDownloadActionState,
    onRetry: () -> Unit,
    onTranslateDescription: () -> Unit,
    onDownload: (WorkshopBrowseItem) -> Unit,
    onOpenRequiredItem: (WorkshopBrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val description = detail?.description?.ifBlank { state.item.descriptionSnippet }.orEmpty()
    val canTranslateDescription = detail != null && description.isNotBlank()
    val metrics = buildList {
        add("作者 ${detail?.authorName ?: state.item.authorName}")
        detail?.subscriptions?.let { add("订阅 ${formatCount(it)}") }
        detail?.views?.let { add("浏览 ${formatCount(it)}") }
        detail?.fileSizeBytes?.let { add("大小 ${formatBinaryFileSize(it)}") }
        detail?.requiredItems?.takeIf { requiredItems -> requiredItems.isNotEmpty() }?.let { requiredItems ->
            add("前置 ${requiredItems.size}")
        }
    }

    if (state.showConnectionErrorState) {
        WorkshopCenteredState(
            title = "啊哦，加载超时",
            message = state.message
                ?: "啊哦，加载超时，您的网络环境可能不支持直连创意工坊，请开启加速器加速 steam 或科学上网后重试。",
            actionLabel = "重试",
            onAction = onRetry,
            modifier = modifier.workshopChromePadding(topExtra = 24.dp, bottomExtra = 24.dp),
        )
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .workshopChromePadding(topExtra = 8.dp, bottomExtra = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenSummaryCard(
                title = detail?.title ?: state.item.title,
                subtitle = "PublishedFileID ${state.item.publishedFileId}",
                metrics = metrics,
            ) {
                WorkshopDetailHeaderImage(
                    thumbnailUrl = state.item.previewImageUrl,
                    fullImageUrl = detail?.previewImageUrl,
                    contentDescription = state.item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                if (description.isNotBlank()) {
                    if (state.translatedDescription != null) {
                        Text(
                            text = "原文",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (canTranslateDescription) {
                    WorkshopOutlinedButton(
                        onClick = onTranslateDescription,
                        enabled = !state.isTranslatingDescription,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isTranslatingDescription) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(" 正在翻译描述…")
                        } else {
                            Text(
                                if (state.translatedDescription == null) {
                                    "翻译描述"
                                } else {
                                    "重新翻译描述"
                                },
                            )
                        }
                    }
                }

                state.translationErrorMessage?.let { translationErrorMessage ->
                    WorkshopMessageBanner(
                        message = translationErrorMessage,
                        tone = MessageTone.Error,
                    )
                }

                state.translatedDescription?.takeIf(String::isNotBlank)?.let { translatedDescription ->
                    Text(
                        text = "译文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = translatedDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                WorkshopButton(
                    onClick = {
                        if (downloadActionState == WorkshopDownloadActionState.Idle) {
                            onDownload(state.item)
                        }
                    },
                    enabled = downloadActionState == WorkshopDownloadActionState.Idle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when (downloadActionState) {
                        WorkshopDownloadActionState.Idle -> {
                            Icon(
                                imageVector = if (state.item.publishedFileId in downloadedItemIds) {
                                    Icons.Default.Refresh
                                } else {
                                    Icons.Default.Download
                                },
                                contentDescription = null,
                            )
                            Text(
                                if (state.item.publishedFileId in downloadedItemIds) {
                                    " 重新下载"
                                } else {
                                    " 下载"
                                },
                            )
                        }

                        WorkshopDownloadActionState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(" 下载中")
                        }

                        WorkshopDownloadActionState.Downloading -> {
                            DownloadingAnimatedIcon()
                            Text(" 下载中")
                        }
                    }
                }

                if (state.message != null) {
                    WorkshopOutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                        )
                        Text(" 重试加载详情")
                    }
                }
            }

            if (state.isLoading) {
                WorkshopLoadingBlock(label = "正在加载更完整的模组详情。")
            }

            state.message?.let { message ->
                WorkshopMessageBanner(
                    message = "$message\n如果网络不稳定，可以稍后重试；下载功能仍可直接使用。",
                    tone = MessageTone.Error,
                )
            }

            detail?.requiredItems?.takeIf { requiredItems -> requiredItems.isNotEmpty() }?.let { requiredItems ->
                WorkshopPanelCard {
                    Text(
                        text = "前置内容",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "下载这个内容前，建议先准备以下 ${requiredItems.size} 个前置工坊物品。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    requiredItems.forEach { requiredItem ->
                        RequiredItemLine(
                            item = requiredItem,
                            isDownloaded = requiredItem.publishedFileId in downloadedItemIds,
                            onClick = { onOpenRequiredItem(requiredItem.toBrowseItem()) },
                        )
                    }
                }
            }

            detail?.let {
                WorkshopPanelCard {
                    Text(
                        text = "模组信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    MetricFlow(
                        metrics = listOfNotNull(
                            it.timeUpdatedEpochSeconds?.let(::formatUpdatedTime)?.let { value -> "更新 $value" },
                            it.favorited?.let(::formatCount)?.let { value -> "收藏 $value" },
                            it.tags.takeIf { tags -> tags.isNotEmpty() }?.let { tags -> "标签 ${tags.size}" },
                        ),
                    )
                    DetailLine(
                        label = "文件大小",
                        value = it.fileSizeBytes?.let(::formatBinaryFileSize) ?: "未知",
                    )
                    DetailLine(label = "更新时间", value = it.timeUpdatedEpochSeconds?.let(::formatUpdatedTime) ?: "未知")
                    DetailLine(label = "订阅数", value = it.subscriptions?.let(::formatCount) ?: "未知")
                    DetailLine(label = "收藏数", value = it.favorited?.let(::formatCount) ?: "未知")
                    DetailLine(label = "浏览量", value = it.views?.let(::formatCount) ?: "未知")
                    DetailLine(
                        label = "标签",
                        value = it.tags.takeIf { tags -> tags.isNotEmpty() }?.joinToString(" / ") ?: "暂无标签",
                    )
                }
            }
        }
    }

}

@Composable
private fun WorkshopDetailHeaderImage(
    thumbnailUrl: String,
    fullImageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(MaterialTheme.shapes.large)) {
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val resolvedFullImageUrl = fullImageUrl?.ifBlank { null }
        if (resolvedFullImageUrl != null && resolvedFullImageUrl != thumbnailUrl) {
            AsyncImage(
                model = resolvedFullImageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (thumbnailUrl.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RequiredItemLine(
    item: WorkshopRequiredItem,
    isDownloaded: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.previewImageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.previewImageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无\n封面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isDownloaded) {
                        MetricPill(text = "已下载")
                    }
                }
                Text(
                    text = item.descriptionSnippet.ifBlank { "暂无描述" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatUpdatedTime(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(
            Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault()),
        )

private fun formatCount(value: Long): String = "%,d".format(value)
