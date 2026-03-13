package top.apricityx.workshop.ui.component

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.ModUpdateCheckResult
import top.apricityx.workshop.ModUpdateCheckStatus

private val modLibraryTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal fun buildModEntryMetrics(entry: DownloadedModEntry): List<String> =
    listOf(
        "AppID ${entry.appId}",
        "模组 ${entry.publishedFileId}",
        "文件 ${entry.files.size}",
        "同步 ${formatModLibraryTimestamp(entry.storedAtMillis)}",
    )

internal fun formatModLibraryTimestamp(timestampMillis: Long): String =
    modLibraryTimeFormatter.format(
        Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()),
    )

@Composable
internal fun ModUpdateStatusText(
    result: ModUpdateCheckResult?,
    modifier: Modifier = Modifier,
) {
    val message = result?.displayText()?.takeIf(String::isNotBlank) ?: return
    Text(
        text = message,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = when (result.status) {
            ModUpdateCheckStatus.UpdateAvailable -> MaterialTheme.colorScheme.primary
            ModUpdateCheckStatus.Failed -> MaterialTheme.colorScheme.error
            ModUpdateCheckStatus.Checking -> MaterialTheme.colorScheme.secondary
            ModUpdateCheckStatus.UpToDate,
            ModUpdateCheckStatus.Unknown,
            -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
internal fun ModPreviewImage(
    previewImagePath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
) {
    if (previewImagePath.isNullOrBlank()) {
        return
    }

    AsyncImage(
        model = previewImagePath,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = if (fillMaxWidth) {
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        } else {
            modifier.aspectRatio(16f / 9f)
        },
    )
}

private fun ModUpdateCheckResult.displayText(): String =
    when (status) {
        ModUpdateCheckStatus.Unknown -> ""
        ModUpdateCheckStatus.Checking -> "正在检查创意工坊更新"
        ModUpdateCheckStatus.UpToDate -> remoteUpdatedAtMillis
            ?.let { "已是最新，工坊更新于 ${formatModLibraryTimestamp(it)}" }
            ?: "已是最新"
        ModUpdateCheckStatus.UpdateAvailable -> remoteUpdatedAtMillis
            ?.let { "发现更新，工坊更新于 ${formatModLibraryTimestamp(it)}" }
            ?: "发现更新"
        ModUpdateCheckStatus.Failed -> message ?: "检查更新失败"
    }
