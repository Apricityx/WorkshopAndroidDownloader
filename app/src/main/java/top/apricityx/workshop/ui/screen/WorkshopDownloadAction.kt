package top.apricityx.workshop.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal enum class WorkshopDownloadActionState {
    Idle,
    Loading,
    Downloading,
}

internal fun resolveWorkshopDownloadActionState(
    publishedFileId: ULong?,
    pendingDownloadItemIds: Set<ULong>,
    activeDownloadItemIds: Set<ULong>,
): WorkshopDownloadActionState = when {
    publishedFileId != null && publishedFileId in activeDownloadItemIds ->
        WorkshopDownloadActionState.Downloading

    publishedFileId != null && publishedFileId in pendingDownloadItemIds ->
        WorkshopDownloadActionState.Loading

    else -> WorkshopDownloadActionState.Idle
}

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
