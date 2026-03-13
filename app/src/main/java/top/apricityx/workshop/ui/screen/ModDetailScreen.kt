package top.apricityx.workshop.ui.screen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.ExportedDownloadFile
import top.apricityx.workshop.primaryFile
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ModPreviewImage
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.component.buildModEntryMetrics
import top.apricityx.workshop.ui.component.formatModLibraryTimestamp

@Composable
fun ModDetailScreen(
    entry: DownloadedModEntry,
    onOpenFile: (ExportedDownloadFile) -> Unit,
    onShareFile: (ExportedDownloadFile) -> Unit,
    onRemoveMod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenSummaryCard(
            title = entry.itemTitle,
            subtitle = entry.gameTitle,
            metrics = buildModEntryMetrics(entry),
        ) {
            ModPreviewImage(
                previewImagePath = entry.previewImagePath,
                contentDescription = entry.itemTitle,
            )
            entry.primaryFile()?.let { primaryFile ->
                Text(
                    text = "主文件：${primaryFile.relativePath}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(
                onClick = onRemoveMod,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除本地模组")
            }
        }

        WorkshopPanelCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "导出文件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()
            if (entry.files.isEmpty()) {
                Text(
                    text = "暂无可操作文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    entry.files.forEach { file ->
                        FileRow(
                            file = file,
                            sizeText = Formatter.formatFileSize(context, file.sizeBytes),
                            onOpenFile = { onOpenFile(file) },
                            onShareFile = { onShareFile(file) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: ExportedDownloadFile,
    sizeText: String,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = file.relativePath,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MetricFlow(
            metrics = listOf(
                sizeText,
                formatModLibraryTimestamp(file.modifiedEpochMillis),
            ),
        )
        Text(
            text = file.userVisiblePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onOpenFile,
                modifier = Modifier.weight(1f),
            ) {
                Text("打开")
            }
            OutlinedButton(
                onClick = onShareFile,
                modifier = Modifier.weight(1f),
            ) {
                Text("分享")
            }
        }
    }
}
