package top.apricityx.workshop

import android.content.ActivityNotFoundException
import android.content.Intent
import android.app.UiModeManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import top.apricityx.workshop.ui.screen.WorkshopScreen
import top.apricityx.workshop.ui.screen.WorkshopScreenActions
import top.apricityx.workshop.ui.theme.SteamWorkshopDemoTheme

class MainActivity : ComponentActivity() {
    private val workshopViewModel: WorkshopViewModel by viewModels { WorkshopViewModel.Factory }

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
                    getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                        when (uiState.themeMode) {
                            AppThemeMode.FollowSystem -> UiModeManager.MODE_NIGHT_AUTO
                            AppThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                            AppThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                        },
                    )
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
                        onDownloadSingleItem = workshopViewModel::downloadSingleItem,
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
