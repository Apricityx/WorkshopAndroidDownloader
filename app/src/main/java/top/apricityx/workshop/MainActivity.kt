package top.apricityx.workshop

import android.Manifest
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.data.WorkshopRequiredItem
import top.apricityx.workshop.ui.screen.WorkshopScreen
import top.apricityx.workshop.ui.screen.WorkshopScreenActions
import top.apricityx.workshop.ui.component.WorkshopButton
import top.apricityx.workshop.ui.component.WorkshopDialog
import top.apricityx.workshop.ui.component.WorkshopOutlinedButton
import top.apricityx.workshop.ui.component.WorkshopTextButton
import top.apricityx.workshop.ui.theme.SteamWorkshopDemoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val workshopViewModel: WorkshopViewModel by viewModels { WorkshopViewModel.Factory }
    private val downloadDebugLogManager by lazy { DownloadDebugLogManager(application) }
    private val steamLoginDebugLogManager by lazy { SteamLoginDebugLogManager(application) }
    private var pendingDownloadItems: List<WorkshopBrowseItem> = emptyList()
    private var pendingDownloadItemKeys by mutableStateOf<Set<WorkshopModKey>>(emptySet())
    private var downloadDependencyWarningDialogState: DownloadDependencyWarningDialogState? by mutableStateOf(null)
    private var isCheckingDownloadDependencies by mutableStateOf(false)
    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val items = pendingDownloadItems.takeIf { it.isNotEmpty() } ?: return@registerForActivityResult
            pendingDownloadItems = emptyList()
            if (!granted) {
                Toast.makeText(
                    this,
                    "未授予存储权限，下载完成后将导出到应用专用目录。",
                    Toast.LENGTH_LONG,
                ).show()
            }
            if (!workshopViewModel.downloadItems(items)) {
                clearPendingDownloads(items)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleLaunchIntent()
        }

        setContent {
            val uiState = workshopViewModel.uiState.collectAsStateWithLifecycle().value
            val activeDownloadItemKeys = uiState.downloadCenterState.activeTasks
                .map(DownloadCenterTaskUiState::workshopModKey)
                .toSet()

            SteamWorkshopDemoTheme(
                themeMode = uiState.themeMode,
                frontendMode = uiState.frontendMode,
            ) {
                LaunchedEffect(uiState.themeMode) {
                    applySystemNightMode(uiState.themeMode)
                }
                LaunchedEffect(Unit) {
                    workshopViewModel.toastMessages.collect { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                LaunchedEffect(activeDownloadItemKeys) {
                    if (activeDownloadItemKeys.isNotEmpty()) {
                        pendingDownloadItemKeys -= activeDownloadItemKeys
                    }
                }

                WorkshopScreen(
                    state = uiState,
                    actions = buildWorkshopScreenActions(),
                    pendingDownloadItemKeys = pendingDownloadItemKeys,
                )

                downloadDependencyWarningDialogState?.let { dialogState ->
                    WorkshopDialog(
                        onDismissRequest = { downloadDependencyWarningDialogState = null },
                        title = { Text("还有前置未下载") },
                        buttons = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                WorkshopButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val items = dialogState.requiredItems
                                            .map(WorkshopRequiredItem::toBrowseItem) + dialogState.item
                                        downloadDependencyWarningDialogState = null
                                        markDownloadPending(items)
                                        startDownloadItemsWithCompatibilityGuard(items)
                                    },
                                ) {
                                    Text("下载所有前置并下载模组")
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                ) {
                                    WorkshopTextButton(onClick = { downloadDependencyWarningDialogState = null }) {
                                        Text("取消")
                                    }
                                    WorkshopOutlinedButton(
                                        onClick = {
                                            val item = dialogState.item
                                            downloadDependencyWarningDialogState = null
                                            markDownloadPending(item)
                                            startDownloadItemsWithCompatibilityGuard(listOf(item))
                                        },
                                    ) {
                                        Text("只下载模组")
                                    }
                                }
                            }
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("「${dialogState.item.title}」还有 ${dialogState.requiredItems.size} 个前置工坊物品未下载。")
                            Text("你可以选择下载所有前置后再下载模组，或者只下载当前模组。")
                            Text(
                                text = dialogState.requiredItems.joinToString(separator = "\n") { "• ${it.title}" },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent()
    }

    private fun handleLaunchIntent() {
        AdbDownloadCommandParser.parse(intent)?.let(workshopViewModel::applyAdbCommand)
    }

    private fun requestDownloadSingleItem(item: WorkshopBrowseItem) {
        if (isCheckingDownloadDependencies) {
            return
        }
        markDownloadPending(item)

        lifecycleScope.launch {
            isCheckingDownloadDependencies = true
            val requiredItems = try {
                workshopViewModel.loadRequiredItemsForDownload(item)
            } finally {
                isCheckingDownloadDependencies = false
            }
            if (requiredItems.isNotEmpty()) {
                clearPendingDownloads(listOf(item))
                downloadDependencyWarningDialogState = DownloadDependencyWarningDialogState(
                    item = item,
                    requiredItems = requiredItems,
                )
            } else {
                startDownloadItemsWithCompatibilityGuard(listOf(item))
            }
        }
    }

    private fun startDownloadItemsWithCompatibilityGuard(items: List<WorkshopBrowseItem>) {
        val distinctItems = items.distinctBy { workshopItem -> workshopItem.appId to workshopItem.publishedFileId }
        if (distinctItems.isEmpty()) {
            return
        }
        if (!shouldRequestLegacyStoragePermission()) {
            if (!workshopViewModel.downloadItems(distinctItems)) {
                clearPendingDownloads(distinctItems)
            }
            return
        }

        pendingDownloadItems = distinctItems
        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun markDownloadPending(item: WorkshopBrowseItem) {
        markDownloadPending(listOf(item))
    }

    private fun markDownloadPending(items: List<WorkshopBrowseItem>) {
        pendingDownloadItemKeys += items.map(WorkshopBrowseItem::workshopModKey)
    }

    private fun clearPendingDownloads(items: List<WorkshopBrowseItem>) {
        pendingDownloadItemKeys -= items.map(WorkshopBrowseItem::workshopModKey).toSet()
    }

    private fun openExportedFile(file: ExportedDownloadFile) {
        val intent = WorkshopFileOpenManager.createOpenFileIntent(file)
        if (intent == null) {
            Toast.makeText(this, "暂无可打开文件", Toast.LENGTH_SHORT).show()
            return
        }

        launchIntent(
            intent = intent,
            notFoundMessage = "没有找到可打开这个文件的应用",
            failureMessage = "打开文件失败",
        )
    }

    private fun shareExportedFile(file: ExportedDownloadFile) {
        val intent = WorkshopFileShareManager.createShareFileIntent(file)
        if (intent == null) {
            Toast.makeText(this, "暂无可分享文件", Toast.LENGTH_SHORT).show()
            return
        }

        launchIntent(
            intent = intent,
            notFoundMessage = "没有找到可分享这个文件的应用",
            failureMessage = "分享文件失败",
        )
    }

    private fun shareDownloadTaskDebugLog(task: DownloadCenterTaskUiState) {
        val file = downloadDebugLogManager.shareableFile(task)
        if (file == null) {
            Toast.makeText(this, "调试日志还没有生成", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = WorkshopFileShareManager.createShareFileIntent(file)
        if (intent == null) {
            Toast.makeText(this, "暂无可分享调试日志", Toast.LENGTH_SHORT).show()
            return
        }

        launchIntent(
            intent = intent,
            notFoundMessage = "没有找到可分享调试日志的应用",
            failureMessage = "分享调试日志失败",
        )
    }

    private fun openSteamLoginDebugLog() {
        val file = steamLoginDebugLogManager.latestShareableFile()
        if (file == null) {
            Toast.makeText(this, "登录日志还没有生成", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = WorkshopFileOpenManager.createOpenFileIntent(file)
        if (intent == null) {
            Toast.makeText(this, "暂无可打开登录日志", Toast.LENGTH_SHORT).show()
            return
        }

        launchIntent(
            intent = intent,
            notFoundMessage = "没有找到可打开这个登录日志的应用",
            failureMessage = "打开登录日志失败",
        )
    }

    private fun shareSteamLoginDebugLog() {
        val file = steamLoginDebugLogManager.latestShareableFile()
        if (file == null) {
            Toast.makeText(this, "登录日志还没有生成", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = WorkshopFileShareManager.createShareFileIntent(file)
        if (intent == null) {
            Toast.makeText(this, "暂无可分享登录日志", Toast.LENGTH_SHORT).show()
            return
        }

        launchIntent(
            intent = intent,
            notFoundMessage = "没有找到可分享登录日志的应用",
            failureMessage = "分享登录日志失败",
        )
    }

    private fun shareRuntimeAppLog() {
        val file = AppRuntimeLogManager.shareableLatestLogFile(application)
        if (file == null) {
            Toast.makeText(this, "运行日志还没有生成", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = WorkshopFileShareManager.createShareFileIntent(file)
        if (intent == null) {
            Toast.makeText(this, "暂无可分享运行日志", Toast.LENGTH_SHORT).show()
            return
        }

        launchIntent(
            intent = intent,
            notFoundMessage = "没有找到可分享运行日志的应用",
            failureMessage = "分享运行日志失败",
        )
    }

    private fun applySystemNightMode(themeMode: AppThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (themeMode) {
                AppThemeMode.FollowSystem -> UiModeManager.MODE_NIGHT_AUTO
                AppThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                AppThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    private fun shouldRequestLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

    private fun openExternalUrl(url: String) {
        launchIntent(
            intent = Intent(Intent.ACTION_VIEW, url.toUri()),
            notFoundMessage = "没有找到可打开链接的应用",
            failureMessage = "打开链接失败",
        )
    }

    private fun buildWorkshopScreenActions(): WorkshopScreenActions =
        WorkshopScreenActions(
            onNavigateBack = workshopViewModel::navigateBack,
            onNavigateToGameLibrary = workshopViewModel::navigateToGameLibrary,
            onNavigateToModLibrary = workshopViewModel::navigateToModLibrary,
            onNavigateToAddGame = workshopViewModel::navigateToAddGame,
            onNavigateToDownloadCenter = workshopViewModel::navigateToDownloadCenter,
            onClearFinishedDownloadTasks = workshopViewModel::clearFinishedDownloadTasks,
            onOpenDownloadTaskDetail = workshopViewModel::openDownloadTaskDetail,
            onPauseDownloadTask = workshopViewModel::pauseDownloadTask,
            onResumeDownloadTask = workshopViewModel::resumeDownloadTask,
            onRemoveDownloadTask = workshopViewModel::removeDownloadTask,
            onShareDownloadTaskDebugLog = ::shareDownloadTaskDebugLog,
            onShareRuntimeAppLog = ::shareRuntimeAppLog,
            onRetryLibraryLoad = workshopViewModel::retryMainScreenNetwork,
            onRetryModLibrarySync = workshopViewModel::retryModLibrarySync,
            onCheckModLibraryUpdates = workshopViewModel::checkModLibraryUpdates,
            onToggleModLibraryDisplayMode = workshopViewModel::toggleModLibraryDisplayMode,
            onToggleModLibraryFilterPanel = workshopViewModel::toggleModLibraryFilterPanel,
            onUpdateModLibrarySearchQuery = workshopViewModel::updateModLibrarySearchQuery,
            onUpdateModLibraryGameFilter = workshopViewModel::updateModLibraryGameFilter,
            onUpdateModLibrarySortOption = workshopViewModel::updateModLibrarySortOption,
            onClearModLibraryFilters = workshopViewModel::clearModLibraryFilters,
            onDismissUsageNotice = workshopViewModel::dismissUsageNoticeDialog,
            onRequestRemoveGame = workshopViewModel::requestRemoveGame,
            onConfirmRemoveGame = workshopViewModel::confirmRemoveGame,
            onDismissRemoveGame = workshopViewModel::dismissRemoveGameDialog,
            onOpenModDetail = workshopViewModel::openModDetail,
            onRequestRenameMod = workshopViewModel::requestRenameMod,
            onUpdateRenameModTitleInput = workshopViewModel::updateRenameModTitleInput,
            onConfirmRenameMod = workshopViewModel::confirmRenameMod,
            onDismissRenameMod = workshopViewModel::dismissRenameModDialog,
            onOpenModFile = ::openExportedFile,
            onShareModFile = ::shareExportedFile,
            onUpdateMod = workshopViewModel::updateMod,
            onRequestRemoveMod = workshopViewModel::requestRemoveMod,
            onConfirmRemoveMod = workshopViewModel::confirmRemoveMod,
            onDismissRemoveMod = workshopViewModel::dismissRemoveModDialog,
            onToggleGameWorkshopMoreActions = workshopViewModel::toggleGameWorkshopMoreActions,
            onDismissGameWorkshopMoreActions = workshopViewModel::dismissGameWorkshopMoreActions,
            onOpenGameWorkshopDirectDownloadDialog = workshopViewModel::openGameWorkshopDirectDownloadDialog,
            onDismissGameWorkshopDirectDownloadDialog = workshopViewModel::dismissGameWorkshopDirectDownloadDialog,
            onNavigateToSettings = workshopViewModel::navigateToSettings,
            onOpenSteamLoginDialog = workshopViewModel::openSteamLoginDialog,
            onDismissSteamLoginDialog = workshopViewModel::dismissSteamLoginDialog,
            onUpdateSteamLoginUsername = workshopViewModel::updateSteamLoginUsername,
            onUpdateSteamLoginPassword = workshopViewModel::updateSteamLoginPassword,
            onUpdateSteamLoginRefreshToken = workshopViewModel::updateSteamLoginRefreshToken,
            onUpdateSteamGuardCode = workshopViewModel::updateSteamGuardCode,
            onSwitchSteamLoginInputMode = workshopViewModel::switchSteamLoginInputMode,
            onSubmitSteamLogin = workshopViewModel::submitSteamLogin,
            onOpenSteamLoginDebugLog = ::openSteamLoginDebugLog,
            onShareSteamLoginDebugLog = ::shareSteamLoginDebugLog,
            onSwitchToAnonymousSteamAccount = workshopViewModel::switchToAnonymousSteamAccount,
            onSetActiveSteamAccount = workshopViewModel::setActiveSteamAccount,
            onReauthenticateSteamAccount = workshopViewModel::reauthenticateSteamAccount,
            onRemoveSteamAccount = workshopViewModel::removeSteamAccount,
            onUpdateThemeMode = workshopViewModel::updateThemeMode,
            onUpdateFrontendMode = workshopViewModel::updateFrontendMode,
            onUpdateSteamLanguagePreference = workshopViewModel::updateSteamLanguagePreference,
            onOpenBaiduTranslationApiKeyScreen = workshopViewModel::openBaiduTranslationApiKeyScreen,
            onUpdateBaiduTranslationAppIdInput = workshopViewModel::updateBaiduTranslationAppIdInput,
            onUpdateBaiduTranslationApiKeyInput = workshopViewModel::updateBaiduTranslationApiKeyInput,
            onSaveBaiduTranslationApiKey = workshopViewModel::saveBaiduTranslationApiKey,
            onTestBaiduTranslationApiKey = workshopViewModel::testBaiduTranslationConfiguration,
            onUpdateAutoCheckUpdates = workshopViewModel::updateAutoCheckUpdates,
            onUpdatePreferredUpdateSource = workshopViewModel::updatePreferredUpdateSource,
            onCheckForUpdatesNow = workshopViewModel::checkForUpdatesNow,
            onDismissUpdatePrompt = workshopViewModel::dismissUpdatePrompt,
            onOpenExternalUrl = ::openExternalUrl,
            onUpdateDownloadThreadCountInput = workshopViewModel::updateDownloadThreadCountInput,
            onUpdateConcurrentDownloadTaskCountInput = workshopViewModel::updateConcurrentDownloadTaskCountInput,
            onUpdateModUpdateConcurrentCheckCountInput = workshopViewModel::updateModUpdateConcurrentCheckCountInput,
            onUpdateAllowSteamAuthenticatedCleartextHttp = workshopViewModel::updateAllowSteamAuthenticatedCleartextHttp,
            onSaveDownloadSettings = workshopViewModel::saveDownloadSettings,
            onUpdateAddGameSearchQuery = workshopViewModel::updateAddGameSearchQuery,
            onSearchGames = workshopViewModel::searchGames,
            onUpdateDirectAppId = workshopViewModel::updateDirectAppId,
            onAddGameById = workshopViewModel::addGameById,
            onAddGameToLibrary = workshopViewModel::addGameToLibrary,
            onOpenGameWorkshop = workshopViewModel::openGameWorkshop,
            onRetryFeaturedGames = workshopViewModel::retryFeaturedGames,
            onUpdateWorkshopSearchQuery = workshopViewModel::updateWorkshopSearchQuery,
            onUpdateWorkshopSort = workshopViewModel::updateWorkshopSort,
            onUpdateWorkshopTimeWindow = workshopViewModel::updateWorkshopTimeWindow,
            onSearchCurrentWorkshop = workshopViewModel::searchCurrentGameWorkshop,
            onLoadMoreWorkshopItems = workshopViewModel::loadMoreWorkshopItems,
            onOpenWorkshopItemDetail = workshopViewModel::openWorkshopItemDetail,
            onRetryWorkshopItemDetail = workshopViewModel::retryWorkshopItemDetail,
            onRetryWorkshopCommentsPage = workshopViewModel::retryWorkshopCommentsPage,
            onLoadPreviousWorkshopCommentsPage = workshopViewModel::loadPreviousWorkshopCommentsPage,
            onLoadNextWorkshopCommentsPage = workshopViewModel::loadNextWorkshopCommentsPage,
            onTranslateWorkshopItemDescription = workshopViewModel::translateWorkshopItemDescription,
            onTranslateModLibraryDescription = workshopViewModel::translateModLibraryDescription,
            onDownloadSingleItem = ::requestDownloadSingleItem,
        )

    private fun launchIntent(
        intent: Intent,
        notFoundMessage: String,
        failureMessage: String,
    ) {
        runCatching {
            startActivity(intent)
        }.onFailure { error ->
            val message = if (error is ActivityNotFoundException) notFoundMessage else failureMessage
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}

private data class DownloadDependencyWarningDialogState(
    val item: WorkshopBrowseItem,
    val requiredItems: List<WorkshopRequiredItem>,
)
