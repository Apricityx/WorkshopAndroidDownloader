package top.apricityx.workshop.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled

enum class MessageTone {
    Info,
    Success,
    Error,
}

@Composable
fun WorkshopPanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isLiquidFrontend = isLiquidGlassFrontendEnabled()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLiquidFrontend) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = if (isLiquidFrontend) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun ScreenSummaryCard(
    title: String,
    subtitle: String,
    metrics: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    if (isLiquidGlassFrontendEnabled()) {
        WorkshopLensBackdropSurface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            lensHeight = 10.dp,
            lensAmount = 18.dp,
            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (metrics.isNotEmpty()) {
                    MetricFlow(metrics = metrics)
                }
                CompositionLocalProvider(LocalWorkshopPreferLensButtons provides false) {
                    content?.invoke(this)
                }
            }
        }
        return
    }

    WorkshopPanelCard(modifier = modifier) {
        CompositionLocalProvider(LocalWorkshopPreferLensButtons provides false) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (metrics.isNotEmpty()) {
                MetricFlow(metrics = metrics)
            }
            content?.invoke(this)
        }
    }
}

@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun WorkshopMessageBanner(
    message: String,
    tone: MessageTone,
    modifier: Modifier = Modifier,
) {
    val isLiquidFrontend = isLiquidGlassFrontendEnabled()
    val containerColor = when (tone) {
        MessageTone.Info -> MaterialTheme.colorScheme.surfaceVariant
        MessageTone.Success -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        MessageTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    }
    val contentColor = when (tone) {
        MessageTone.Info -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageTone.Success -> MaterialTheme.colorScheme.primary
        MessageTone.Error -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isLiquidFrontend) {
                containerColor.copy(
                    alpha = when (tone) {
                        MessageTone.Info -> 0.22f
                        MessageTone.Success -> 0.16f
                        MessageTone.Error -> 0.18f
                    },
                )
            } else {
                containerColor
            },
        ),
        border = if (isLiquidFrontend) {
            BorderStroke(1.dp, contentColor.copy(alpha = 0.18f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}

@Composable
fun WorkshopCenteredState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        WorkshopPanelCard(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                WorkshopButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun WorkshopLoadingBlock(
    label: String,
    modifier: Modifier = Modifier,
) {
    WorkshopPanelCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricFlow(
    metrics: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { metric ->
            MetricPill(text = metric)
        }
    }
}

@Composable
fun MetricPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (isLiquidGlassFrontendEnabled()) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.22f else 0.16f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.14f else 0.1f),
            ),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
