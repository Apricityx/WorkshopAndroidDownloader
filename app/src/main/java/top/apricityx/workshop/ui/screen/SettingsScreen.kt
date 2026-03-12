package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.AppThemeMode
import top.apricityx.workshop.DownloadSettingsRepository
import top.apricityx.workshop.SettingsUiState
import top.apricityx.workshop.displayName
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.ScreenSummaryCard
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onThreadCountChange: (String) -> Unit,
    onConcurrentTaskCountChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkshopPanelCard {
            Text("外观设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "支持亮色、深色和跟随系统主题，切换后会立即生效。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.selectedThemeMode == mode,
                                onClick = { onThemeModeSelected(mode) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = state.selectedThemeMode == mode,
                            onClick = null,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = mode.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = when (mode) {
                                    AppThemeMode.FollowSystem -> "根据系统当前外观自动切换。"
                                    AppThemeMode.Light -> "始终使用亮色界面。"
                                    AppThemeMode.Dark -> "始终使用深色界面。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        WorkshopPanelCard {
            Text("下载设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "单任务线程数影响模组分块下载；同时下载任务数影响下载中心里并行跑的任务数量。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.downloadThreadCountInput,
                onValueChange = onThreadCountChange,
                label = { Text("单任务线程数") },
                supportingText = {
                    Text(
                        "范围 ${DownloadSettingsRepository.MIN_DOWNLOAD_THREADS} - ${DownloadSettingsRepository.MAX_DOWNLOAD_THREADS}",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.concurrentDownloadTaskCountInput,
                onValueChange = onConcurrentTaskCountChange,
                label = { Text("同时下载任务数") },
                supportingText = {
                    Text(
                        "范围 ${DownloadSettingsRepository.MIN_CONCURRENT_DOWNLOAD_TASKS} - ${DownloadSettingsRepository.MAX_CONCURRENT_DOWNLOAD_TASKS}",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onSave) {
                    Text("保存")
                }
            }
        }

        state.message?.let {
            WorkshopMessageBanner(
                message = it,
                tone = MessageTone.Success,
            )
        }
    }
}
