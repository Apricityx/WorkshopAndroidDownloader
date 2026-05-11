package top.apricityx.workshop.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.DownloadCenterTaskUiState
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.DownloadedModGroup
import top.apricityx.workshop.ModLibraryDisplayMode
import top.apricityx.workshop.WorkshopModKey
import top.apricityx.workshop.WorkshopScreenDestination
import top.apricityx.workshop.WorkshopUiState
import top.apricityx.workshop.buildWorkshopModStatusResolver
import top.apricityx.workshop.downloadedPublishedFileIds
import top.apricityx.workshop.isLibraryRoot
import top.apricityx.workshop.matches
import top.apricityx.workshop.showsGameWorkshopMoreShortcut
import top.apricityx.workshop.showsDownloadCenterShortcut
import top.apricityx.workshop.showsSettingsShortcut
import top.apricityx.workshop.toggleContentDescription
import top.apricityx.workshop.versionLabel
import top.apricityx.workshop.workshopChangeNotesUrl
import top.apricityx.workshop.workshopModKey
import top.apricityx.workshop.ui.component.WorkshopChangeNotesDialog
import top.apricityx.workshop.ui.component.WorkshopButton
import top.apricityx.workshop.ui.component.SimpleMarkdownCard
import top.apricityx.workshop.ui.component.WorkshopDialog
import top.apricityx.workshop.ui.component.WorkshopLensBackdropSurface
import top.apricityx.workshop.ui.component.WorkshopOutlinedButton
import top.apricityx.workshop.ui.component.WorkshopOutlinedTextField
import top.apricityx.workshop.ui.component.WorkshopPopupMenu
import top.apricityx.workshop.ui.component.WorkshopPopupMenuItem
import top.apricityx.workshop.ui.component.liquid.LiquidButton
import top.apricityx.workshop.ui.component.liquid.LiquidBottomTab
import top.apricityx.workshop.ui.component.liquid.LiquidBottomTabs
import top.apricityx.workshop.ui.theme.LocalWorkshopBackdrop
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled
import top.apricityx.workshop.ui.theme.shouldReduceLiquidGlassEffects
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

