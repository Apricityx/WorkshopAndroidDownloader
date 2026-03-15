package top.apricityx.workshop.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.DownloadCenterTaskUiState
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.DownloadedModGroup
import top.apricityx.workshop.ModLibraryDisplayMode
import top.apricityx.workshop.WorkshopScreenDestination
import top.apricityx.workshop.WorkshopUiState
import top.apricityx.workshop.isLibraryRoot
import top.apricityx.workshop.showsDownloadCenterShortcut
import top.apricityx.workshop.showsSettingsShortcut
import top.apricityx.workshop.toggleContentDescription
import top.apricityx.workshop.versionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkshopTopBar(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(state.titleForScreen(selectedTask = selectedTask, selectedMod = selectedMod))
        },
        navigationIcon = {
            if (!state.currentScreen.isLibraryRoot()) {
                IconButton(onClick = actions.onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            }
        },
        actions = {
            if (state.currentScreen.showsDownloadCenterShortcut()) {
                IconButton(onClick = actions.onNavigateToDownloadCenter) {
                    BadgedBox(
                        badge = {
                            if (state.downloadCenterState.activeCount > 0) {
                                Badge {
                                    Text(state.downloadCenterState.activeCount.toString())
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载中心",
                        )
                    }
                }
            }

            if (state.currentScreen == WorkshopScreenDestination.GameLibrary) {
                IconButton(onClick = actions.onNavigateToAddGame) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加游戏",
                    )
                }
            }

            if (state.currentScreen == WorkshopScreenDestination.ModLibrary) {
                val toggleContentDescription = state.modLibraryState.displayMode.toggleContentDescription()
                val toggleIcon = when (state.modLibraryState.displayMode) {
                    ModLibraryDisplayMode.LargePreview -> Icons.AutoMirrored.Filled.ViewList
                    ModLibraryDisplayMode.CompactList -> Icons.Default.Dashboard
                    ModLibraryDisplayMode.Overview -> Icons.Default.ViewModule
                }
                IconButton(
                    onClick = actions.onToggleModLibraryDisplayMode,
                    modifier = Modifier.testTag("modLibraryDisplayModeToggle"),
                ) {
                    Icon(
                        imageVector = toggleIcon,
                        contentDescription = toggleContentDescription,
                    )
                }
            }

            if (state.currentScreen.showsSettingsShortcut()) {
                IconButton(onClick = actions.onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                    )
                }
            }
        },
    )
}

@Composable
internal fun WorkshopLibraryBottomBar(
    state: WorkshopUiState,
    actions: WorkshopScreenActions,
) {
    if (!state.currentScreen.isLibraryRoot()) {
        return
    }

    NavigationBar {
        NavigationBarItem(
            selected = state.currentScreen == WorkshopScreenDestination.GameLibrary,
            onClick = actions.onNavigateToGameLibrary,
            icon = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                )
            },
            label = { Text("游戏库") },
            modifier = Modifier.testTag("gameLibraryTab"),
        )
        NavigationBarItem(
            selected = state.currentScreen == WorkshopScreenDestination.ModLibrary,
            onClick = actions.onNavigateToModLibrary,
            icon = {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                )
            },
            label = { Text("模组库") },
            modifier = Modifier.testTag("modLibraryTab"),
        )
    }
}

