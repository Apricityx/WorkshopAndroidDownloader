package top.apricityx.workshop

import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.update.UpdateSource

enum class WorkshopScreenDestination {
    Library,
    AddGame,
    GameWorkshop,
    WorkshopItemDetail,
    DownloadCenter,
    DownloadTaskDetail,
    Settings,
}

enum class AppThemeMode(
    val storageValue: String,
) {
    FollowSystem("follow_system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorageValue(value: String): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: FollowSystem
    }
}

data class AddGameUiState(
    val featuredGames: List<SteamGame> = emptyList(),
    val searchResults: List<SteamGame> = emptyList(),
    val searchQuery: String = "",
    val directAppIdText: String = "",
    val isLoadingFeatured: Boolean = false,
    val isSearching: Boolean = false,
    val searchRequestFailed: Boolean = false,
    val featuredErrorMessage: String? = null,
    val message: String? = null,
)

data class GameWorkshopUiState(
    val game: SteamGame,
    val searchQuery: String = "",
    val items: List<WorkshopBrowseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val message: String? = null,
)

data class WorkshopItemDetailUiState(
    val item: WorkshopBrowseItem,
    val detail: top.apricityx.workshop.data.WorkshopItemDetail? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

data class WorkshopUiState(
    val currentScreen: WorkshopScreenDestination = WorkshopScreenDestination.Library,
    val previousScreen: WorkshopScreenDestination = WorkshopScreenDestination.Library,
    val themeMode: AppThemeMode = DownloadSettingsRepository.DEFAULT_THEME_MODE,
    val libraryGames: List<SteamGame> = emptyList(),
    val isLibraryLoading: Boolean = true,
    val libraryMessage: String? = null,
    val libraryError: LibraryErrorUiState? = null,
    val pendingRemoveGame: SteamGame? = null,
    val addGameState: AddGameUiState = AddGameUiState(),
    val gameWorkshopState: GameWorkshopUiState? = null,
    val workshopItemDetailState: WorkshopItemDetailUiState? = null,
    val downloadCenterState: DownloadCenterUiState = DownloadCenterUiState(),
    val selectedDownloadTaskId: String? = null,
    val settingsState: SettingsUiState = SettingsUiState(),
)

data class LibraryErrorUiState(
    val reason: String,
    val showAcceleratorHint: Boolean = false,
)

data class SettingsUiState(
    val downloadThreadCountInput: String = "",
    val savedDownloadThreadCount: Int = DownloadSettingsRepository.DEFAULT_DOWNLOAD_THREADS,
    val concurrentDownloadTaskCountInput: String = "",
    val savedConcurrentDownloadTaskCount: Int = DownloadSettingsRepository.DEFAULT_CONCURRENT_DOWNLOAD_TASKS,
    val selectedThemeMode: AppThemeMode = DownloadSettingsRepository.DEFAULT_THEME_MODE,
    val autoCheckUpdatesEnabled: Boolean = DownloadSettingsRepository.DEFAULT_AUTO_CHECK_UPDATES_ENABLED,
    val preferredUpdateSource: UpdateSource = UpdateSource.DEFAULT_PREFERRED_USER_SOURCE,
    val availableUpdateSources: List<UpdateSource> = UpdateSource.userSelectableSources(),
    val currentVersionText: String = "",
    val updateStatusSummary: String = "尚未执行过更新检查。",
    val updateCheckInProgress: Boolean = false,
    val updatePromptState: UpdatePromptState? = null,
    val message: String? = null,
)

data class UpdatePromptState(
    val currentVersion: String,
    val latestVersion: String,
    val publishedAtText: String,
    val downloadSourceDisplayName: String,
    val notesText: String,
    val downloadUrl: String,
)

fun AppThemeMode.displayName(): String =
    when (this) {
        AppThemeMode.FollowSystem -> "跟随系统"
        AppThemeMode.Light -> "亮色模式"
        AppThemeMode.Dark -> "深色模式"
    }