@Composable
internal fun WorkshopTopBar(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
) {
    if (isLiquidGlassFrontendEnabled()) {
        WorkshopLiquidTopBar(
            state = state,
            selectedTask = selectedTask,
            selectedMod = selectedMod,
            actions = actions,
        )
        return
    }

    LegacyWorkshopTopBar(
        state = state,
        selectedTask = selectedTask,
        selectedMod = selectedMod,
        actions = actions,
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

    if (isLiquidGlassFrontendEnabled()) {
        WorkshopLiquidBottomBar(
            state = state,
            actions = actions,
        )
        return
    }

    LegacyWorkshopBottomBar(
        state = state,
        actions = actions,
    )
}

@Composable
internal fun WorkshopBody(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
    pendingDownloadItemKeys: Set<WorkshopModKey>,
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
            pendingDownloadItemKeys = pendingDownloadItemKeys,
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
        WorkshopDialog(
            onDismissRequest = {},
            title = { Text("使用须知") },
            buttons = {
                WorkshopButton(onClick = actions.onDismissUsageNotice) {
                    Text("我知道了")
                }
            },
        ) {
            Text("欢迎使用创意工坊下载器！如果出现模组无法正常浏览或正常下载的问题，请自备加速器加速 steam 或者使用科学上网。")
        }
        return
    }

    val steamDirectAccessFallbackDialogState = state.steamDirectAccessFallbackDialogState
    if (steamDirectAccessFallbackDialogState != null) {
        WorkshopDialog(
            onDismissRequest = actions.onDismissSteamDirectAccessFallbackDialog,
            title = { Text("Steam 加速链路不可用") },
            buttons = {
                WorkshopButton(onClick = actions.onDismissSteamDirectAccessFallbackDialog) {
                    Text("我知道了")
                }
            },
        ) {
            Text(steamDirectAccessFallbackDialogState.message)
        }
        return
    }

    val updatePrompt = state.settingsState.updatePromptState
    val modLibraryChangeNotesDialogState = state.modLibraryState.changeNotesDialogState
    val pendingRemoveGame = state.pendingRemoveGame
    val pendingRemoveMod = state.pendingRemoveMod
    val pendingRenameMod = state.pendingRenameMod

    if (modLibraryChangeNotesDialogState != null) {
        WorkshopChangeNotesDialog(
            title = modLibraryChangeNotesDialogState.group.itemTitle,
            markdown = modLibraryChangeNotesDialogState.markdown,
            isLoading = modLibraryChangeNotesDialogState.isLoading,
            errorMessage = modLibraryChangeNotesDialogState.errorMessage,
            onRetryRequest = {
                actions.onOpenModLibraryChangeNotes(modLibraryChangeNotesDialogState.group)
            },
            onDismissRequest = actions.onDismissModLibraryChangeNotes,
            onOpenExternalUrl = {
                actions.onOpenExternalUrl(
                    workshopChangeNotesUrl(modLibraryChangeNotesDialogState.group.publishedFileId),
                )
            },
        )
    }

    if (updatePrompt != null) {
        var showDownloadChoiceDialog by remember(updatePrompt) {
            mutableStateOf(false)
        }
        var downloadMenuExpanded by remember(updatePrompt) {
            mutableStateOf(false)
        }
        var selectedDownloadSourceId by remember(updatePrompt) {
            mutableStateOf(updatePrompt.defaultDownloadSourceId)
        }
        val selectedDownloadOption = updatePrompt.downloadOptions.firstOrNull {
            it.source.id == selectedDownloadSourceId
        } ?: updatePrompt.downloadOptions.firstOrNull()
        if (showDownloadChoiceDialog) {
            WorkshopDialog(
                onDismissRequest = {
                    downloadMenuExpanded = false
                    showDownloadChoiceDialog = false
                },
                title = { Text("选择下载方式") },
                buttons = {
                    WorkshopOutlinedButton(
                        onClick = {
                            downloadMenuExpanded = false
                            showDownloadChoiceDialog = false
                            actions.onOpenExternalUrl(updateQuarkDownloadUrl)
                            actions.onDismissUpdatePrompt()
                        },
                    ) {
                        Text("夸克下载")
                    }
                    WorkshopButton(
                        onClick = {
                            val targetUrl = selectedDownloadOption?.url ?: return@WorkshopButton
                            downloadMenuExpanded = false
                            showDownloadChoiceDialog = false
                            actions.onOpenExternalUrl(targetUrl)
                            actions.onDismissUpdatePrompt()
                        },
                    ) {
                        Text("直链下载")
                    }
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "直链下载将从 GitHub Release 进行下载，不一定会有稳定的速度。如果想要支持开发，可以使用夸克下载。开发者会从每一次转存获得收益😋",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "下载源",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        WorkshopOutlinedButton(
                            onClick = { downloadMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(selectedDownloadOption?.label.orEmpty())
                                Text(if (downloadMenuExpanded) "▲" else "▼")
                            }
                        }
                        WorkshopPopupMenu(
                            expanded = downloadMenuExpanded,
                            onDismissRequest = { downloadMenuExpanded = false },
                        ) {
                            updatePrompt.downloadOptions.forEach { option ->
                                WorkshopPopupMenuItem(
                                    text = { Text(option.label) },
                                    reserveLeadingSpace = true,
                                    leadingIcon = {
                                        if (option.source.id == selectedDownloadSourceId) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedDownloadSourceId = option.source.id
                                        downloadMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } else {
        WorkshopDialog(
            onDismissRequest = actions.onDismissUpdatePrompt,
            title = { Text("发现新版本") },
            buttons = {
                WorkshopOutlinedButton(onClick = actions.onDismissUpdatePrompt) {
                    Text("稍后")
                }
                WorkshopButton(
                    onClick = { showDownloadChoiceDialog = true },
                ) {
                    Text("前往下载")
                }
            },
        ) {
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
                SimpleMarkdownCard(
                    title = "更新说明",
                    markdown = updatePrompt.notesText,
                )
            }
        }
        }
    }

    if (pendingRenameMod != null) {
        val trimmedRenameTitle = state.renameModTitleInput.trim()
        WorkshopDialog(
            onDismissRequest = actions.onDismissRenameMod,
            title = { Text("重命名模组") },
            buttons = {
                WorkshopOutlinedButton(onClick = actions.onDismissRenameMod) {
                    Text("取消")
                }
                WorkshopButton(
                    onClick = actions.onConfirmRenameMod,
                    enabled = trimmedRenameTitle.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("只修改应用内显示名称，不会重命名已经导出的文件。")
                WorkshopOutlinedTextField(
                    value = state.renameModTitleInput,
                    onValueChange = actions.onUpdateRenameModTitleInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模组名称") },
                    singleLine = true,
                )
            }
        }
    }

    if (pendingRemoveGame != null && state.currentScreen == WorkshopScreenDestination.GameLibrary) {
        WorkshopDialog(
            onDismissRequest = actions.onDismissRemoveGame,
            title = { Text("移出游戏库") },
            buttons = {
                WorkshopOutlinedButton(onClick = actions.onDismissRemoveGame) {
                    Text("取消")
                }
                WorkshopOutlinedButton(onClick = actions.onConfirmRemoveGame) {
                    Text("确定")
                }
            },
        ) {
            Text("确定要移除「${pendingRemoveGame.name}」吗？")
        }
    }

    if (pendingRemoveMod != null) {
        WorkshopDialog(
            onDismissRequest = actions.onDismissRemoveMod,
            title = {
                Text(
                    if (pendingRemoveMod.isTrackingOnly) {
                        "从模组库移除"
                    } else {
                        "删除本地模组"
                    },
                )
            },
            buttons = {
                WorkshopOutlinedButton(onClick = actions.onDismissRemoveMod) {
                    Text("取消")
                }
                WorkshopOutlinedButton(onClick = actions.onConfirmRemoveMod) {
                    Text("确定")
                }
            },
        ) {
            Text(
                if (pendingRemoveMod.isTrackingOnly) {
                    "确定要从模组库移除「${pendingRemoveMod.itemTitle}」吗？移除后将不再继续为它检查更新。"
                } else {
                    "确定要删除「${pendingRemoveMod.itemTitle}」的 ${pendingRemoveMod.versionLabel()} 本地文件吗？下载历史会保留。"
                },
            )
        }
    }
}

@Composable
private fun WorkshopScreenContent(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
    pendingDownloadItemKeys: Set<WorkshopModKey>,
    saveableStateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val compactTransition = state.currentScreen.prefersImmediateScreenSwap()

    androidx.compose.runtime.key(state.currentScreen) {
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            entered = true
        }
        val alpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = if (compactTransition) 140 else 180),
            label = "screenEnterAlpha",
        )
        val translateY by animateDpAsState(
            targetValue = if (entered) 0.dp else if (compactTransition) 10.dp else 18.dp,
            animationSpec = tween(durationMillis = if (compactTransition) 180 else 220),
            label = "screenEnterTranslateY",
        )
        WorkshopScreenScene(
            screen = state.currentScreen,
            state = state,
            selectedTask = selectedTask,
            selectedMod = selectedMod,
            actions = actions,
            pendingDownloadItemKeys = pendingDownloadItemKeys,
            saveableStateHolder = saveableStateHolder,
            modifier = modifier.graphicsLayer {
                this.alpha = alpha
                translationY = with(density) { translateY.toPx() }
            },
        )
    }
}

private fun WorkshopScreenDestination.prefersImmediateScreenSwap(): Boolean =
    when (this) {
        WorkshopScreenDestination.GameLibrary,
        WorkshopScreenDestination.ModLibrary,
        WorkshopScreenDestination.AddGame,
        WorkshopScreenDestination.GameWorkshop,
        WorkshopScreenDestination.DownloadCenter,
        -> true

        WorkshopScreenDestination.WorkshopItemDetail,
        WorkshopScreenDestination.ModDetail,
        WorkshopScreenDestination.DownloadTaskDetail,
        WorkshopScreenDestination.Settings,
        WorkshopScreenDestination.BaiduTranslationApiKey,
        -> false
    }

@Composable
private fun WorkshopScreenScene(
    screen: WorkshopScreenDestination,
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
    pendingDownloadItemKeys: Set<WorkshopModKey>,
    saveableStateHolder: SaveableStateHolder,
    modifier: Modifier = Modifier,
) {
    val modStatusResolver = remember(
        state.modLibraryState.items,
        state.modLibraryState.updateCheckState.results,
        pendingDownloadItemKeys,
        state.downloadCenterState.activeTasks,
    ) {
        buildWorkshopModStatusResolver(
            downloadedGroups = state.modLibraryState.items,
            updateResults = state.modLibraryState.updateCheckState.results,
            pendingDownloadItemKeys = pendingDownloadItemKeys,
            activeDownloadItemKeys = state.downloadCenterState.activeTasks
                .map(DownloadCenterTaskUiState::workshopModKey)
                .toSet(),
        )
    }
    Box(modifier = modifier) {
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
                    onViewChangeNotes = actions.onOpenModLibraryChangeNotes,
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
                        modStatusResolver = modStatusResolver,
                        isBrowsingUnauthenticated = state.settingsState.steamAuthState.isBrowsingUnauthenticated,
                        onSearchQueryChange = actions.onUpdateWorkshopSearchQuery,
                        onSortOptionSelected = actions.onUpdateWorkshopSort,
                        onTimeWindowSelected = actions.onUpdateWorkshopTimeWindow,
                        onSearch = actions.onSearchCurrentWorkshop,
                        onLoadMore = actions.onLoadMoreWorkshopItems,
                        onOpenItemDetail = actions.onOpenWorkshopItemDetail,
                        onDownloadSingleItem = actions.onDownloadSingleItem,
                        onDismissDirectDownloadDialog = actions.onDismissGameWorkshopDirectDownloadDialog,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                WorkshopScreenDestination.WorkshopItemDetail -> state.workshopItemDetailState?.let { detailState ->
                    val downloadedItemIds = state.modLibraryState.items
                        .downloadedPublishedFileIds(detailState.item.appId)
                    WorkshopItemDetailScreen(
                        state = detailState,
                        downloadedItemIds = downloadedItemIds,
                        isInModLibrary = state.modLibraryState.items.any { group ->
                            group.matches(
                                appId = detailState.item.appId,
                                publishedFileId = detailState.item.publishedFileId,
                            )
                        },
                        modStatus = modStatusResolver.resolve(detailState.item),
                        onRetry = actions.onRetryWorkshopItemDetail,
                        onRetryComments = actions.onRetryWorkshopCommentsPage,
                        onLoadPreviousCommentsPage = actions.onLoadPreviousWorkshopCommentsPage,
                        onLoadNextCommentsPage = actions.onLoadNextWorkshopCommentsPage,
                        onTranslateDescription = actions.onTranslateWorkshopItemDescription,
                        onAddToLibrary = { actions.onAddWorkshopItemToModLibrary(detailState.item) },
                        onDownload = actions.onDownloadSingleItem,
                        onViewDownloadedMod = actions.onOpenDownloadedWorkshopItem,
                        onOpenRequiredItem = actions.onOpenWorkshopItemDetail,
                        onOpenExternalUrl = actions.onOpenExternalUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                WorkshopScreenDestination.ModDetail -> selectedMod?.let { entry ->
                    ModDetailScreen(
                        group = entry,
                        descriptionTranslationState = state.modLibraryState.detailDescriptionTranslation,
                        updateResults = state.modLibraryState.updateCheckState.results,
                        onTranslateDescription = actions.onTranslateModLibraryDescription,
                        onRenameMod = { actions.onRequestRenameMod(entry) },
                        onOpenFile = actions.onOpenModFile,
                        onShareFile = actions.onShareModFile,
                        onUpdateMod = actions.onUpdateMod,
                        onRemoveMod = actions.onRequestRemoveMod,
                        onViewChangeNotes = actions.onOpenModLibraryChangeNotes,
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
                    onUpdateSteamLoginRefreshToken = actions.onUpdateSteamLoginRefreshToken,
                    onUpdateSteamGuardCode = actions.onUpdateSteamGuardCode,
                    onSwitchSteamLoginInputMode = actions.onSwitchSteamLoginInputMode,
                    onSubmitSteamLogin = actions.onSubmitSteamLogin,
                    onOpenRuntimeLog = actions.onOpenRuntimeLog,
                    onShareRuntimeLogBundle = actions.onShareRuntimeLogBundle,
                    onExportRuntimeLogBundle = actions.onExportRuntimeLogBundle,
                    onSwitchToAnonymousSteamAccount = actions.onSwitchToAnonymousSteamAccount,
                    onSetActiveSteamAccount = actions.onSetActiveSteamAccount,
                    onReauthenticateSteamAccount = actions.onReauthenticateSteamAccount,
                    onRemoveSteamAccount = actions.onRemoveSteamAccount,
                    onFrontendModeSelected = actions.onUpdateFrontendMode,
                    onThemeModeSelected = actions.onUpdateThemeMode,
                    onSteamLanguagePreferenceSelected = actions.onUpdateSteamLanguagePreference,
                    onOpenBaiduTranslationApiKeyScreen = actions.onOpenBaiduTranslationApiKeyScreen,
                    onAutoCheckUpdatesChanged = actions.onUpdateAutoCheckUpdates,
                    onPreferredUpdateSourceSelected = actions.onUpdatePreferredUpdateSource,
                    onManualCheckUpdates = actions.onCheckForUpdatesNow,
                    onOpenExternalUrl = actions.onOpenExternalUrl,
                    onThreadCountChange = actions.onUpdateDownloadThreadCountInput,
                    onConcurrentTaskCountChange = actions.onUpdateConcurrentDownloadTaskCountInput,
                    onModUpdateConcurrentCheckCountChange = actions.onUpdateModUpdateConcurrentCheckCountInput,
                    onAllowSteamAuthenticatedCleartextHttpChanged = actions.onUpdateAllowSteamAuthenticatedCleartextHttp,
                    onExperimentalWorkshopDirectAccessChanged = actions.onUpdateExperimentalWorkshopDirectAccess,
                    onAutoRenameModFilesToModNameChanged = actions.onUpdateAutoRenameModFilesToModName,
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
private const val updateQuarkDownloadUrl = "https://pan.quark.cn/s/2ffa884df03f"

@Composable
private fun LegacyBlurBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalWorkshopBackdrop.current
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)

    if (backdrop == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = containerColor,
            tonalElevation = 0.dp,
            content = content,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(0.dp) },
                effects = {
                    blur(24.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(containerColor)
                },
            ),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyWorkshopTopBar(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
) {
    LegacyBlurBar {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
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

                if (state.currentScreen.showsGameWorkshopMoreShortcut()) {
                    LegacyGameWorkshopMoreMenuButton(
                        expanded = state.gameWorkshopState?.isMoreActionsExpanded == true,
                        actions = actions,
                    )
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
}

@Composable
private fun WorkshopLiquidTopBar(
    state: WorkshopUiState,
    selectedTask: DownloadCenterTaskUiState?,
    selectedMod: DownloadedModGroup?,
    actions: WorkshopScreenActions,
) {
    val backdrop = LocalWorkshopBackdrop.current ?: run {
        LegacyWorkshopTopBar(
            state = state,
            selectedTask = selectedTask,
            selectedMod = selectedMod,
            actions = actions,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.currentScreen.isLibraryRoot()) {
                WorkshopLiquidTopBarActionButton(
                    onClick = actions.onNavigateBack,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }

            WorkshopLiquidTopBarCapsule(
                modifier = Modifier.weight(1f),
                height = 52.dp,
                horizontalPadding = 18.dp,
                fillWidth = true,
            ) {
                Text(
                    text = state.titleForScreen(selectedTask = selectedTask, selectedMod = selectedMod),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.currentScreen.showsDownloadCenterShortcut()) {
                    WorkshopLiquidTopBarActionButton(
                        onClick = actions.onNavigateToDownloadCenter,
                        imageVector = Icons.Default.Download,
                        contentDescription = "下载中心",
                        badgeCount = state.downloadCenterState.activeCount,
                    )
                }

                if (state.currentScreen == WorkshopScreenDestination.GameLibrary) {
                    WorkshopLiquidTopBarActionButton(
                        onClick = actions.onNavigateToAddGame,
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加游戏",
                    )
                }

                if (state.currentScreen == WorkshopScreenDestination.ModLibrary) {
                    val toggleContentDescription = state.modLibraryState.displayMode.toggleContentDescription()
                    val toggleIcon = when (state.modLibraryState.displayMode) {
                        ModLibraryDisplayMode.LargePreview -> Icons.AutoMirrored.Filled.ViewList
                        ModLibraryDisplayMode.CompactList -> Icons.Default.Dashboard
                        ModLibraryDisplayMode.Overview -> Icons.Default.ViewModule
                    }
                    WorkshopLiquidTopBarActionButton(
                        onClick = actions.onToggleModLibraryDisplayMode,
                        imageVector = toggleIcon,
                        contentDescription = toggleContentDescription,
                        modifier = Modifier.testTag("modLibraryDisplayModeToggle"),
                    )
                }

                if (state.currentScreen.showsGameWorkshopMoreShortcut()) {
                    LiquidGameWorkshopMoreMenuButton(
                        expanded = state.gameWorkshopState?.isMoreActionsExpanded == true,
                        actions = actions,
                    )
                }

                if (state.currentScreen.showsSettingsShortcut()) {
                    WorkshopLiquidTopBarActionButton(
                        onClick = actions.onNavigateToSettings,
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyGameWorkshopMoreMenuButton(
    expanded: Boolean,
    actions: WorkshopScreenActions,
) {
    Box {
        IconButton(onClick = actions.onToggleGameWorkshopMoreActions) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多",
            )
        }
        GameWorkshopMoreDropdownMenu(
            expanded = expanded,
            actions = actions,
        )
    }
}

@Composable
private fun LiquidGameWorkshopMoreMenuButton(
    expanded: Boolean,
    actions: WorkshopScreenActions,
) {
    Box {
        WorkshopLiquidTopBarActionButton(
            onClick = actions.onToggleGameWorkshopMoreActions,
            imageVector = Icons.Default.MoreVert,
            contentDescription = "更多",
        )
        GameWorkshopMoreDropdownMenu(
            expanded = expanded,
            actions = actions,
        )
    }
}

@Composable
private fun WorkshopLiquidTopBarCapsule(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    fillWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalWorkshopBackdrop.current
    val shape = Capsule()
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    if (backdrop != null && !shouldReduceLiquidGlassEffects()) {
        Row(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(12.dp.toPx(), 24.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(surfaceColor)
                    },
                )
                .border(width = 1.dp, color = borderColor, shape = shape)
                .clip(shape)
                .then(
                    if (fillWidth) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = height)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }

    WorkshopLensBackdropSurface(
        modifier = modifier,
        shape = shape,
        lensHeight = 8.dp,
        lensAmount = 16.dp,
        surfaceColor = surfaceColor,
        borderColor = borderColor,
    ) {
        Row(
            modifier = (if (fillWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
            })
                .heightIn(min = height)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun BoxScope.GameWorkshopMoreDropdownMenu(
    expanded: Boolean,
    actions: WorkshopScreenActions,
) {
    WorkshopPopupMenu(
        expanded = expanded,
        onDismissRequest = actions.onDismissGameWorkshopMoreActions,
    ) {
        WorkshopPopupMenuItem(
            text = { Text("刷新列表") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                )
            },
            onClick = {
                actions.onDismissGameWorkshopMoreActions()
                actions.onSearchCurrentWorkshop()
            },
        )
        WorkshopPopupMenuItem(
            text = { Text("ID 下载") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                )
            },
            onClick = {
                actions.onOpenGameWorkshopDirectDownloadDialog()
            },
        )
        WorkshopPopupMenuItem(
            text = { Text("设置") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                )
            },
            onClick = {
                actions.onDismissGameWorkshopMoreActions()
                actions.onNavigateToSettings()
            },
        )
    }
}

@Composable
private fun LegacyWorkshopBottomBar(
    state: WorkshopUiState,
    actions: WorkshopScreenActions,
) {
    LegacyBlurBar {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
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
}

@Composable
private fun WorkshopLiquidBottomBar(
    state: WorkshopUiState,
    actions: WorkshopScreenActions,
) {
    val backdrop = LocalWorkshopBackdrop.current ?: run {
        LegacyWorkshopBottomBar(state = state, actions = actions)
        return
    }
    val selectedIndex = when (state.currentScreen) {
        WorkshopScreenDestination.GameLibrary -> 0
        WorkshopScreenDestination.ModLibrary -> 1
        else -> 0
    }
    var requestedIndex by remember {
        mutableIntStateOf(selectedIndex)
    }

    LaunchedEffect(selectedIndex) {
        requestedIndex = selectedIndex
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        LiquidBottomTabs(
            selectedTabIndex = { requestedIndex },
            onTabSelected = { index ->
                when (index) {
                    0 -> if (state.currentScreen != WorkshopScreenDestination.GameLibrary) {
                        actions.onNavigateToGameLibrary()
                    }

                    1 -> if (state.currentScreen != WorkshopScreenDestination.ModLibrary) {
                        actions.onNavigateToModLibrary()
                    }
                }
            },
            backdrop = backdrop,
            tabsCount = 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LiquidBottomTab(
                selected = state.currentScreen == WorkshopScreenDestination.GameLibrary,
                onClick = {
                    if (requestedIndex != 0) {
                        requestedIndex = 0
                    }
                },
                modifier = Modifier.testTag("gameLibraryTab"),
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "游戏库",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            LiquidBottomTab(
                selected = state.currentScreen == WorkshopScreenDestination.ModLibrary,
                onClick = {
                    if (requestedIndex != 1) {
                        requestedIndex = 1
                    }
                },
                modifier = Modifier.testTag("modLibraryTab"),
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "模组库",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun WorkshopLiquidTopBarActionButton(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val backdrop = LocalWorkshopBackdrop.current
    if (backdrop == null || shouldReduceLiquidGlassEffects()) {
        WorkshopLiquidTopBarCapsule(
            modifier = modifier,
            onClick = onClick,
            horizontalPadding = if (badgeCount > 0) 14.dp else 12.dp,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            if (badgeCount > 0) {
                Text(
                    text = badgeCount.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        return
    }

    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
        height = 48.dp,
        horizontalPadding = if (badgeCount > 0) 14.dp else 12.dp,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        if (badgeCount > 0) {
            Text(
                text = badgeCount.coerceAtMost(99).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
