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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    icon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val presentation = resolveMetricPillPresentation(
        text = text,
        icon = icon,
        contentDescription = contentDescription,
    )
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
            MetricPillContent(presentation = presentation)
        }
        return
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        MetricPillContent(presentation = presentation)
    }
}

@Composable
private fun MetricPillContent(
    presentation: MetricPillPresentation,
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presentation.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = presentation.contentDescription,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = presentation.displayText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class MetricPillPresentation(
    val displayText: String,
    val icon: ImageVector? = null,
    val contentDescription: String? = null,
)

private data class MetricPrefixIconRule(
    val prefix: String,
    val icon: ImageVector,
    val label: String,
)

private val MetricPrefixIconRules = listOf(
    MetricPrefixIconRule(prefix = "AppID ", icon = Icons.Default.Dashboard, label = "AppID"),
    MetricPrefixIconRule(prefix = "模组 ", icon = Icons.Default.Extension, label = "模组"),
    MetricPrefixIconRule(prefix = "版本 ", icon = Icons.Default.Layers, label = "版本"),
    MetricPrefixIconRule(prefix = "文件 ", icon = Icons.Default.Folder, label = "文件"),
    MetricPrefixIconRule(prefix = "同步 ", icon = Icons.Default.Schedule, label = "同步"),
    MetricPrefixIconRule(prefix = "可更新 ", icon = Icons.Default.Refresh, label = "可更新"),
    MetricPrefixIconRule(prefix = "运行中 ", icon = Icons.Default.Sync, label = "运行中"),
    MetricPrefixIconRule(prefix = "排队 ", icon = Icons.Default.Schedule, label = "排队"),
    MetricPrefixIconRule(prefix = "暂停 ", icon = Icons.Default.Pause, label = "暂停"),
    MetricPrefixIconRule(prefix = "历史 ", icon = Icons.Default.History, label = "历史"),
    MetricPrefixIconRule(prefix = "阶段 ", icon = Icons.AutoMirrored.Filled.ViewList, label = "阶段"),
    MetricPrefixIconRule(prefix = "账号 ", icon = Icons.Default.Person, label = "账号"),
    MetricPrefixIconRule(prefix = "总进度 ", icon = Icons.Default.DonutLarge, label = "总进度"),
    MetricPrefixIconRule(prefix = "数据 ", icon = Icons.Default.Storage, label = "数据"),
    MetricPrefixIconRule(prefix = "分块 ", icon = Icons.Default.ViewModule, label = "分块"),
    MetricPrefixIconRule(prefix = "速度 ", icon = Icons.Default.Speed, label = "速度"),
    MetricPrefixIconRule(prefix = "更新 ", icon = Icons.Default.Update, label = "更新"),
    MetricPrefixIconRule(prefix = "收藏 ", icon = Icons.Default.Favorite, label = "收藏"),
    MetricPrefixIconRule(prefix = "标签 ", icon = Icons.AutoMirrored.Filled.Label, label = "标签"),
    MetricPrefixIconRule(prefix = "作者 ", icon = Icons.Default.Person, label = "作者"),
    MetricPrefixIconRule(prefix = "订阅 ", icon = Icons.Default.Download, label = "订阅"),
    MetricPrefixIconRule(prefix = "浏览 ", icon = Icons.Default.Visibility, label = "浏览"),
    MetricPrefixIconRule(prefix = "大小 ", icon = Icons.Default.Storage, label = "大小"),
    MetricPrefixIconRule(prefix = "前置 ", icon = Icons.Default.Extension, label = "前置"),
    MetricPrefixIconRule(prefix = "评论 ", icon = Icons.AutoMirrored.Filled.Comment, label = "评论"),
)

private val MetricExactIconRules = mapOf(
    "等待中" to Icons.Default.Schedule,
    "排队中" to Icons.Default.Schedule,
    "解析元数据" to Icons.Default.Info,
    "连接内容服务器" to Icons.Default.Cloud,
    "下载中" to Icons.Default.Sync,
    "已暂停" to Icons.Default.Pause,
    "已完成" to Icons.Default.CheckCircle,
    "失败" to Icons.Default.ErrorOutline,
    "已下载" to Icons.Default.CheckCircle,
    "时间未知" to Icons.Default.Schedule,
)

private val MetricTimestampRegex = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(:\\d{2})?$")
private val MetricBinarySizeRegex = Regex("^\\d+(?:\\.\\d+)?\\s?(?:B|KB|MB|GB|TB)$")
private val MetricBinarySpeedRegex = Regex("^\\d+(?:\\.\\d+)?\\s?(?:B|KB|MB|GB|TB)/s$")
private val MetricBinaryProgressRegex =
    Regex("^\\d+(?:\\.\\d+)?\\s?(?:B|KB|MB|GB|TB)\\s*/\\s*\\d+(?:\\.\\d+)?\\s?(?:B|KB|MB|GB|TB)$")

private fun resolveMetricPillPresentation(
    text: String,
    icon: ImageVector?,
    contentDescription: String?,
): MetricPillPresentation {
    if (icon != null) {
        return MetricPillPresentation(
            displayText = text,
            icon = icon,
            contentDescription = contentDescription,
        )
    }

    val normalized = text.trim()
    MetricPrefixIconRules.firstOrNull { rule -> normalized.startsWith(rule.prefix) }?.let { rule ->
        val stripped = normalized.removePrefix(rule.prefix).trim()
        return MetricPillPresentation(
            displayText = stripped.ifBlank { normalized },
            icon = rule.icon,
            contentDescription = rule.label,
        )
    }

    MetricExactIconRules[normalized]?.let { resolvedIcon ->
        return MetricPillPresentation(
            displayText = normalized,
            icon = resolvedIcon,
            contentDescription = normalized,
        )
    }

    return when {
        MetricTimestampRegex.matches(normalized) || looksLikeRelativeMetricTime(normalized) ->
            MetricPillPresentation(
                displayText = normalized,
                icon = Icons.Default.Schedule,
                contentDescription = "时间",
            )

        MetricBinarySpeedRegex.matches(normalized) ->
            MetricPillPresentation(
                displayText = normalized,
                icon = Icons.Default.Speed,
                contentDescription = "速度",
            )

        MetricBinaryProgressRegex.matches(normalized) || MetricBinarySizeRegex.matches(normalized) ->
            MetricPillPresentation(
                displayText = normalized,
                icon = Icons.Default.Storage,
                contentDescription = "大小",
            )

        else -> MetricPillPresentation(displayText = normalized)
    }
}

private fun looksLikeRelativeMetricTime(text: String): Boolean =
    text.endsWith("前") ||
        text.contains("刚刚") ||
        text.contains("今天") ||
        text.contains("昨天")
