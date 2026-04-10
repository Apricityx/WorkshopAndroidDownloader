package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.DownloadedModGroup
import top.apricityx.workshop.ExportedDownloadFile
import top.apricityx.workshop.ModLibraryDescriptionTranslationUiState
import top.apricityx.workshop.ModUpdateCheckResult
import top.apricityx.workshop.ModUpdateCheckStatus
import top.apricityx.workshop.formatBinaryFileSize
import top.apricityx.workshop.latestVersion
import top.apricityx.workshop.modLibraryKey
import top.apricityx.workshop.primaryFile
import top.apricityx.workshop.totalFileCount
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.MetricFlow
import top.apricityx.workshop.ui.component.ModPreviewImage
import top.apricityx.workshop.ui.component.ModUpdateStatusText
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopButton
import top.apricityx.workshop.ui.component.WorkshopDestructiveButton
import top.apricityx.workshop.ui.component.WorkshopOutlinedButton
import top.apricityx.workshop.ui.component.WorkshopPanelCard
import top.apricityx.workshop.ui.component.formatModLibraryTimestamp
import top.apricityx.workshop.ui.theme.workshopChromePadding
import top.apricityx.workshop.versionCount
import top.apricityx.workshop.versionLabel

@Composable
fun ModDetailScreen(
    group: DownloadedModGroup,
    descriptionTranslationState: ModLibraryDescriptionTranslationUiState,
    updateResults: Map<String, ModUpdateCheckResult>,
    onTranslateDescription: () -> Unit,
    onRenameMod: () -> Unit,
    onOpenFile: (ExportedDownloadFile) -> Unit,
    onShareFile: (ExportedDownloadFile) -> Unit,
    onUpdateMod: (DownloadedModEntry) -> Unit,
    onRemoveMod: (DownloadedModEntry) -> Unit,
    onViewChangeNotes: (DownloadedModGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestVersion = group.latestVersion()
    val latestUpdateResult = updateResults[latestVersion.modLibraryKey()]
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .workshopChromePadding(topExtra = 8.dp, bottomExtra = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenSummaryCard(
            title = group.itemTitle,
            subtitle = group.gameTitle,
            metrics = listOf(
                "AppID ${group.appId}",
                "模组 ${group.publishedFileId}",
                "版本 ${group.versionCount()}",
                "文件 ${group.totalFileCount()}",
            ),
        ) {
            ModPreviewImage(
                previewImagePath = group.previewImagePath,
                contentDescription = group.itemTitle,
            )
            latestVersion.primaryFile()?.let { primaryFile ->
                Text(
                    text = "最新主文件：${primaryFile.relativePath}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ModUpdateStatusText(result = latestUpdateResult)
            WorkshopOutlinedButton(
                onClick = onRenameMod,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("重命名模组")
            }
            WorkshopOutlinedButton(
                onClick = { onViewChangeNotes(group) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看更新日志")
            }
            if (group.versionCount() > 1) {
                Text(
                    text = "该模组共保存了 ${group.versionCount()} 个版本，下面可以分别查看、更新或删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (group.description.isNotBlank()) {
            WorkshopPanelCard {
                Text(
                    text = "简介",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (descriptionTranslationState.translatedDescription != null) {
                    Text(
                        text = "原文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SelectionContainer {
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WorkshopOutlinedButton(
                    onClick = onTranslateDescription,
                    enabled = !descriptionTranslationState.isTranslatingDescription,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (descriptionTranslationState.isTranslatingDescription) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(" 正在翻译简介…")
                    } else {
                        Text(
                            if (descriptionTranslationState.translatedDescription == null) {
                                "翻译简介"
                            } else {
                                "重新翻译简介"
                            },
                        )
                    }
                }
                descriptionTranslationState.translationErrorMessage?.let { translationErrorMessage ->
                    WorkshopMessageBanner(
                        message = translationErrorMessage,
                        tone = MessageTone.Error,
                    )
                }
                descriptionTranslationState.translatedDescription?.takeIf(String::isNotBlank)?.let { translatedDescription ->
                    Text(
                        text = "译文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SelectionContainer {
                        Text(
                            text = translatedDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        group.versions.forEach { entry ->
            ModVersionPanel(
                entry = entry,
                updateResult = updateResults[entry.modLibraryKey()],
                onOpenFile = onOpenFile,
                onShareFile = onShareFile,
                onUpdateMod = { onUpdateMod(entry) },
                onRemoveMod = { onRemoveMod(entry) },
            )
        }
    }
}

@Composable
private fun ModVersionPanel(
    entry: DownloadedModEntry,
    updateResult: ModUpdateCheckResult?,
    onOpenFile: (ExportedDownloadFile) -> Unit,
    onShareFile: (ExportedDownloadFile) -> Unit,
    onUpdateMod: () -> Unit,
    onRemoveMod: () -> Unit,
) {
    WorkshopPanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.versionLabel(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MetricFlow(
            metrics = listOf(
                "文件 ${entry.files.size}",
                "同步 ${formatModLibraryTimestamp(entry.storedAtMillis)}",
            ),
        )
        entry.primaryFile()?.let { primaryFile ->
            Text(
                text = "主文件：${primaryFile.relativePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ModUpdateStatusText(result = updateResult)
        if (updateResult?.status == ModUpdateCheckStatus.UpdateAvailable) {
            WorkshopButton(
                onClick = onUpdateMod,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    Text(
                        text = "更新到最新版本",
                        color = Color.Black,
                    )
                }
            }
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (entry.files.isEmpty()) {
                Text(
                    text = "暂无可操作文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entry.files.forEach { file ->
                    FileRow(
                        file = file,
                        sizeText = formatBinaryFileSize(file.sizeBytes),
                        onOpenFile = { onOpenFile(file) },
                        onShareFile = { onShareFile(file) },
                    )
                }
            }
        }

        WorkshopDestructiveButton(
            onClick = onRemoveMod,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("删除这个版本")
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
            WorkshopOutlinedButton(
                onClick = onOpenFile,
                modifier = Modifier.weight(1f),
            ) {
                Text("打开")
            }
            WorkshopOutlinedButton(
                onClick = onShareFile,
                modifier = Modifier.weight(1f),
            ) {
                Text("分享")
            }
        }
    }
}
