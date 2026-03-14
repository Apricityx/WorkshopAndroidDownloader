package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.apricityx.workshop.AppThemeMode
import top.apricityx.workshop.DownloadSettingsRepository
import top.apricityx.workshop.SettingsUiState
import top.apricityx.workshop.SteamLoginDialogMode
import top.apricityx.workshop.SteamLanguagePreference
import top.apricityx.workshop.TranslationProvider
import top.apricityx.workshop.displayName
import top.apricityx.workshop.steam.protocol.SteamGuardChallengeType
import top.apricityx.workshop.update.UpdateSource
import top.apricityx.workshop.ui.component.MessageTone
import top.apricityx.workshop.ui.component.WorkshopMessageBanner
import top.apricityx.workshop.ui.component.WorkshopPanelCard

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOpenSteamLoginDialog: () -> Unit,
    onDismissSteamLoginDialog: () -> Unit,
    onUpdateSteamLoginUsername: (String) -> Unit,
    onUpdateSteamLoginPassword: (String) -> Unit,
    onUpdateSteamGuardCode: (String) -> Unit,
    onSubmitSteamLogin: () -> Unit,
    onSwitchToAnonymousSteamAccount: () -> Unit,
    onSetActiveSteamAccount: (String) -> Unit,
    onReauthenticateSteamAccount: (String) -> Unit,
    onRemoveSteamAccount: (String) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onSteamLanguagePreferenceSelected: (SteamLanguagePreference) -> Unit,
    onTranslationProviderSelected: (TranslationProvider) -> Unit,
    onOpenBaiduTranslationApiKeyScreen: () -> Unit,
    onAutoCheckUpdatesChanged: (Boolean) -> Unit,
    onPreferredUpdateSourceSelected: (UpdateSource) -> Unit,
    onManualCheckUpdates: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onThreadCountChange: (String) -> Unit,
    onConcurrentTaskCountChange: (String) -> Unit,
    onModUpdateConcurrentCheckCountChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkshopPanelCard {
            Text("Steam 账号", style = MaterialTheme.typography.titleLarge)
            Text(
                state.steamAuthState.statusSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onOpenSteamLoginDialog) {
                    Text("添加账号")
                }
                OutlinedButton(onClick = onSwitchToAnonymousSteamAccount) {
                    Text("切回匿名")
                }
            }

            if (state.steamAuthState.accounts.isEmpty()) {
                Text(
                    "当前没有已保存的 Steam 账号。登录后浏览会自动带上 `steamLoginSecure`，下载任务也会在入队时绑定当前账号。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.steamAuthState.accounts.forEach { account ->
                        WorkshopPanelCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(account.accountName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (account.requiresReauthentication) {
                                        "该账号需要重新认证。浏览会回退到匿名，绑定到它的新下载也会被阻止。"
                                    } else if (account.isActive) {
                                        "当前浏览账号"
                                    } else {
                                        "已保存账号"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (!account.isActive) {
                                        OutlinedButton(onClick = { onSetActiveSteamAccount(account.accountId) }) {
                                            Text("设为当前")
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    SteamAccountActionsButton(
                                        onReauthenticate = { onReauthenticateSteamAccount(account.accountId) },
                                        onRemove = { onRemoveSteamAccount(account.accountId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        WorkshopPanelCard {
            Text("翻译设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "描述翻译支持本地模型和百度大模型文本翻译两种方式；切到百度后可单独配置 AppID 和 API Key。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TranslationProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.selectedTranslationProvider == provider,
                                onClick = { onTranslationProviderSelected(provider) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = state.selectedTranslationProvider == provider,
                            onClick = null,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = provider.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = translationProviderDescription(
                                    provider = provider,
                                    baiduApiKeyConfigured = state.baiduTranslationApiKeyConfigured,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.selectedTranslationProvider == TranslationProvider.BaiduGeneralText) {
                OutlinedButton(
                    onClick = onOpenBaiduTranslationApiKeyScreen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.baiduTranslationApiKeyConfigured) {
                            "配置百度大模型文本翻译凭据"
                        } else {
                            "添加百度大模型文本翻译凭据"
                        },
                    )
                }
            }
        }

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
            Text("语言偏好", style = MaterialTheme.typography.titleLarge)
            Text(
                "影响添加游戏时的 Steam 商店搜索，以及浏览模组时的工坊列表语言偏好。默认使用简体中文。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SteamLanguagePreference.entries.forEach { preference ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.selectedSteamLanguagePreference == preference,
                                onClick = { onSteamLanguagePreferenceSelected(preference) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = state.selectedSteamLanguagePreference == preference,
                            onClick = null,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = preference.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = steamLanguagePreferenceDescription(preference),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        WorkshopPanelCard {
            Text("应用更新", style = MaterialTheme.typography.titleLarge)
            Text(
                "参考 SlayTheAmethystModded 的策略，从 GitHub Releases 检查最新发布版，发现新版本后展示更新说明并跳转下载。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("启动时自动检查更新", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "冷启动时后台检查最新 Release；如果没有更新则保持静默。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.autoCheckUpdatesEnabled,
                    onCheckedChange = onAutoCheckUpdatesChanged,
                )
            }

            Text("首选更新源", style = MaterialTheme.typography.titleMedium)
            Text(
                "下载 APK 时优先使用所选源；元数据检查会自动回退到其他镜像，最后使用官方 GitHub 直链。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.availableUpdateSources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.preferredUpdateSource == source,
                                onClick = { onPreferredUpdateSourceSelected(source) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = state.preferredUpdateSource == source,
                            onClick = null,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = source.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = sourceDescription(source),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text("当前版本：${state.currentVersionText}", style = MaterialTheme.typography.bodyMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onManualCheckUpdates,
                    enabled = !state.updateCheckInProgress,
                ) {
                    if (state.updateCheckInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = if (state.updateCheckInProgress) {
                            "正在检查更新…"
                        } else {
                            "立即检查更新"
                        },
                    )
                }
            }

            Text("最近检查结果", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.updateStatusSummary.ifBlank { "尚未执行过更新检查。" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        WorkshopPanelCard {
            Text("下载与检查设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "单任务线程数影响模组分块下载；同时下载任务数影响下载中心里并行跑的任务数量；并发检查数影响模组库检查更新时同时发起的工坊详情请求数量。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.downloadThreadCountInput,
                onValueChange = onThreadCountChange,
                label = { Text("单任务线程数") },
                supportingText = {
                    Text("范围 ${DownloadSettingsRepository.MIN_DOWNLOAD_THREADS} - ${DownloadSettingsRepository.MAX_DOWNLOAD_THREADS}")
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
                    Text("范围 ${DownloadSettingsRepository.MIN_CONCURRENT_DOWNLOAD_TASKS} - ${DownloadSettingsRepository.MAX_CONCURRENT_DOWNLOAD_TASKS}")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.modUpdateConcurrentCheckCountInput,
                onValueChange = onModUpdateConcurrentCheckCountChange,
                label = { Text("模组更新并发检查数") },
                supportingText = {
                    Text("范围 ${DownloadSettingsRepository.MIN_MOD_UPDATE_CONCURRENT_CHECKS} - ${DownloadSettingsRepository.MAX_MOD_UPDATE_CONCURRENT_CHECKS}")
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

        WorkshopPanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于", style = MaterialTheme.typography.titleLarge)
                Text(
                    "开发者",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "apricityx",
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenExternalUrl(primaryDeveloperUrl) },
                )
                Text(
                    text = "ZJustin117",
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenExternalUrl(secondaryDeveloperUrl) },
                )
                Text(
                    "仓库地址：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = repositoryUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenExternalUrl(repositoryUrl) },
                )
                Text(
                    "如果这个项目对你有帮助，欢迎去 GitHub 给项目点个 Star 支持一下，这对我有很大帮助！如果有问题，欢迎提交 issue!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.steamAuthState.loginDialogState?.let { dialogState ->
        SteamLoginDialog(
            state = dialogState,
            onDismiss = onDismissSteamLoginDialog,
            onUsernameChange = onUpdateSteamLoginUsername,
            onPasswordChange = onUpdateSteamLoginPassword,
            onGuardCodeChange = onUpdateSteamGuardCode,
            onSubmit = onSubmitSteamLogin,
        )
    }
}

@Composable
private fun SteamAccountActionsButton(
    onReauthenticate: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("操作")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("重新认证") },
                onClick = {
                    expanded = false
                    onReauthenticate()
                },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun SteamLoginDialog(
    state: top.apricityx.workshop.SteamLoginDialogUiState,
    onDismiss: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onGuardCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (state.mode == SteamLoginDialogMode.Reauthenticate) {
                        "重新认证 Steam"
                    } else {
                        "登录 Steam"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )

                when (state.challengeType) {
                    SteamGuardChallengeType.EmailCode,
                    SteamGuardChallengeType.DeviceCode,
                    -> {
                        Text(
                            state.challengeMessage ?: "请输入 Steam Guard 验证码。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = state.guardCode,
                            onValueChange = onGuardCodeChange,
                            label = { Text("验证码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    SteamGuardChallengeType.DeviceConfirmation,
                    SteamGuardChallengeType.EmailConfirmation,
                    -> {
                        Text(
                            state.challengeMessage ?: "请在 Steam 手机 App 中完成确认，应用会自动继续等待结果。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = onUsernameChange,
                            label = { Text("账号名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = state.mode != SteamLoginDialogMode.Reauthenticate,
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = { Text("密码") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                state.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                    WorkshopMessageBanner(
                        message = message,
                        tone = MessageTone.Error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss, enabled = !state.isSubmitting) {
                        Text("关闭")
                    }
                    Button(onClick = onSubmit, enabled = !state.isSubmitting) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(
                            when (state.challengeType) {
                                SteamGuardChallengeType.EmailCode,
                                SteamGuardChallengeType.DeviceCode,
                                -> "提交验证码"

                                SteamGuardChallengeType.DeviceConfirmation,
                                SteamGuardChallengeType.EmailConfirmation,
                                -> "继续等待"

                                else -> if (state.mode == SteamLoginDialogMode.Reauthenticate) {
                                    "重新认证"
                                } else {
                                    "登录"
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun sourceDescription(source: UpdateSource): String =
    when (source) {
        UpdateSource.GH_PROXY_COM -> "默认优先源，适合下载 GitHub 附件。"
        UpdateSource.GH_PROXY_VIP -> "支持元数据和下载代理，适合作为备用源。"
        UpdateSource.GH_LLKK -> "支持元数据和下载代理，可作为另一条回退线路。"
        UpdateSource.GH_PROXY_NET -> "自动回退源，不在设置里手动选择。"
        UpdateSource.OFFICIAL -> "官方 GitHub 直连地址。"
    }

private fun translationProviderDescription(
    provider: TranslationProvider,
    baiduApiKeyConfigured: Boolean,
): String =
    when (provider) {
        TranslationProvider.OnDevice -> "直接使用设备本地翻译模型，不依赖第三方翻译接口。"
        TranslationProvider.BaiduGeneralText -> if (baiduApiKeyConfigured) {
            "当前已配置 AppID 和 API Key，描述翻译会优先走百度大模型文本翻译。"
        } else {
            "需要先配置 AppID 和 API Key，配置完成后再切换过去更合适。"
        }
    }

private fun steamLanguagePreferenceDescription(
    preference: SteamLanguagePreference,
): String =
    when (preference) {
        SteamLanguagePreference.SimplifiedChinese -> "添加游戏和工坊浏览会优先按中文界面与中文偏好请求。"
        SteamLanguagePreference.English -> "添加游戏和工坊浏览会优先按英文界面与英文偏好请求。"
    }

private const val repositoryUrl = "https://github.com/Apricityx/WorkshopAndroidDownloader"
private const val primaryDeveloperUrl = "https://github.com/Apricityx"
private const val secondaryDeveloperUrl = "https://github.com/ZJustin117"