@Composable
internal fun WorkshopBody(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
    saveableStateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkshopDialogs(state = state, actions = actions)
        WorkshopScreenContent(
            state = state,
            selectedTask = selectedTask,
            selectedMod = selectedMod,
            actions = actions,
            saveableStateHolder = saveableStateHolder,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WorkshopDialogs(
    state: WorkshopUiState,
    actions: WorkshopScreenActions,
) {
    if (state.showUsageNoticeDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("使用须知") },
            text = {
                Text("欢迎使用创意工坊下载器！如果出现模组无法正常浏览或正常下载的问题，请自备加速器加速 steam 或者使用科学上网。")
            },
            confirmButton = {
                Button(onClick = actions.onDismissUsageNotice) {
                    Text("我知道了")
                }
            },
        )
        return
    }

    val updatePrompt = state.settingsState.updatePromptState
    val pendingRemoveGame = state.pendingRemoveGame
    val pendingRemoveMod = state.pendingRemoveMod

    if (updatePrompt != null) {
        AlertDialog(
            onDismissRequest = actions.onDismissUpdatePrompt,
            title = { Text("发现新版本") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("当前版本：${updatePrompt.currentVersion}")
                    Text("最新版本：${updatePrompt.latestVersion}")
                    Text("发布日期：${updatePrompt.publishedAtText}")
                    Text("下载来源：${updatePrompt.downloadSourceDisplayName}")
                    Text(
                        text = "更新说明",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = updatePrompt.notesText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        actions.onOpenExternalUrl(updatePrompt.downloadUrl)
                        actions.onDismissUpdatePrompt()
                    },
                ) {
                    Text("前往下载")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = actions.onDismissUpdatePrompt) {
                    Text("稍后")
                }
            },
        )
    }

    if (pendingRemoveGame != null && state.currentScreen == WorkshopScreenDestination.GameLibrary) {
        AlertDialog(
            onDismissRequest = actions.onDismissRemoveGame,
            title = { Text("移出游戏库") },
            text = { Text("确定要移除「${pendingRemoveGame.name}」吗？") },
            confirmButton = {
                OutlinedButton(onClick = actions.onConfirmRemoveGame) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = actions.onDismissRemoveGame) {
                    Text("取消")
                }
            },
        )
    }

    if (pendingRemoveMod != null) {
        AlertDialog(
            onDismissRequest = actions.onDismissRemoveMod,
            title = { Text("删除本地模组") },
            text = {
                Text("确定要删除「${pendingRemoveMod.itemTitle}」的 ${pendingRemoveMod.versionLabel()} 本地文件吗？下载历史会保留。")
            },
            confirmButton = {
                OutlinedButton(onClick = actions.onConfirmRemoveMod) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = actions.onDismissRemoveMod) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun WorkshopScreenContent(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
    saveableStateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.currentScreen,
        transitionSpec = {
            val slideIn = slideInHorizontally { width -> width / 6 }
            val slideOut = slideOutHorizontally { width -> -width / 6 }
            (slideIn + fadeIn()).togetherWith(slideOut + fadeOut())
        },
        label = "screen-transition",
        modifier = modifier,
    ) { screen ->
        saveableStateHolder.SaveableStateProvider(key = screen.name) {
            when (screen) {
                WorkshopScreenDestination.GameLibrary -> LibraryScreen(
                    games = state.libraryGames,
                    isLoading = state.isLibraryLoading,
                    message = state.libraryMessage,
                    error = state.libraryError,
                    onRetry = actions.onRetryLibraryLoad,
                    onOpenGame = actions.onOpenGameWorkshop,
                    onRemoveGame = actions.onRequestRemoveGame,
                    modifier = Modifier.fillMaxSize(),
                )

                WorkshopScreenDestination.ModLibrary -> ModLibraryScreen(
                    state = state.modLibraryState,
                    onRetry = actions.onRetryModLibrarySync,
                    onCheckUpdates = actions.onCheckModLibraryUpdates,
                    onToggleFilterPanel = actions.onToggleModLibraryFilterPanel,
                    onSearchQueryChange = actions.onUpdateModLibrarySearchQuery,
                    onGameFilterSelected = actions.onUpdateModLibraryGameFilter,
                    onSortOptionSelected = actions.onUpdateModLibrarySortOption,
                    onClearFilters = actions.onClearModLibraryFilters,
                    onOpenModDetail = actions.onOpenModDetail,
                    onOpenPrimaryFile = actions.onOpenModFile,
                    onSharePrimaryFile = actions.onShareModFile,
                    modifier = Modifier.fillMaxSize(),
                )

                WorkshopScreenDestination.AddGame -> AddGameScreen(
                    state = state.addGameState,
                    onSearchQueryChange = actions.onUpdateAddGameSearchQuery,
                    onSearch = actions.onSearchGames,
                    onDirectAppIdChange = actions.onUpdateDirectAppId,
                    onAddById = actions.onAddGameById,
                    onAddGame = actions.onAddGameToLibrary,
                    onOpenGame = actions.onOpenGameWorkshop,
                    onRetryFeaturedLoad = actions.onRetryFeaturedGames,
                    modifier = Modifier.fillMaxSize(),
                )

                WorkshopScreenDestination.GameWorkshop -> state.gameWorkshopState?.let { workshopState ->
                    GameWorkshopScreen(
                        state = workshopState,
                        onSearchQueryChange = actions.onUpdateWorkshopSearchQuery,
                        onSortOptionSelected = actions.onUpdateWorkshopSort,
                        onTimeWindowSelected = actions.onUpdateWorkshopTimeWindow,
                        onSearch = actions.onSearchCurrentWorkshop,
                        onLoadMore = actions.onLoadMoreWorkshopItems,
                        onOpenItemDetail = actions.onOpenWorkshopItemDetail,
                        onDownloadSingleItem = actions.onDownloadSingleItem,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                WorkshopScreenDestination.WorkshopItemDetail -> state.workshopItemDetailState?.let { detailState ->
                    WorkshopItemDetailScreen(
                        state = detailState,
                        onRetry = actions.onRetryWorkshopItemDetail,
                        onTranslateDescription = actions.onTranslateWorkshopItemDescription,
                        onDownload = actions.onDownloadSingleItem,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                WorkshopScreenDestination.ModDetail -> selectedMod?.let { entry ->
                    ModDetailScreen(
                        group = entry,
                        updateResults = state.modLibraryState.updateCheckState.results,
                        onOpenFile = actions.onOpenModFile,
                        onShareFile = actions.onShareModFile,
                        onUpdateMod = actions.onUpdateMod,
                        onRemoveMod = actions.onRequestRemoveMod,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                WorkshopScreenDestination.DownloadCenter -> DownloadCenterScreen(
                    state = state.downloadCenterState,
                    onClearFinished = actions.onClearFinishedDownloadTasks,
                    onOpenTask = actions.onOpenDownloadTaskDetail,
                    onRemoveTask = actions.onRemoveDownloadTask,
                    modifier = Modifier.fillMaxSize(),
                )

                WorkshopScreenDestination.DownloadTaskDetail -> selectedTask?.let { task ->
                    DownloadTaskDetailScreen(
                        task = task,
                        onPauseTask = { actions.onPauseDownloadTask(task.id) },
                        onResumeTask = { actions.onResumeDownloadTask(task.id) },
                        onRemoveTask = { actions.onRemoveDownloadTask(task.id) },
                        onShareDebugLog = { actions.onShareDownloadTaskDebugLog(task) },
                        onShareRuntimeLog = actions.onShareRuntimeAppLog,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                WorkshopScreenDestination.Settings -> SettingsScreen(
                    state = state.settingsState,
                    onOpenSteamLoginDialog = actions.onOpenSteamLoginDialog,
                    onDismissSteamLoginDialog = actions.onDismissSteamLoginDialog,
                    onUpdateSteamLoginUsername = actions.onUpdateSteamLoginUsername,
                    onUpdateSteamLoginPassword = actions.onUpdateSteamLoginPassword,
                    onUpdateSteamGuardCode = actions.onUpdateSteamGuardCode,
                    onSubmitSteamLogin = actions.onSubmitSteamLogin,
                    onSwitchToAnonymousSteamAccount = actions.onSwitchToAnonymousSteamAccount,
                    onSetActiveSteamAccount = actions.onSetActiveSteamAccount,
                    onReauthenticateSteamAccount = actions.onReauthenticateSteamAccount,
                    onRemoveSteamAccount = actions.onRemoveSteamAccount,
                    onThemeModeSelected = actions.onUpdateThemeMode,
                    onSteamLanguagePreferenceSelected = actions.onUpdateSteamLanguagePreference,
                    onTranslationProviderSelected = actions.onUpdateTranslationProvider,
                    onOpenBaiduTranslationApiKeyScreen = actions.onOpenBaiduTranslationApiKeyScreen,
                    onAutoCheckUpdatesChanged = actions.onUpdateAutoCheckUpdates,
                    onPreferredUpdateSourceSelected = actions.onUpdatePreferredUpdateSource,
                    onManualCheckUpdates = actions.onCheckForUpdatesNow,
                    onOpenExternalUrl = actions.onOpenExternalUrl,
                    onThreadCountChange = actions.onUpdateDownloadThreadCountInput,
                    onConcurrentTaskCountChange = actions.onUpdateConcurrentDownloadTaskCountInput,
                    onModUpdateConcurrentCheckCountChange = actions.onUpdateModUpdateConcurrentCheckCountInput,
                    onSave = actions.onSaveDownloadSettings,
                    modifier = Modifier.fillMaxSize(),
                )

                WorkshopScreenDestination.BaiduTranslationApiKey -> BaiduTranslationApiKeyScreen(
                    state = state.baiduTranslationApiKeyState,
                    onAppIdChange = actions.onUpdateBaiduTranslationAppIdInput,
                    onApiKeyChange = actions.onUpdateBaiduTranslationApiKeyInput,
                    onSave = actions.onSaveBaiduTranslationApiKey,
                    onTestTranslation = actions.onTestBaiduTranslationApiKey,
                    onOpenApiKeyGuide = { actions.onOpenExternalUrl(baiduApiKeyGuideUrl) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun WorkshopUiState.titleForScreen(
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
): String =
    when (currentScreen) {
        WorkshopScreenDestination.GameLibrary -> "游戏库"
        WorkshopScreenDestination.ModLibrary -> "模组库"
        WorkshopScreenDestination.AddGame -> "添加游戏"
        WorkshopScreenDestination.GameWorkshop -> gameWorkshopState?.game?.name ?: "创意工坊"
        WorkshopScreenDestination.WorkshopItemDetail ->
            workshopItemDetailState?.detail?.title
                ?: workshopItemDetailState?.item?.title
                ?: "模组详情"

        WorkshopScreenDestination.ModDetail -> selectedMod?.itemTitle ?: "模组详情"
        WorkshopScreenDestination.DownloadCenter -> "下载中心"
        WorkshopScreenDestination.DownloadTaskDetail -> selectedTask?.itemTitle ?: "任务详情"
        WorkshopScreenDestination.Settings -> "设置"
        WorkshopScreenDestination.BaiduTranslationApiKey -> "百度大模型翻译配置"
    }

private const val baiduApiKeyGuideUrl = "https://fanyi-api.baidu.com/product/13"
