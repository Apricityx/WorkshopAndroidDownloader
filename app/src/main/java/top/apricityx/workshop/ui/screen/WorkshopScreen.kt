package top.apricityx.workshop.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.apricityx.workshop.AppFrontendMode
import top.apricityx.workshop.AppThemeMode
import top.apricityx.workshop.DownloadCenterTaskUiState
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.DownloadedModGroup
import top.apricityx.workshop.ExportedDownloadFile
import top.apricityx.workshop.SteamLanguagePreference
import top.apricityx.workshop.SteamLoginInputMode
import top.apricityx.workshop.WorkshopModKey
import top.apricityx.workshop.WorkshopBrowseSortOption
import top.apricityx.workshop.WorkshopBrowseTimeWindow
import top.apricityx.workshop.WorkshopUiState
import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.isLibraryRoot
import top.apricityx.workshop.update.UpdateSource
import top.apricityx.workshop.ui.component.WorkshopLiquidGlassWallpaper
import top.apricityx.workshop.ui.theme.LocalWorkshopBackdrop
import top.apricityx.workshop.ui.theme.LocalWorkshopChromePadding
import top.apricityx.workshop.ui.theme.WorkshopChromePadding
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled

data class WorkshopScreenActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToGameLibrary: () -> Unit,
    val onNavigateToModLibrary: () -> Unit,
    val onNavigateToAddGame: () -> Unit,
    val onNavigateToDownloadCenter: () -> Unit,
    val onClearFinishedDownloadTasks: () -> Unit,
    val onOpenDownloadTaskDetail: (String) -> Unit,
    val onPauseDownloadTask: (String) -> Unit,
    val onResumeDownloadTask: (String) -> Unit,
    val onRemoveDownloadTask: (String) -> Unit,
    val onShareDownloadTaskDebugLog: (DownloadCenterTaskUiState) -> Unit,
    val onShareRuntimeAppLog: () -> Unit,
    val onRetryLibraryLoad: () -> Unit,
    val onRetryModLibrarySync: () -> Unit,
    val onCheckModLibraryUpdates: () -> Unit,
    val onToggleModLibraryDisplayMode: () -> Unit,
    val onToggleModLibraryFilterPanel: () -> Unit,
    val onUpdateModLibrarySearchQuery: (String) -> Unit,
    val onUpdateModLibraryGameFilter: (String?) -> Unit,
    val onUpdateModLibrarySortOption: (top.apricityx.workshop.ModLibrarySortOption) -> Unit,
    val onClearModLibraryFilters: () -> Unit,
    val onDismissUsageNotice: () -> Unit,
    val onRequestRemoveGame: (SteamGame) -> Unit,
    val onConfirmRemoveGame: () -> Unit,
    val onDismissRemoveGame: () -> Unit,
    val onOpenModDetail: (DownloadedModGroup) -> Unit,
    val onOpenModLibraryChangeNotes: (DownloadedModGroup) -> Unit,
    val onDismissModLibraryChangeNotes: () -> Unit,
    val onRequestRenameMod: (DownloadedModGroup) -> Unit,
    val onUpdateRenameModTitleInput: (String) -> Unit,
    val onConfirmRenameMod: () -> Unit,
    val onDismissRenameMod: () -> Unit,
    val onOpenModFile: (ExportedDownloadFile) -> Unit,
    val onShareModFile: (ExportedDownloadFile) -> Unit,
    val onUpdateMod: (DownloadedModEntry) -> Unit,
    val onRequestRemoveMod: (DownloadedModEntry) -> Unit,
    val onConfirmRemoveMod: () -> Unit,
    val onDismissRemoveMod: () -> Unit,
    val onToggleGameWorkshopMoreActions: () -> Unit,
    val onDismissGameWorkshopMoreActions: () -> Unit,
    val onOpenGameWorkshopDirectDownloadDialog: () -> Unit,
    val onDismissGameWorkshopDirectDownloadDialog: () -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onOpenSteamLoginDialog: () -> Unit,
    val onDismissSteamLoginDialog: () -> Unit,
    val onUpdateSteamLoginUsername: (String) -> Unit,
    val onUpdateSteamLoginPassword: (String) -> Unit,
    val onUpdateSteamLoginRefreshToken: (String) -> Unit,
    val onUpdateSteamGuardCode: (String) -> Unit,
    val onSwitchSteamLoginInputMode: (SteamLoginInputMode) -> Unit,
    val onSubmitSteamLogin: () -> Unit,
    val onOpenSteamLoginDebugLog: () -> Unit,
    val onShareSteamLoginDebugLog: () -> Unit,
    val onSwitchToAnonymousSteamAccount: () -> Unit,
    val onSetActiveSteamAccount: (String) -> Unit,
    val onReauthenticateSteamAccount: (String) -> Unit,
    val onRemoveSteamAccount: (String) -> Unit,
    val onUpdateThemeMode: (AppThemeMode) -> Unit,
    val onUpdateFrontendMode: (AppFrontendMode) -> Unit,
    val onUpdateSteamLanguagePreference: (SteamLanguagePreference) -> Unit,
    val onOpenBaiduTranslationApiKeyScreen: () -> Unit,
    val onUpdateBaiduTranslationAppIdInput: (String) -> Unit,
    val onUpdateBaiduTranslationApiKeyInput: (String) -> Unit,
    val onSaveBaiduTranslationApiKey: () -> Unit,
    val onTestBaiduTranslationApiKey: () -> Unit,
    val onUpdateAutoCheckUpdates: (Boolean) -> Unit,
    val onUpdatePreferredUpdateSource: (UpdateSource) -> Unit,
    val onCheckForUpdatesNow: () -> Unit,
    val onDismissUpdatePrompt: () -> Unit,
    val onOpenExternalUrl: (String) -> Unit,
    val onUpdateDownloadThreadCountInput: (String) -> Unit,
    val onUpdateConcurrentDownloadTaskCountInput: (String) -> Unit,
    val onUpdateModUpdateConcurrentCheckCountInput: (String) -> Unit,
    val onUpdateAllowSteamAuthenticatedCleartextHttp: (Boolean) -> Unit,
    val onSaveDownloadSettings: () -> Unit,
    val onUpdateAddGameSearchQuery: (String) -> Unit,
    val onSearchGames: () -> Unit,
    val onUpdateDirectAppId: (String) -> Unit,
    val onAddGameById: () -> Unit,
    val onAddGameToLibrary: (SteamGame) -> Unit,
    val onOpenGameWorkshop: (SteamGame) -> Unit,
    val onRetryFeaturedGames: () -> Unit,
    val onUpdateWorkshopSearchQuery: (String) -> Unit,
    val onUpdateWorkshopSort: (WorkshopBrowseSortOption) -> Unit,
    val onUpdateWorkshopTimeWindow: (WorkshopBrowseTimeWindow) -> Unit,
    val onSearchCurrentWorkshop: () -> Unit,
    val onLoadMoreWorkshopItems: () -> Unit,
    val onOpenWorkshopItemDetail: (WorkshopBrowseItem) -> Unit,
    val onRetryWorkshopItemDetail: () -> Unit,
    val onRetryWorkshopCommentsPage: () -> Unit,
    val onLoadPreviousWorkshopCommentsPage: () -> Unit,
    val onLoadNextWorkshopCommentsPage: () -> Unit,
    val onTranslateWorkshopItemDescription: () -> Unit,
    val onTranslateModLibraryDescription: () -> Unit,
    val onDownloadSingleItem: (WorkshopBrowseItem) -> Unit,
)

