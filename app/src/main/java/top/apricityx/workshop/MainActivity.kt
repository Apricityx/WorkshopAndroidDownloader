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
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.ui.screen.WorkshopScreen
import top.apricityx.workshop.ui.screen.WorkshopScreenActions
import top.apricityx.workshop.ui.theme.SteamWorkshopDemoTheme

class MainActivity : ComponentActivity() {
    private val workshopViewModel: WorkshopViewModel by viewModels { WorkshopViewModel.Factory }
    private val downloadDebugLogManager by lazy { DownloadDebugLogManager(application) }
    private var pendingDownloadItem: WorkshopBrowseItem? = null
    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val item = pendingDownloadItem ?: return@registerForActivityResult
            pendingDownloadItem = null
            if (!granted) {
                Toast.makeText(
                    this,
                    "未授予存储权限，下载完成后将导出到应用专用目录。",
                    Toast.LENGTH_LONG,
                ).show()
            }
            workshopViewModel.downloadSingleItem(item)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleLaunchIntent()
        }

        setContent {
            val uiState = workshopViewModel.uiState.collectAsStateWithLifecycle().value

            SteamWorkshopDemoTheme(themeMode = uiState.themeMode) {
                LaunchedEffect(uiState.themeMode) {
                    applySystemNightMode(uiState.themeMode)
                }
                LaunchedEffect(Unit) {
                    workshopViewModel.toastMessages.collect { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }

                WorkshopScreen(
                    state = uiState,
                    actions = buildWorkshopScreenActions(),
                )
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

    private fun downloadSingleItemWithCompatibilityGuard(item: WorkshopBrowseItem) {
        if (!shouldRequestLegacyStoragePermission()) {
            workshopViewModel.downloadSingleItem(item)
            return
        }

        pendingDownloadItem = item
        legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
            onOpenModFile = ::openExportedFile,
            onShareModFile = ::shareExportedFile,
            onUpdateMod = workshopViewModel::updateMod,
            onRequestRemoveMod = workshopViewModel::requestRemoveMod,
            onConfirmRemoveMod = workshopViewModel::confirmRemoveMod,
            onDismissRemoveMod = workshopViewModel::dismissRemoveModDialog,
            onNavigateToSettings = workshopViewModel::navigateToSettings,
            onOpenSteamLoginDialog = workshopViewModel::openSteamLoginDialog,
            onDismissSteamLoginDialog = workshopViewModel::dismissSteamLoginDialog,
            onUpdateSteamLoginUsername = workshopViewModel::updateSteamLoginUsername,
            onUpdateSteamLoginPassword = workshopViewModel::updateSteamLoginPassword,
            onUpdateSteamGuardCode = workshopViewModel::updateSteamGuardCode,
            onSubmitSteamLogin = workshopViewModel::submitSteamLogin,
            onSwitchToAnonymousSteamAccount = workshopViewModel::switchToAnonymousSteamAccount,
            onSetActiveSteamAccount = workshopViewModel::setActiveSteamAccount,
            onReauthenticateSteamAccount = workshopViewModel::reauthenticateSteamAccount,
            onRemoveSteamAccount = workshopViewModel::removeSteamAccount,
            onUpdateThemeMode = workshopViewModel::updateThemeMode,
            onUpdateSteamLanguagePreference = workshopViewModel::updateSteamLanguagePreference,
            onUpdateTranslationProvider = workshopViewModel::updateTranslationProvider,
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
            onTranslateWorkshopItemDescription = workshopViewModel::translateWorkshopItemDescription,
            onDownloadSingleItem = ::downloadSingleItemWithCompatibilityGuard,
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
