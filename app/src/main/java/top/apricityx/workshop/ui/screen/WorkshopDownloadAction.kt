package top.apricityx.workshop.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.WorkshopModStatus

internal fun WorkshopModStatus.actionLabel(): String =
    when (this) {
        WorkshopModStatus.LatestDownloaded -> "已下载"
        WorkshopModStatus.UpdateAvailable -> "更新到最新版本"
        WorkshopModStatus.NotDownloaded -> "下载"
        WorkshopModStatus.Downloading -> "下载中"
    }

internal fun WorkshopModStatus.actionIcon(): ImageVector =
    when (this) {
        WorkshopModStatus.LatestDownloaded -> Icons.Default.Done
        WorkshopModStatus.UpdateAvailable -> Icons.Default.Upload
        WorkshopModStatus.NotDownloaded -> Icons.Default.Download
        WorkshopModStatus.Downloading -> Icons.Default.Sync
    }

internal fun WorkshopModStatus.isDownloadActionEnabled(): Boolean =
    this == WorkshopModStatus.UpdateAvailable || this == WorkshopModStatus.NotDownloaded

@Composable
internal fun DownloadingAnimatedIcon(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
    )

    Icon(
        imageVector = Icons.Default.Sync,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = rotation },
    )
}
