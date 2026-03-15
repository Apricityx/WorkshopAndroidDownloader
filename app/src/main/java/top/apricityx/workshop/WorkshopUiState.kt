package top.apricityx.workshop

import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.update.UpdateSource
import top.apricityx.workshop.steam.protocol.SteamGuardChallengeType

enum class WorkshopScreenDestination {
    GameLibrary,
    ModLibrary,
    AddGame,
    GameWorkshop,
    WorkshopItemDetail,
    ModDetail,
    DownloadCenter,
    DownloadTaskDetail,
    Settings,
    BaiduTranslationApiKey,
}

fun WorkshopScreenDestination.isLibraryRoot(): Boolean =
    this == WorkshopScreenDestination.GameLibrary || this == WorkshopScreenDestination.ModLibrary

fun WorkshopScreenDestination.showsDownloadCenterShortcut(): Boolean =
    this != WorkshopScreenDestination.DownloadCenter &&
        this != WorkshopScreenDestination.DownloadTaskDetail &&
        this != WorkshopScreenDestination.Settings &&
        this != WorkshopScreenDestination.BaiduTranslationApiKey

fun WorkshopScreenDestination.showsSettingsShortcut(): Boolean =
    this != WorkshopScreenDestination.DownloadCenter &&
        this != WorkshopScreenDestination.DownloadTaskDetail &&
        this != WorkshopScreenDestination.Settings &&
        this != WorkshopScreenDestination.BaiduTranslationApiKey

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

enum class TranslationProvider(
    val storageValue: String,
) {
    OnDevice("on_device"),
    BaiduGeneralText("baidu_general_text");

    companion object {
        fun fromStorageValue(value: String): TranslationProvider =
            entries.firstOrNull { it.storageValue == value } ?: OnDevice
    }
}

enum class SteamLanguagePreference(
    val storageValue: String,
    val requestValue: String,
    val acceptLanguageValue: String,
) {
    SimplifiedChinese(
        storageValue = "schinese",
        requestValue = "schinese",
        acceptLanguageValue = "zh-CN,zh;q=0.9",
    ),
    English(
        storageValue = "english",
        requestValue = "english",
        acceptLanguageValue = "en-US,en;q=0.9",
    );

    companion object {
        fun fromStorageValue(value: String): SteamLanguagePreference =
            entries.firstOrNull { it.storageValue == value } ?: SimplifiedChinese
    }
}

