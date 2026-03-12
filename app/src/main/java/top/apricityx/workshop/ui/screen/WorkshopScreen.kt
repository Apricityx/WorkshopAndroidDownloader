package top.apricityx.workshop.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Settings
import top.apricityx.workshop.AppThemeMode
import top.apricityx.workshop.WorkshopScreenDestination
import top.apricityx.workshop.WorkshopUiState
import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.data.WorkshopBrowseItem

data class WorkshopScreenActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToAddGame: () -> Unit,
    val onNavigateToDownloadCenter: () -> Unit,
    val onClearFinishedDownloadTasks: () -> Unit,
    val onOpenDownloadTaskDetail: (String) -> Unit,
    val onPauseDownloadTask: (String) -> Unit,
    val onResumeDownloadTask: (String) -> Unit,
    val onRemoveDownloadTask: (String) -> Unit,
    val onOpenDownloadFile: (String, String) -> Unit,
    val onRetryLibraryLoad: () -> Unit,
    val onRequestRemoveGame: (SteamGame) -> Unit,
    val onConfirmRemoveGame: () -> Unit,
    val onDismissRemoveGame: () -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onUpdateThemeMode: (AppThemeMode) -> Unit,
    val onUpdateDownloadThreadCountInput: (String) -> Unit,
    val onUpdateConcurrentDownloadTaskCountInput: (String) -> Unit,
    val onSaveDownloadSettings: () -> Unit,
    val onUpdateAddGameSearchQuery: (String) -> Unit,
    val onSearchGames: () -> Unit,
    val onUpdateDirectAppId: (String) -> Unit,
    val onAddGameById: () -> Unit,
    val onAddGameToLibrary: (SteamGame) -> Unit,
    val onOpenGameWorkshop: (SteamGame) -> Unit,
    val onUpdateWorkshopSearchQuery: (String) -> Unit,
    val onSearchCurrentWorkshop: () -> Unit,
    val onLoadMoreWorkshopItems: () -> Unit,
    val onOpenWorkshopItemDetail: (WorkshopBrowseItem) -> Unit,
    val onRetryWorkshopItemDetail: () -> Unit,
    val onDownloadSingleItem: (WorkshopBrowseItem) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopScreen(
    state: WorkshopUiState,
    actions: WorkshopScreenActions,
) {
    val selectedTask = state.downloadCenterState.tasks.firstOrNull { it.id == state.selectedDownloadTaskId }
    val saveableStateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = state.currentScreen != WorkshopScreenDestination.Library) {
        actions.onNavigateBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (state.currentScreen) {
                            WorkshopScreenDestination.Library -> "游戏库"
                            WorkshopScreenDestination.AddGame -> "添加游戏"
                            WorkshopScreenDestination.GameWorkshop -> state.gameWorkshopState?.game?.name ?: "创意工坊"
                            WorkshopScreenDestination.WorkshopItemDetail ->
                                state.workshopItemDetailState?.detail?.title
                                    ?: state.workshopItemDetailState?.item?.title
                                    ?: "模组详情"
                            WorkshopScreenDestination.DownloadCenter -> "下载中心"
                            WorkshopScreenDestination.DownloadTaskDetail -> selectedTask?.itemTitle ?: "任务详情"
                            WorkshopScreenDestination.Settings -> "设置"
                        },
                    )
                },
                navigationIcon = {
                    if (state.currentScreen != WorkshopScreenDestination.Library) {
                        IconButton(onClick = actions.onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    if (state.currentScreen != WorkshopScreenDestination.DownloadCenter &&
                        state.currentScreen != WorkshopScreenDestination.DownloadTaskDetail
                    ) {
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

                    if (state.currentScreen == WorkshopScreenDestination.Library) {
                        IconButton(onClick = actions.onNavigateToAddGame) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加游戏",
                            )
                        }
                    }

                    if (state.currentScreen != WorkshopScreenDestination.Settings) {
                        IconButton(onClick = actions.onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val pendingRemove = state.pendingRemoveGame
                if (pendingRemove != null && state.currentScreen == WorkshopScreenDestination.Library) {
                    AlertDialog(
                        onDismissRequest = actions.onDismissRemoveGame,
                        title = { Text("移出游戏库") },
                        text = { Text("确定要移除「${pendingRemove.name}」吗？") },
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

                AnimatedContent(
                    targetState = state.currentScreen,
                    transitionSpec = {
                        val slideIn = slideInHorizontally { width -> width / 6 }
                        val slideOut = slideOutHorizontally { width -> -width / 6 }
                        (slideIn + fadeIn()).togetherWith(slideOut + fadeOut())
                    },
                    label = "screen-transition",
                ) { screen ->
                    saveableStateHolder.SaveableStateProvider(key = screen.name) {
                        when (screen) {
                            WorkshopScreenDestination.Library -> LibraryScreen(
                                games = state.libraryGames,
                                isLoading = state.isLibraryLoading,
                                message = state.libraryMessage,
                                error = state.libraryError,
                                onRetry = actions.onRetryLibraryLoad,
                                onOpenGame = actions.onOpenGameWorkshop,
                                onRemoveGame = actions.onRequestRemoveGame,
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
                                modifier = Modifier.fillMaxSize(),
                            )

                            WorkshopScreenDestination.GameWorkshop -> state.gameWorkshopState?.let { workshopState ->
                                GameWorkshopScreen(
                                    state = workshopState,
                                    onSearchQueryChange = actions.onUpdateWorkshopSearchQuery,
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
                                    onDownload = actions.onDownloadSingleItem,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            WorkshopScreenDestination.DownloadCenter -> DownloadCenterScreen(
                                state = state.downloadCenterState,
                                onClearFinished = actions.onClearFinishedDownloadTasks,
                                onOpenTask = actions.onOpenDownloadTaskDetail,
                                onRemoveTask = actions.onRemoveDownloadTask,
                                onOpenFile = actions.onOpenDownloadFile,
                                modifier = Modifier.fillMaxSize(),
                            )

                            WorkshopScreenDestination.DownloadTaskDetail -> selectedTask?.let { task ->
                                DownloadTaskDetailScreen(
                                    task = task,
                                    onPauseTask = { actions.onPauseDownloadTask(task.id) },
                                    onResumeTask = { actions.onResumeDownloadTask(task.id) },
                                    onRemoveTask = { actions.onRemoveDownloadTask(task.id) },
                                    onOpenFile = { contentUri -> actions.onOpenDownloadFile(task.id, contentUri) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            WorkshopScreenDestination.Settings -> SettingsScreen(
                                state = state.settingsState,
                                onThemeModeSelected = actions.onUpdateThemeMode,
                                onThreadCountChange = actions.onUpdateDownloadThreadCountInput,
                                onConcurrentTaskCountChange = actions.onUpdateConcurrentDownloadTaskCountInput,
                                onSave = actions.onSaveDownloadSettings,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
