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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.ui.screen.WorkshopScreen
import top.apricityx.workshop.ui.screen.WorkshopScreenActions
import top.apricityx.workshop.ui.theme.SteamWorkshopDemoTheme

class MainActivity : ComponentActivity() {
    private val workshopViewModel: WorkshopViewModel by viewModels { WorkshopViewModel.Factory }
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
            val uiState by workshopViewModel.uiState.collectAsStateWithLifecycle()

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
                    actions = WorkshopScreenActions(
                        onNavigateBack = workshopViewModel::navigateBack,
                        onNavigateToAddGame = workshopViewModel::navigateToAddGame,
                        onNavigateToDownloadCenter = workshopViewModel::navigateToDownloadCenter,
                        onClearFinishedDownloadTasks = workshopViewModel::clearFinishedDownloadTasks,
                        onOpenDownloadTaskDetail = workshopViewModel::openDownloadTaskDetail,
                        onPauseDownloadTask = workshopViewModel::pauseDownloadTask,
                        onResumeDownloadTask = workshopViewModel::resumeDownloadTask,
                        onRemoveDownloadTask = workshopViewModel::removeDownloadTask,
                        onOpenDownloadFile = { taskId, contentUri -> openDownloadedFile(uiState, taskId, contentUri) },
                        onRetryLibraryLoad = workshopViewModel::retryMainScreenNetwork,
                        onRequestRemoveGame = workshopViewModel::requestRemoveGame,
                        onConfirmRemoveGame = workshopViewModel::confirmRemoveGame,
                        onDismissRemoveGame = workshopViewModel::dismissRemoveGameDialog,
                        onNavigateToSettings = workshopViewModel::navigateToSettings,
                        onUpdateThemeMode = workshopViewModel::updateThemeMode,
                        onUpdateAutoCheckUpdates = workshopViewModel::updateAutoCheckUpdates,
                        onUpdatePreferredUpdateSource = workshopViewModel::updatePreferredUpdateSource,
                        onCheckForUpdatesNow = workshopViewModel::checkForUpdatesNow,
                        onDismissUpdatePrompt = workshopViewModel::dismissUpdatePrompt,
                        onOpenExternalUrl = ::openExternalUrl,
                        onUpdateDownloadThreadCountInput = workshopViewModel::updateDownloadThreadCountInput,
                        onUpdateConcurrentDownloadTaskCountInput = workshopViewModel::updateConcurrentDownloadTaskCountInput,
                        onSaveDownloadSettings = workshopViewModel::saveDownloadSettings,
                        onUpdateAddGameSearchQuery = workshopViewModel::updateAddGameSearchQuery,
                        onSearchGames = workshopViewModel::searchGames,
                        onUpdateDirectAppId = workshopViewModel::updateDirectAppId,
                        onAddGameById = workshopViewModel::addGameById,
                        onAddGameToLibrary = workshopViewModel::addGameToLibrary,
                        onOpenGameWorkshop = workshopViewModel::openGameWorkshop,
                        onUpdateWorkshopSearchQuery = workshopViewModel::updateWorkshopSearchQuery,
                        onSearchCurrentWorkshop = workshopViewModel::searchCurrentGameWorkshop,
                        onLoadMoreWorkshopItems = workshopViewModel::loadMoreWorkshopItems,
                        onOpenWorkshopItemDetail = workshopViewModel::openWorkshopItemDetail,
                        onRetryWorkshopItemDetail = workshopViewModel::retryWorkshopItemDetail,
                        onDownloadSingleItem = ::downloadSingleItemWithCompatibilityGuard,
                    ),
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
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

    private fun openDownloadedFile(
        uiState: WorkshopUiState,
        taskId: String,
        contentUri: String,
    ) {
        val task = uiState.downloadCenterState.tasks.firstOrNull { it.id == taskId }
        val file = task?.files?.firstOrNull { it.contentUri == contentUri }
        val intent = file?.let(WorkshopFileOpenManager::createOpenFileIntent)
        if (intent == null) {
            Toast.makeText(this, "暂无可打开文件", Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            val message = if (it is ActivityNotFoundException) "没有找到可打开这个文件的应用" else "打开文件失败"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
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
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        runCatching {
            startActivity(intent)
        }.onFailure {
            val message = if (it is ActivityNotFoundException) "没有找到可打开链接的应用" else "打开链接失败"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