enum class ModLibraryDisplayMode(
    val storageValue: String,
) {
    LargePreview("large_preview"),
    CompactList("compact_list"),
    Overview("overview");

    fun next(): ModLibraryDisplayMode =
        when (this) {
            LargePreview -> CompactList
            CompactList -> Overview
            Overview -> LargePreview
        }

    companion object {
        fun fromStorageValue(value: String): ModLibraryDisplayMode =
            entries.firstOrNull { it.storageValue == value } ?: CompactList
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
    val selectedSortOption: WorkshopBrowseSortOption = WorkshopBrowseSortOption.MostPopular,
    val selectedTimeWindow: WorkshopBrowseTimeWindow = WorkshopBrowseTimeWindow.OneWeek,
    val items: List<WorkshopBrowseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val message: String? = null,
    val showConnectionErrorState: Boolean = false,
    val retryLoadMoreOnError: Boolean = false,
)

data class WorkshopItemDetailUiState(
    val item: WorkshopBrowseItem,
    val detail: top.apricityx.workshop.data.WorkshopItemDetail? = null,
    val isLoading: Boolean = false,
    val isTranslatingDescription: Boolean = false,
    val translatedDescription: String? = null,
    val translationErrorMessage: String? = null,
    val message: String? = null,
    val showConnectionErrorState: Boolean = false,
)

data class ModLibraryUiState(
    val items: List<DownloadedModEntry> = emptyList(),
    val selectedEntry: DownloadedModEntry? = null,
    val displayMode: ModLibraryDisplayMode = DownloadSettingsRepository.DEFAULT_MOD_LIBRARY_DISPLAY_MODE,
    val updateCheckState: ModLibraryUpdateCheckState = ModLibraryUpdateCheckState(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
)

data class WorkshopUiState(
    val currentScreen: WorkshopScreenDestination = WorkshopScreenDestination.GameLibrary,
    val previousScreen: WorkshopScreenDestination = WorkshopScreenDestination.GameLibrary,
    val themeMode: AppThemeMode = DownloadSettingsRepository.DEFAULT_THEME_MODE,
    val libraryGames: List<SteamGame> = emptyList(),
    val isLibraryLoading: Boolean = true,
    val libraryMessage: String? = null,
    val libraryError: LibraryErrorUiState? = null,
    val pendingRemoveGame: SteamGame? = null,
    val modLibraryState: ModLibraryUiState = ModLibraryUiState(isLoading = true),
    val pendingRemoveMod: DownloadedModEntry? = null,
    val showUsageNoticeDialog: Boolean = false,
    val addGameState: AddGameUiState = AddGameUiState(),
    val gameWorkshopState: GameWorkshopUiState? = null,
    val workshopItemDetailState: WorkshopItemDetailUiState? = null,
    val downloadCenterState: DownloadCenterUiState = DownloadCenterUiState(),
    val selectedDownloadTaskId: String? = null,
    val settingsState: SettingsUiState = SettingsUiState(),
    val baiduTranslationApiKeyState: BaiduTranslationApiKeyUiState = BaiduTranslationApiKeyUiState(),
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
    val modUpdateConcurrentCheckCountInput: String = "",
    val savedModUpdateConcurrentCheckCount: Int = DownloadSettingsRepository.DEFAULT_MOD_UPDATE_CONCURRENT_CHECKS,
    val selectedThemeMode: AppThemeMode = DownloadSettingsRepository.DEFAULT_THEME_MODE,
    val selectedSteamLanguagePreference: SteamLanguagePreference =
        DownloadSettingsRepository.DEFAULT_STEAM_LANGUAGE_PREFERENCE,
    val selectedTranslationProvider: TranslationProvider = DownloadSettingsRepository.DEFAULT_TRANSLATION_PROVIDER,
    val baiduTranslationApiKeyConfigured: Boolean = false,
    val steamAuthState: SteamAuthUiState = SteamAuthUiState(),
    val autoCheckUpdatesEnabled: Boolean = DownloadSettingsRepository.DEFAULT_AUTO_CHECK_UPDATES_ENABLED,
    val preferredUpdateSource: UpdateSource = UpdateSource.DEFAULT_PREFERRED_USER_SOURCE,
    val availableUpdateSources: List<UpdateSource> = UpdateSource.userSelectableSources(),
    val currentVersionText: String = "",
    val updateStatusSummary: String = "尚未执行过更新检查。",
    val updateCheckInProgress: Boolean = false,
    val updatePromptState: UpdatePromptState? = null,
    val message: String? = null,
)

data class BaiduTranslationApiKeyUiState(
    val appIdInput: String = "",
    val apiKeyInput: String = "",
    val hasSavedCredentials: Boolean = false,
    val isTesting: Boolean = false,
    val sampleSourceText: String = BAIDU_TRANSLATION_SAMPLE_TEXT,
    val testResultText: String? = null,
    val testFailureReason: String? = null,
    val message: String? = null,
)

data class SteamAuthUiState(
    val accounts: List<SteamAccountSummary> = emptyList(),
    val activeAccountId: String? = null,
    val statusSummary: String =
        "当前浏览账号：匿名。未登录时只能保证公开可见内容，部分成人内容或需要年龄确认的条目可能不会出现。",
    val loginDialogState: SteamLoginDialogUiState? = null,
)

data class SteamLoginDialogUiState(
    val mode: SteamLoginDialogMode = SteamLoginDialogMode.Add,
    val username: String = "",
    val password: String = "",
    val guardCode: String = "",
    val challengeType: SteamGuardChallengeType? = null,
    val challengeMessage: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val targetAccountId: String? = null,
)

enum class SteamLoginDialogMode {
    Add,
    Reauthenticate,
}

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

fun SteamLanguagePreference.displayName(): String =
    when (this) {
        SteamLanguagePreference.SimplifiedChinese -> "简体中文"
        SteamLanguagePreference.English -> "English"
    }

fun TranslationProvider.displayName(): String =
    when (this) {
        TranslationProvider.OnDevice -> "本地翻译"
        TranslationProvider.BaiduGeneralText -> "百度大模型文本翻译"
    }

fun ModLibraryDisplayMode.screenSubtitle(): String =
    when (this) {
        ModLibraryDisplayMode.LargePreview ->
            "当前为大图显示，这里展示已经导出到本地的模组文件，会在启动和下载完成后自动同步。"
        ModLibraryDisplayMode.CompactList ->
            "当前为精简列表显示，这里展示已经导出到本地的模组文件，会在启动和下载完成后自动同步。"
        ModLibraryDisplayMode.Overview ->
            "当前为总览模式，这里会优先展示模组预览图，方便快速浏览本地模组。"
    }

fun ModLibraryDisplayMode.sectionSubtitle(): String =
    when (this) {
        ModLibraryDisplayMode.LargePreview ->
            "列表操作默认作用于主文件，进入详情后可以逐个文件处理。"
        ModLibraryDisplayMode.CompactList ->
            "点击条目进入详情后，可以打开、分享或删除文件。"
        ModLibraryDisplayMode.Overview ->
            "点击缩略图进入详情，长按可打开操作菜单。"
    }

fun ModLibraryDisplayMode.toggleContentDescription(): String =
    when (next()) {
        ModLibraryDisplayMode.LargePreview -> "切换为大图显示"
        ModLibraryDisplayMode.CompactList -> "切换为精简列表"
        ModLibraryDisplayMode.Overview -> "切换为总览模式"
    }

fun SteamAccountsSnapshot.toUiState(loginDialogState: SteamLoginDialogUiState? = null): SteamAuthUiState =
    activeAccount.let { currentActiveAccount ->
        SteamAuthUiState(
            accounts = accounts,
            activeAccountId = activeAccountId,
            statusSummary = if (currentActiveAccount != null) {
                if (currentActiveAccount.requiresReauthentication) {
                    "当前浏览账号：${currentActiveAccount.accountName}。该账号需要重新认证，浏览将自动回退到匿名可见性。"
                } else {
                    "当前浏览账号：${currentActiveAccount.accountName}。工坊浏览会自动投影 Steam 登录态，下载任务会在入队时冻结当前账号。"
                }
            } else {
                "当前浏览账号：匿名。未登录时只能保证公开可见内容，部分成人内容或需要年龄确认的条目可能不会出现。"
            },
            loginDialogState = loginDialogState,
        )
    }

const val BAIDU_TRANSLATION_SAMPLE_TEXT =
    "This mod adds a new relic and several balance changes for a smoother run."
