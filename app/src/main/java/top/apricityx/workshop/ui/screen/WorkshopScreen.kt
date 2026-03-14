package top.apricityx.workshop.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import top.apricityx.workshop.AppThemeMode
import top.apricityx.workshop.DownloadCenterTaskUiState
import top.apricityx.workshop.DownloadedModEntry
import top.apricityx.workshop.ExportedDownloadFile
import top.apricityx.workshop.SteamLanguagePreference
import top.apricityx.workshop.TranslationProvider
import top.apricityx.workshop.WorkshopBrowseSortOption
import top.apricityx.workshop.WorkshopBrowseTimeWindow
import top.apricityx.workshop.WorkshopUiState
import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.isLibraryRoot
import top.apricityx.workshop.update.UpdateSource

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
    val onRequestRemoveGame: (SteamGame) -> Unit,
    val onConfirmRemoveGame: () -> Unit,
    val onDismissRemoveGame: () -> Unit,
    val onOpenModDetail: (DownloadedModEntry) -> Unit,
    val onOpenModFile: (ExportedDownloadFile) -> Unit,
    val onShareModFile: (ExportedDownloadFile) -> Unit,
    val onRequestRemoveMod: (DownloadedModEntry) -> Unit,
    val onConfirmRemoveMod: () -> Unit,
    val onDismissRemoveMod: () -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onOpenSteamLoginDialog: () -> Unit,
    val onDismissSteamLoginDialog: () -> Unit,
    val onUpdateSteamLoginUsername: (String) -> Unit,
    val onUpdateSteamLoginPassword: (String) -> Unit,
    val onUpdateSteamGuardCode: (String) -> Unit,
    val onSubmitSteamLogin: () -> Unit,
    val onSwitchToAnonymousSteamAccount: () -> Unit,
    val onSetActiveSteamAccount: (String) -> Unit,
    val onReauthenticateSteamAccount: (String) -> Unit,
    val onRemoveSteamAccount: (String) -> Unit,
    val onUpdateThemeMode: (AppThemeMode) -> Unit,
    val onUpdateSteamLanguagePreference: (SteamLanguagePreference) -> Unit,
    val onUpdateTranslationProvider: (TranslationProvider) -> Unit,
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
    val onTranslateWorkshopItemDescription: () -> Unit,
    val onDownloadSingleItem: (WorkshopBrowseItem) -> Unit,
)

@Composable
fun WorkshopScreen(
    state: WorkshopUiState,
    actions: WorkshopScreenActions,
) {
    val selectedTask = state.downloadCenterState.tasks.firstOrNull { it.id == state.selectedDownloadTaskId }
    val selectedMod = state.modLibraryState.selectedEntry
    val saveableStateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = !state.currentScreen.isLibraryRoot()) {
        actions.onNavigateBack()
    }

    Scaffold(
        topBar = {
            WorkshopTopBar(
                state = state,
                selectedTask = selectedTask,
                selectedMod = selectedMod,
                actions = actions,
            )
        },
        bottomBar = {
            WorkshopLibraryBottomBar(
                state = state,
                actions = actions,
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
            WorkshopBody(
                state = state,
                selectedTask = selectedTask,
                selectedMod = selectedMod,
                actions = actions,
                saveableStateHolder = saveableStateHolder,
            )
        }
    }
}