@Composable
fun WorkshopScreen(
    state: WorkshopUiState,
    actions: WorkshopScreenActions,
    pendingDownloadItemKeys: Set<WorkshopModKey> = emptySet(),
) {
    val selectedTask = state.downloadCenterState.tasks.firstOrNull { it.id == state.selectedDownloadTaskId }
    val selectedMod = state.modLibraryState.selectedEntry
    val saveableStateHolder = rememberSaveableStateHolder()
    val isLiquidGlassFrontend = isLiquidGlassFrontendEnabled()
    val density = LocalDensity.current
    val defaultTopBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp() + if (isLiquidGlassFrontend) 80.dp else 64.dp
    }
    val defaultBottomBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp() + if (isLiquidGlassFrontend) 88.dp else 80.dp
    }
    var measuredTopBarHeight by remember(isLiquidGlassFrontend) {
        mutableStateOf(defaultTopBarHeight)
    }
    var measuredBottomBarHeight by remember(isLiquidGlassFrontend) {
        mutableStateOf(defaultBottomBarHeight)
    }
    val chromePadding = WorkshopChromePadding(
        top = measuredTopBarHeight,
        bottom = if (state.currentScreen.isLibraryRoot()) {
            measuredBottomBarHeight
        } else {
            16.dp
        },
    )

    BackHandler(enabled = !state.currentScreen.isLibraryRoot()) {
        actions.onNavigateBack()
    }

    if (isLiquidGlassFrontend) {
        val wallpaperBackdrop = rememberLayerBackdrop()
        val contentBackdrop = rememberLayerBackdrop()
        val chromeBackdrop = rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
        CompositionLocalProvider(
            LocalWorkshopChromePadding provides chromePadding,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(wallpaperBackdrop),
                ) {
                    WorkshopLiquidGlassWallpaper(modifier = Modifier.fillMaxSize())
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(contentBackdrop),
                ) {
                    CompositionLocalProvider(
                        LocalWorkshopBackdrop provides wallpaperBackdrop,
                    ) {
                        WorkshopBody(
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

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            measuredTopBarHeight = with(density) { size.height.toDp() }
                        },
                ) {
                    CompositionLocalProvider(
                        LocalWorkshopBackdrop provides chromeBackdrop,
                    ) {
                        WorkshopTopBar(
                            state = state,
                            selectedTask = selectedTask,
                            selectedMod = selectedMod,
                            actions = actions,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            measuredBottomBarHeight = with(density) { size.height.toDp() }
                        },
                ) {
                    CompositionLocalProvider(
                        LocalWorkshopBackdrop provides chromeBackdrop,
                    ) {
                        WorkshopLibraryBottomBar(
                            state = state,
                            actions = actions,
                        )
                    }
                }
            }
        }
        return
    }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalWorkshopBackdrop provides backdrop,
        LocalWorkshopChromePadding provides chromePadding,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                color = MaterialTheme.colorScheme.background,
            ) {
                WorkshopBody(
                    state = state,
                    selectedTask = selectedTask,
                    selectedMod = selectedMod,
                    actions = actions,
                    pendingDownloadItemKeys = pendingDownloadItemKeys,
                    saveableStateHolder = saveableStateHolder,
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        measuredTopBarHeight = with(density) { size.height.toDp() }
                    },
            ) {
                WorkshopTopBar(
                    state = state,
                    selectedTask = selectedTask,
                    selectedMod = selectedMod,
                    actions = actions,
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        measuredBottomBarHeight = with(density) { size.height.toDp() }
                    },
            ) {
                WorkshopLibraryBottomBar(
                    state = state,
                    actions = actions,
                )
            }
        }
    }
}
