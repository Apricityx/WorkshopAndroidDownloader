package top.apricityx.workshop.ui.screen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import top.apricityx.workshop.WorkshopItemDetailUiState
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.WorkshopLoadingBlock
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard

@Composable
fun WorkshopItemDetailScreen(
    state: WorkshopItemDetailUiState,
    onRetry: () -> Unit,
    onDownload: (WorkshopBrowseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val context = LocalContext.current
    val description = detail?.description?.ifBlank { state.item.descriptionSnippet }.orEmpty()
    val metrics = buildList {
        add("作者 ${detail?.authorName ?: state.item.authorName}")
        detail?.subscriptions?.let { add("订阅 ${formatCount(it)}") }
        detail?.views?.let { add("浏览 ${formatCount(it)}") }
        detail?.fileSizeBytes?.let { add("大小 ${Formatter.formatFileSize(context, it)}") }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
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
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Button(
                onClick = { onDownload(state.item) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                )
                Text(" 开始下载")
            }

            if (state.message != null) {
                OutlinedButton(
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
                    value = it.fileSizeBytes?.let { bytes -> Formatter.formatFileSize(context, bytes) } ?: "未知",
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

private fun formatUpdatedTime(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(
            Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault()),
        )

private fun formatCount(value: Long): String = "%,d".format(value)
