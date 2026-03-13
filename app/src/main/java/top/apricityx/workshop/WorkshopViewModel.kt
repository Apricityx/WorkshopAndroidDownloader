package top.apricityx.workshop

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import top.apricityx.workshop.steam.protocol.SteamGuardChallengeType
import top.apricityx.workshop.data.GameLibraryRepository
import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.data.SteamGameRepository
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.data.WorkshopBrowseRepository
import top.apricityx.workshop.data.WorkshopDetailRepository
import top.apricityx.workshop.update.UpdateCheckExecutionResult
import top.apricityx.workshop.update.UpdateDownloadResolution
import top.apricityx.workshop.update.UpdateReleaseInfo
import top.apricityx.workshop.update.UpdateSource
import top.apricityx.workshop.update.UpdateUiMessage
import top.apricityx.workshop.update.WorkshopUpdateService
import top.apricityx.workshop.update.WorkshopUpdateUiReducer
import top.apricityx.workshop.update.WorkshopUpdateVersioning

class WorkshopViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val steamAuthRepository = SteamAuthRepository(application)
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(SteamCookieInterceptor(steamAuthRepository))
        .build()
    private val gameRepository = SteamGameRepository(httpClient)
    private val browseRepository = WorkshopBrowseRepository(httpClient)
    private val detailRepository = WorkshopDetailRepository(httpClient)
    private val libraryRepository = GameLibraryRepository(application)
    private val modLibraryRepository = ModLibraryRepository(application)
    private val downloadCenterManager = DownloadCenterManager.getInstance(application)
    private val settingsRepository = DownloadSettingsRepository(application)
    private val updateService = WorkshopUpdateService(httpClient)
    private val descriptionTranslator = OnDeviceDescriptionTranslator(application)

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<WorkshopUiState> = _uiState.asStateFlow()
    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessages = _toastMessages.asSharedFlow()

    private var lastDownloadCenterModSignature: String? = null

    init {
        refreshLibrary()
        refreshModLibrary()
        loadFeaturedGames()
        maybeStartAutoUpdateCheck()
        lastDownloadCenterModSignature = buildModLibrarySyncSignature(downloadCenterManager.uiState.value)
        viewModelScope.launch {
            downloadCenterManager.uiState.collect { downloadCenterState ->
                val nextSignature = buildModLibrarySyncSignature(downloadCenterState)
                val shouldRefreshModLibrary = nextSignature != lastDownloadCenterModSignature
                lastDownloadCenterModSignature = nextSignature
                _uiState.update { state ->
                    state.copy(downloadCenterState = downloadCenterState)
                }
                if (shouldRefreshModLibrary) {
                    refreshModLibrary(showLoading = false)
                }
            }
        }
    }

    fun navigateBack() {
        val state = _uiState.value
        when (state.currentScreen) {
            WorkshopScreenDestination.GameLibrary,
            WorkshopScreenDestination.ModLibrary,
            -> Unit

            WorkshopScreenDestination.AddGame,
            WorkshopScreenDestination.GameWorkshop,
            -> navigateTo(WorkshopScreenDestination.GameLibrary, rememberPrevious = false)

            WorkshopScreenDestination.WorkshopItemDetail ->
                navigateTo(WorkshopScreenDestination.GameWorkshop, rememberPrevious = false)

            WorkshopScreenDestination.ModDetail ->
                navigateTo(WorkshopScreenDestination.ModLibrary, rememberPrevious = false)

            WorkshopScreenDestination.DownloadCenter,
            WorkshopScreenDestination.Settings,
            -> navigateTo(state.previousScreen, rememberPrevious = false)

            WorkshopScreenDestination.DownloadTaskDetail -> {
                _uiState.update { it.copy(selectedDownloadTaskId = null) }
                navigateTo(WorkshopScreenDestination.DownloadCenter, rememberPrevious = false)
            }
        }
    }

    fun navigateToGameLibrary() {
        navigateTo(WorkshopScreenDestination.GameLibrary, rememberPrevious = false)
    }

    fun navigateToModLibrary() {
        navigateTo(WorkshopScreenDestination.ModLibrary, rememberPrevious = false)
    }
    fun navigateToAddGame() {
        navigateTo(WorkshopScreenDestination.AddGame)
    }

    fun navigateToDownloadCenter() {
        navigateTo(WorkshopScreenDestination.DownloadCenter)
    }

    fun navigateToSettings() {
        val currentThreads = settingsRepository.getDownloadThreadCount()
        val currentConcurrentTasks = settingsRepository.getConcurrentDownloadTaskCount()
        val currentThemeMode = settingsRepository.getThemeMode()
        val currentSteamAuthState = steamAuthRepository.loadSnapshot().toUiState(
            loginDialogState = _uiState.value.settingsState.steamAuthState.loginDialogState,
        )
        _uiState.update { state ->
            state.copy(
                themeMode = currentThemeMode,
                settingsState = state.settingsState.copy(
                    downloadThreadCountInput = currentThreads.toString(),
                    savedDownloadThreadCount = currentThreads,
                    concurrentDownloadTaskCountInput = currentConcurrentTasks.toString(),
                    savedConcurrentDownloadTaskCount = currentConcurrentTasks,
                    selectedThemeMode = currentThemeMode,
                    steamAuthState = currentSteamAuthState,
                    autoCheckUpdatesEnabled = settingsRepository.isAutoCheckUpdatesEnabled(),
                    preferredUpdateSource = settingsRepository.getPreferredUpdateSource(),
                    availableUpdateSources = UpdateSource.userSelectableSources(),
                    currentVersionText = BuildConfig.VERSION_NAME,
                    updateStatusSummary = buildUpdateStatusSummary(),
                    message = null,
                ),
            )
        }
        navigateTo(WorkshopScreenDestination.Settings)
    }

    fun clearFinishedDownloadTasks() {
        downloadCenterManager.clearFinishedTasks()
    }

    fun pauseDownloadTask(taskId: String) {
        downloadCenterManager.pauseTask(taskId)
    }

    fun resumeDownloadTask(taskId: String) {
        downloadCenterManager.resumeTask(taskId)
    }

    fun removeDownloadTask(taskId: String) {
        val isSelectedTask = _uiState.value.selectedDownloadTaskId == taskId
        if (isSelectedTask) {
            _uiState.update { it.copy(selectedDownloadTaskId = null) }
            navigateTo(WorkshopScreenDestination.DownloadCenter, rememberPrevious = false)
        }
        downloadCenterManager.removeTask(taskId)
    }

    fun openDownloadTaskDetail(taskId: String) {
        _uiState.update { it.copy(selectedDownloadTaskId = taskId) }
        navigateTo(WorkshopScreenDestination.DownloadTaskDetail)
    }

    fun retryMainScreenNetwork() {
        refreshLibrary()
    }

    fun retryModLibrarySync() {
        refreshModLibrary()
    }

    fun checkModLibraryUpdates() {
        val entries = _uiState.value.modLibraryState.items
        if (entries.isEmpty()) {
            viewModelScope.launch {
                _toastMessages.emit("模组库还是空的，没有可检查的模组。")
            }
            return
        }
        if (_uiState.value.modLibraryState.updateCheckState.isChecking) {
            return
        }

        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    updateCheckState = ModLibraryUpdateCheckState(
                        isChecking = true,
                        lastCheckedAtMillis = state.modLibraryState.updateCheckState.lastCheckedAtMillis,
                        results = entries.associate { entry ->
                            entry.modLibraryKey() to ModUpdateCheckResult(status = ModUpdateCheckStatus.Checking)
                        },
                    ),
                ),
            )
        }

        viewModelScope.launch {
            val results = linkedMapOf<String, ModUpdateCheckResult>()
            entries.forEach { entry ->
                val checkedAtMillis = System.currentTimeMillis()
                val result = runCatching {
                    withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                        detailRepository.loadWorkshopItemDetail(entry.toWorkshopBrowseItem())
                    }
                }.fold(
                    onSuccess = { detail ->
                        evaluateModUpdate(
                            entry = entry,
                            remoteUpdatedEpochSeconds = detail.timeUpdatedEpochSeconds,
                            checkedAtMillis = checkedAtMillis,
                        )
                    },
                    onFailure = { error ->
                        ModUpdateCheckResult(
                            status = ModUpdateCheckStatus.Failed,
                            checkedAtMillis = checkedAtMillis,
                            message = if (error.isTimeoutRequestFailure()) {
                                REQUEST_TIMEOUT_MESSAGE
                            } else {
                                error.message ?: "检查更新失败。"
                            },
                        )
                    },
                )
                results[entry.modLibraryKey()] = result
                _uiState.update { state ->
                    val nextUpdateCheckState = state.modLibraryState.updateCheckState.copy(
                        results = state.modLibraryState.updateCheckState.results + (entry.modLibraryKey() to result),
                    ).filterForEntries(state.modLibraryState.items)
                    state.copy(
                        modLibraryState = state.modLibraryState.copy(
                            updateCheckState = nextUpdateCheckState,
                        ),
                    )
                }
            }

            val summaryMessage = buildModUpdateCheckSummary(results.values)
            val checkedAtMillis = System.currentTimeMillis()
            _uiState.update { state ->
                val nextUpdateCheckState = state.modLibraryState.updateCheckState.copy(
                    isChecking = false,
                    summaryMessage = summaryMessage,
                    lastCheckedAtMillis = checkedAtMillis,
                    results = results,
                ).filterForEntries(state.modLibraryState.items)
                state.copy(
                    modLibraryState = state.modLibraryState.copy(
                        updateCheckState = nextUpdateCheckState,
                    ),
                )
            }
            _toastMessages.emit(summaryMessage)
        }
    }

    fun toggleModLibraryDisplayMode() {
        val nextMode = when (_uiState.value.modLibraryState.displayMode) {
            ModLibraryDisplayMode.LargePreview -> ModLibraryDisplayMode.CompactList
            ModLibraryDisplayMode.CompactList -> ModLibraryDisplayMode.LargePreview
        }
        settingsRepository.setModLibraryDisplayMode(nextMode)
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(displayMode = nextMode),
            )
        }
    }

    fun updateDownloadThreadCountInput(value: String) {
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    downloadThreadCountInput = value.filter(Char::isDigit),
                    message = null,
                ),
            )
        }
    }

    fun updateConcurrentDownloadTaskCountInput(value: String) {
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    concurrentDownloadTaskCountInput = value.filter(Char::isDigit),
                    message = null,
                ),
            )
        }
    }

    fun updateThemeMode(themeMode: AppThemeMode) {
        settingsRepository.setThemeMode(themeMode)
        _uiState.update { state ->
            state.copy(
                themeMode = themeMode,
                settingsState = state.settingsState.copy(
                    selectedThemeMode = themeMode,
                    message = "已切换为${themeMode.displayName()}。",
                ),
            )
        }
    }

    fun updateAutoCheckUpdates(enabled: Boolean) {
        settingsRepository.setAutoCheckUpdatesEnabled(enabled)
        syncStoredUpdateState()
    }

    fun updatePreferredUpdateSource(source: UpdateSource) {
        if (!source.userSelectable) {
            return
        }
        settingsRepository.setPreferredUpdateSource(source)
        syncStoredUpdateState()
    }

    fun checkForUpdatesNow() {
        runUpdateCheck(userInitiated = true)
    }

    fun dismissUpdatePrompt() {
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(updatePromptState = null),
            )
        }
    }

    fun openSteamLoginDialog() {
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = SteamLoginDialogUiState(),
                    ),
                    message = null,
                ),
            )
        }
    }

    fun dismissSteamLoginDialog() {
        steamAuthRepository.cancelPendingSignIn()
        syncSteamAuthState(
            message = null,
            loginDialogState = null,
        )
    }

    fun updateSteamLoginUsername(value: String) {
        _uiState.update { state ->
            val dialog = state.settingsState.steamAuthState.loginDialogState ?: return@update state
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = dialog.copy(
                            username = value,
                            errorMessage = null,
                        ),
                    ),
                    message = null,
                ),
            )
        }
    }

    fun updateSteamLoginPassword(value: String) {
        _uiState.update { state ->
            val dialog = state.settingsState.steamAuthState.loginDialogState ?: return@update state
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = dialog.copy(
                            password = value,
                            errorMessage = null,
                        ),
                    ),
                    message = null,
                ),
            )
        }
    }

    fun updateSteamGuardCode(value: String) {
        _uiState.update { state ->
            val dialog = state.settingsState.steamAuthState.loginDialogState ?: return@update state
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = dialog.copy(
                            guardCode = value,
                            errorMessage = null,
                        ),
                    ),
                    message = null,
                ),
            )
        }
    }

    fun submitSteamLogin() {
        val dialog = _uiState.value.settingsState.steamAuthState.loginDialogState ?: return
        viewModelScope.launch {
            setSteamLoginSubmitting(true)
            val result = runCatching {
                when (dialog.challengeType) {
                    SteamGuardChallengeType.EmailCode,
                    SteamGuardChallengeType.DeviceCode,
                    -> steamAuthRepository.submitPendingGuardCode(dialog.guardCode.trim())

                    SteamGuardChallengeType.DeviceConfirmation,
                    SteamGuardChallengeType.EmailConfirmation,
                    -> steamAuthRepository.waitForPendingConfirmation()

                    else -> steamAuthRepository.beginSignIn(
                        username = dialog.username.trim(),
                        password = dialog.password,
                        replaceAccountId = dialog.targetAccountId,
                    )
                }
            }
            result.onSuccess(::applySteamSignInStep)
                .onFailure { error ->
                    setSteamLoginSubmitting(false, error.message ?: "Steam 登录失败。")
                }
        }
    }

    fun switchToAnonymousSteamAccount() {
        steamAuthRepository.setActiveAccount(null)
        syncSteamAuthState(message = "已切换为匿名浏览。")
    }

    fun setActiveSteamAccount(accountId: String) {
        steamAuthRepository.setActiveAccount(accountId)
        syncSteamAuthState(message = "已切换浏览账号。")
    }

    fun reauthenticateSteamAccount(accountId: String) {
        val account = steamAuthRepository.loadSnapshot().accounts.firstOrNull { it.accountId == accountId } ?: return
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = SteamLoginDialogUiState(
                            mode = SteamLoginDialogMode.Reauthenticate,
                            username = account.accountName,
                            targetAccountId = account.accountId,
                        ),
                    ),
                    message = null,
                ),
            )
        }
    }

    fun removeSteamAccount(accountId: String) {
        if (downloadCenterManager.hasRecoverableTasksForAccount(accountId)) {
            syncSteamAuthState(message = "该账号仍绑定着可恢复的下载任务，暂时不能删除。")
            return
        }
        steamAuthRepository.removeAccount(accountId)
        syncSteamAuthState(message = "已删除 Steam 账号。")
    }

    fun saveDownloadSettings() {
        val settingsState = _uiState.value.settingsState
        val parsedThreadCount = settingsState.downloadThreadCountInput.toIntOrNull()
        val parsedConcurrentTasks = settingsState.concurrentDownloadTaskCountInput.toIntOrNull()

        if (parsedThreadCount == null || parsedConcurrentTasks == null) {
            _uiState.update { state ->
                state.copy(
                    settingsState = state.settingsState.copy(
                        message = "请输入有效的下载设置。",
                    ),
                )
            }
            return
        }

        val clampedThreadCount = parsedThreadCount.coerceIn(
            DownloadSettingsRepository.MIN_DOWNLOAD_THREADS,
            DownloadSettingsRepository.MAX_DOWNLOAD_THREADS,
        )
        val clampedConcurrentTasks = parsedConcurrentTasks.coerceIn(
            DownloadSettingsRepository.MIN_CONCURRENT_DOWNLOAD_TASKS,
            DownloadSettingsRepository.MAX_CONCURRENT_DOWNLOAD_TASKS,
        )

        settingsRepository.setDownloadThreadCount(clampedThreadCount)
        settingsRepository.setConcurrentDownloadTaskCount(clampedConcurrentTasks)
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    downloadThreadCountInput = clampedThreadCount.toString(),
                    savedDownloadThreadCount = clampedThreadCount,
                    concurrentDownloadTaskCountInput = clampedConcurrentTasks.toString(),
                    savedConcurrentDownloadTaskCount = clampedConcurrentTasks,
                    message = "已保存下载设置：线程 $clampedThreadCount，同时任务 $clampedConcurrentTasks",
                ),
            )
        }
    }

    fun updateAddGameSearchQuery(value: String) {
        _uiState.update { state ->
            state.copy(
                addGameState = state.addGameState.copy(
                    searchQuery = value,
                    message = null,
                ),
            )
        }
    }

    fun updateDirectAppId(value: String) {
        _uiState.update { state ->
            state.copy(
                addGameState = state.addGameState.copy(
                    directAppIdText = value.filter(Char::isDigit),
                    message = null,
                ),
            )
        }
    }

    fun searchGames() {
        val query = _uiState.value.addGameState.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    addGameState = state.addGameState.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        searchRequestFailed = false,
                        message = "输入游戏名，或直接填写 GameID。",
                    ),
                )
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                addGameState = state.addGameState.copy(
                    isSearching = true,
                    searchRequestFailed = false,
                    message = null,
                    searchResults = emptyList(),
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    gameRepository.searchWorkshopGames(query)
                }
            }.onSuccess { results ->
                _uiState.update { state ->
                    state.copy(
                        addGameState = state.addGameState.copy(
                            isSearching = false,
                            searchRequestFailed = false,
                            searchResults = results,
                            message = if (results.isEmpty()) "没有找到支持创意工坊的游戏。" else null,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        addGameState = state.addGameState.copy(
                            isSearching = false,
                            searchRequestFailed = true,
                            message = addGameRequestFailureMessage(
                                error = error,
                                fallbackMessage = error.message ?: "搜索游戏失败。",
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun addGameById() {
        val appIdText = _uiState.value.addGameState.directAppIdText
        val appId = appIdText.toUIntOrNull()
        if (appId == null || appId == 0u) {
            _uiState.update { state ->
                state.copy(
                    addGameState = state.addGameState.copy(
                        message = "GameID 必须是正整数。",
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    gameRepository.lookupGame(appId)
                }
            }.onSuccess { game ->
                when {
                    game == null -> showAddGameMessage("没有找到这个游戏。")
                    !game.supportsWorkshop -> showAddGameMessage("这个游戏当前没有公开 Steam 创意工坊。")
                    else -> addGameAndOpen(game)
                }
            }.onFailure { error ->
                showAddGameMessage(
                    addGameRequestFailureMessage(
                        error = error,
                        fallbackMessage = error.message ?: "加载游戏信息失败。",
                    ),
                )
            }
        }
    }

    fun retryFeaturedGames() {
        loadFeaturedGames()
    }

    fun addGameToLibrary(game: SteamGame) {
        viewModelScope.launch {
            addGameAndOpen(game, openAfterAdd = false)
        }
    }

    fun removeGameFromLibrary(game: SteamGame) {
        viewModelScope.launch {
            libraryRepository.removeGame(game.appId)
            _uiState.update { state ->
                val remaining = state.libraryGames.filterNot { it.appId == game.appId }
                state.copy(
                    libraryGames = remaining,
                    libraryMessage = if (remaining.isEmpty()) "游戏库还是空的，点右上角 + 添加支持创意工坊的游戏。" else null,
                )
            }
        }
    }

    fun requestRemoveGame(game: SteamGame) {
        _uiState.update { it.copy(pendingRemoveGame = game) }
    }

    fun confirmRemoveGame() {
        val game = _uiState.value.pendingRemoveGame ?: return
        _uiState.update { it.copy(pendingRemoveGame = null) }
        removeGameFromLibrary(game)
    }

    fun dismissRemoveGameDialog() {
        _uiState.update { it.copy(pendingRemoveGame = null) }
    }

    fun openModDetail(entry: DownloadedModEntry) {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(selectedEntry = entry),
            )
        }
        navigateTo(WorkshopScreenDestination.ModDetail)
    }

    fun requestRemoveMod(entry: DownloadedModEntry) {
        _uiState.update { it.copy(pendingRemoveMod = entry) }
    }

    fun confirmRemoveMod() {
        val entry = _uiState.value.pendingRemoveMod ?: return
        _uiState.update { it.copy(pendingRemoveMod = null) }
        viewModelScope.launch {
            runCatching {
                modLibraryRepository.deleteMod(entry)
            }.onSuccess { entries ->
                downloadCenterManager.clearExportedFilesForMod(entry.appId, entry.publishedFileId)
                _toastMessages.emit("已删除 ${entry.itemTitle} 的本地文件。")
                _uiState.update { state ->
                    applyModLibraryEntries(
                        state = state.copy(pendingRemoveMod = null),
                        entries = entries,
                    )
                }
            }.onFailure { error ->
                _toastMessages.emit(error.message ?: "删除模组失败。")
                refreshModLibrary(showLoading = false)
            }
        }
    }

    fun dismissRemoveModDialog() {
        _uiState.update { it.copy(pendingRemoveMod = null) }
    }

    fun openGameWorkshop(game: SteamGame) {
        navigateTo(WorkshopScreenDestination.GameWorkshop)
        _uiState.update { state ->
            state.copy(
                gameWorkshopState = GameWorkshopUiState(
                    game = game,
                    isLoading = true,
                ),
                workshopItemDetailState = null,
            )
        }
        loadWorkshopPage(
            game = game,
            searchQuery = "",
            sortOption = WorkshopBrowseSortOption.MostPopular,
            timeWindow = WorkshopBrowseTimeWindow.OneWeek,
            page = 1,
            append = false,
        )
    }

    fun openWorkshopItemDetail(item: WorkshopBrowseItem) {
        _uiState.update { state ->
            state.copy(
                workshopItemDetailState = WorkshopItemDetailUiState(
                    item = item,
                    isLoading = true,
                    showConnectionErrorState = false,
                ),
            )
        }
        navigateTo(WorkshopScreenDestination.WorkshopItemDetail)

        viewModelScope.launch {
            runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    detailRepository.loadWorkshopItemDetail(item)
                }
            }.onSuccess { detail ->
                _uiState.update { state ->
                    val current = state.workshopItemDetailState ?: return@update state
                    state.copy(
                        workshopItemDetailState = current.copy(
                            detail = detail,
                            isLoading = false,
                            message = null,
                            showConnectionErrorState = false,
                        ),
                    )
                }
            }.onFailure { error ->
                val showConnectionErrorState = error.isWorkshopConnectionFailure()
                _uiState.update { state ->
                    val current = state.workshopItemDetailState ?: return@update state
                    state.copy(
                        workshopItemDetailState = current.copy(
                            isLoading = false,
                            message = workshopRequestFailureMessage(
                                error = error,
                                fallbackMessage = error.message ?: "加载模组详情失败。",
                            ),
                            showConnectionErrorState = showConnectionErrorState,
                        ),
                    )
                }
            }
        }
    }

    fun retryWorkshopItemDetail() {
        val item = _uiState.value.workshopItemDetailState?.item ?: return
        openWorkshopItemDetail(item)
    }

    fun translateWorkshopItemDescription() {
        val detailState = _uiState.value.workshopItemDetailState ?: return
        if (detailState.isLoading || detailState.isTranslatingDescription) {
            return
        }

        val description = detailState.detail?.description?.trim().orEmpty()
        if (description.isBlank()) {
            viewModelScope.launch {
                _toastMessages.emit("当前没有可翻译的描述。")
            }
            return
        }

        val targetAppId = detailState.item.appId
        val targetPublishedFileId = detailState.item.publishedFileId
        _uiState.update { state ->
            val current = state.workshopItemDetailState ?: return@update state
            if (current.item.appId != targetAppId || current.item.publishedFileId != targetPublishedFileId) {
                return@update state
            }
            state.copy(
                workshopItemDetailState = current.copy(
                    isTranslatingDescription = true,
                    translationErrorMessage = null,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                descriptionTranslator.translateDescription(description)
            }.onSuccess { translatedDescription ->
                _uiState.update { state ->
                    val current = state.workshopItemDetailState ?: return@update state
                    if (current.item.appId != targetAppId || current.item.publishedFileId != targetPublishedFileId) {
                        return@update state
                    }
                    state.copy(
                        workshopItemDetailState = current.copy(
                            isTranslatingDescription = false,
                            translatedDescription = translatedDescription,
                            translationErrorMessage = null,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    val current = state.workshopItemDetailState ?: return@update state
                    if (current.item.appId != targetAppId || current.item.publishedFileId != targetPublishedFileId) {
                        return@update state
                    }
                    state.copy(
                        workshopItemDetailState = current.copy(
                            isTranslatingDescription = false,
                            translationErrorMessage = error.message ?: "翻译描述失败，请稍后重试。",
                        ),
                    )
                }
            }
        }
    }

    fun updateWorkshopSearchQuery(value: String) {
        _uiState.update { state ->
            state.copy(
                gameWorkshopState = state.gameWorkshopState?.copy(
                    searchQuery = value,
                    message = null,
                    showConnectionErrorState = false,
                    retryLoadMoreOnError = false,
                ),
            )
        }
    }

    fun updateWorkshopSort(sortOption: WorkshopBrowseSortOption) {
        val workshopState = _uiState.value.gameWorkshopState ?: return
        if (workshopState.selectedSortOption == sortOption) {
            return
        }
        loadWorkshopPage(
            game = workshopState.game,
            searchQuery = workshopState.searchQuery.trim(),
            sortOption = sortOption,
            timeWindow = workshopState.selectedTimeWindow,
            page = 1,
            append = false,
        )
    }

    fun updateWorkshopTimeWindow(timeWindow: WorkshopBrowseTimeWindow) {
        val workshopState = _uiState.value.gameWorkshopState ?: return
        if (!workshopState.selectedSortOption.supportsTimeWindow || workshopState.selectedTimeWindow == timeWindow) {
            return
        }
        loadWorkshopPage(
            game = workshopState.game,
            searchQuery = workshopState.searchQuery.trim(),
            sortOption = workshopState.selectedSortOption,
            timeWindow = timeWindow,
            page = 1,
            append = false,
        )
    }

    fun searchCurrentGameWorkshop() {
        val workshopState = _uiState.value.gameWorkshopState ?: return
        loadWorkshopPage(
            game = workshopState.game,
            searchQuery = workshopState.searchQuery.trim(),
            sortOption = workshopState.selectedSortOption,
            timeWindow = workshopState.selectedTimeWindow,
            page = 1,
            append = false,
        )
    }

    fun loadMoreWorkshopItems() {
        val workshopState = _uiState.value.gameWorkshopState ?: return
        if (!workshopState.hasNextPage || workshopState.isLoadingMore || workshopState.isLoading) {
            return
        }

        loadWorkshopPage(
            game = workshopState.game,
            searchQuery = workshopState.searchQuery.trim(),
            sortOption = workshopState.selectedSortOption,
            timeWindow = workshopState.selectedTimeWindow,
            page = workshopState.page + 1,
            append = true,
        )
    }

    fun downloadSingleItem(item: WorkshopBrowseItem) {
        enqueueWorkshopItems(
            appId = item.appId,
            gameTitle = _uiState.value.gameWorkshopState?.game?.name ?: "Workshop",
            items = listOf(item),
        )
    }

    fun applyAdbCommand(command: AdbDownloadCommand) {
        Log.i(
            WorkshopAppContract.logTag,
            "ADB command received appId=${command.appIdText} publishedFileId=${command.publishedFileIdText} autoStart=${command.autoStart}",
        )

        if (!command.autoStart) {
            return
        }

        val validationError = WorkshopInputValidator.validate(command.appIdText, command.publishedFileIdText)
        if (validationError != null) {
            Log.w(WorkshopAppContract.logTag, "ADB command rejected: $validationError")
            return
        }

        val appId = command.appIdText.toUInt()
        val publishedFileId = command.publishedFileIdText.toULong()
        val downloadBinding = steamAuthRepository.currentDownloadBinding()
        val enqueued = downloadCenterManager.enqueueDownloads(
            appId = appId,
            gameTitle = "ADB",
            targets = listOf(
                DownloadCenterManager.QueueTarget(
                    publishedFileId = publishedFileId,
                    itemTitle = "Workshop $publishedFileId",
                    boundAccountId = downloadBinding.accountId,
                    boundAccountName = downloadBinding.accountName,
                ),
            ),
        )
        Log.i(
            WorkshopAppContract.logTag,
            "ADB download task enqueued count=$enqueued appId=$appId publishedFileId=$publishedFileId",
        )
    }

    private fun refreshLibrary() {
        _uiState.update {
            it.copy(
                isLibraryLoading = true,
                libraryMessage = null,
                libraryError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                val appIds = libraryRepository.loadGameIds()
                if (appIds.isEmpty()) {
                    emptyList()
                } else {
                    val cachedGamesById = libraryRepository.loadGames().associateBy(SteamGame::appId)
                    val missingIds = appIds.filterNot(cachedGamesById::containsKey)
                    if (missingIds.isEmpty()) {
                        appIds.mapNotNull(cachedGamesById::get)
                    } else {
                        val loadedGamesById = withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                            gameRepository.lookupGamesByIds(missingIds).associateBy(SteamGame::appId)
                        }
                        appIds.mapNotNull { appId ->
                            cachedGamesById[appId] ?: loadedGamesById[appId]
                        }.also { mergedGames ->
                            libraryRepository.replaceGames(mergedGames)
                        }
                    }
                }
            }.onSuccess { games ->
                _uiState.update {
                    it.copy(
                        libraryGames = games,
                        isLibraryLoading = false,
                        libraryError = null,
                        libraryMessage = if (games.isEmpty()) {
                            "游戏库还是空的，点右上角 + 添加支持创意工坊的游戏。"
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { error ->
                val currentGames = _uiState.value.libraryGames
                if (error is SocketTimeoutException || error is kotlinx.coroutines.TimeoutCancellationException) {
                    _uiState.update {
                        it.copy(
                            isLibraryLoading = false,
                            libraryError = if (currentGames.isEmpty()) {
                                LibraryErrorUiState(
                                    reason = "加载游戏库超时。",
                                    showAcceleratorHint = true,
                                )
                            } else {
                                null
                            },
                            libraryMessage = if (currentGames.isEmpty()) {
                                null
                            } else {
                                "啊哦，加载超时，您的网络环境可能不支持直连创意工坊，请开启加速器加速 steam 或科学上网后重试。"
                            },
                        )
                    }
                    return@onFailure
                }

                _uiState.update {
                    it.copy(
                        isLibraryLoading = false,
                        libraryError = if (currentGames.isEmpty()) {
                            LibraryErrorUiState(
                                reason = error.message ?: "加载游戏库失败。",
                                showAcceleratorHint = true,
                            )
                        } else {
                            null
                        },
                        libraryMessage = if (currentGames.isEmpty()) {
                            null
                        } else {
                            error.message ?: "加载游戏库失败。"
                        },
                    )
                }
            }
        }
    }

    private fun refreshModLibrary(showLoading: Boolean = true) {
        if (showLoading) {
            _uiState.update { state ->
                state.copy(
                    modLibraryState = state.modLibraryState.copy(
                        isLoading = true,
                        errorMessage = null,
                    ),
                )
            }
        }

        viewModelScope.launch {
            runCatching {
                modLibraryRepository.syncWithLocalStorage()
            }.onSuccess { entries ->
                _uiState.update { state ->
                    applyModLibraryEntries(
                        state = state,
                        entries = entries,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        modLibraryState = state.modLibraryState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "同步模组库失败。",
                            message = if (state.modLibraryState.items.isEmpty()) null else state.modLibraryState.message,
                        ),
                    )
                }
            }
        }
    }

    private fun loadFeaturedGames() {
        _uiState.update { state ->
            state.copy(
                addGameState = state.addGameState.copy(
                    isLoadingFeatured = true,
                    featuredErrorMessage = null,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    gameRepository.loadFeaturedWorkshopGames()
                }
            }.onSuccess { games ->
                _uiState.update { state ->
                    state.copy(
                        addGameState = state.addGameState.copy(
                            featuredGames = games.filter(SteamGame::supportsWorkshop),
                            isLoadingFeatured = false,
                            featuredErrorMessage = null,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        addGameState = state.addGameState.copy(
                            isLoadingFeatured = false,
                            featuredErrorMessage = addGameRequestFailureMessage(
                                error = error,
                                fallbackMessage = error.message ?: "加载热门工坊游戏失败。",
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun loadWorkshopPage(
        game: SteamGame,
        searchQuery: String,
        sortOption: WorkshopBrowseSortOption,
        timeWindow: WorkshopBrowseTimeWindow,
        page: Int,
        append: Boolean,
    ) {
        _uiState.update { state ->
            val current = state.gameWorkshopState ?: GameWorkshopUiState(game = game)
            val shouldKeepItemsWhileRefreshing =
                !append &&
                    current.game.appId == game.appId &&
                    current.items.isNotEmpty() &&
                    current.selectedSortOption == sortOption &&
                    current.selectedTimeWindow == timeWindow &&
                    current.searchQuery.trim() == searchQuery
            state.copy(
                gameWorkshopState = current.copy(
                    game = game,
                    selectedSortOption = sortOption,
                    selectedTimeWindow = timeWindow,
                    isLoading = !append,
                    isLoadingMore = append,
                    items = if (append || shouldKeepItemsWhileRefreshing) current.items else emptyList(),
                    message = null,
                    showConnectionErrorState = false,
                    retryLoadMoreOnError = false,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    browseRepository.browseGameWorkshop(
                        appId = game.appId,
                        searchQuery = searchQuery,
                        sortOption = sortOption,
                        timeWindow = timeWindow,
                        page = page,
                    )
                }
            }.onSuccess { result ->
                _uiState.update { state ->
                    val current = state.gameWorkshopState ?: return@update state
                    val nextItems = if (append) {
                        (current.items + result.items).distinctBy(WorkshopBrowseItem::publishedFileId)
                    } else {
                        result.items
                    }
                    state.copy(
                        gameWorkshopState = current.copy(
                            items = nextItems,
                            isLoading = false,
                            isLoadingMore = false,
                            hasNextPage = result.hasNextPage,
                            page = result.page,
                            message = if (nextItems.isEmpty()) "这个游戏的当前筛选结果里没有模组。" else null,
                            showConnectionErrorState = false,
                            retryLoadMoreOnError = false,
                        ),
                    )
                }
            }.onFailure { error ->
                val showConnectionErrorState = error.isWorkshopConnectionFailure()
                _uiState.update { state ->
                    val current = state.gameWorkshopState ?: return@update state
                    state.copy(
                        gameWorkshopState = current.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            message = workshopRequestFailureMessage(
                                error = error,
                                fallbackMessage = error.message ?: "加载创意工坊失败。",
                            ),
                            showConnectionErrorState = showConnectionErrorState,
                            retryLoadMoreOnError = append && showConnectionErrorState,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun addGameAndOpen(
        game: SteamGame,
        openAfterAdd: Boolean = true,
    ) {
        libraryRepository.addGame(game)
        _toastMessages.emit("已添加 ${game.name}。")
        _uiState.update { state ->
            val updatedLibrary = (state.libraryGames + game).distinctBy(SteamGame::appId)
            state.copy(
                libraryGames = updatedLibrary,
                isLibraryLoading = false,
                libraryMessage = null,
                addGameState = state.addGameState.copy(
                    message = null,
                ),
            )
        }

        if (openAfterAdd) {
            openGameWorkshop(game)
        }
    }

    private fun enqueueWorkshopItems(
        appId: UInt,
        gameTitle: String,
        items: List<WorkshopBrowseItem>,
    ) {
        if (steamAuthRepository.activeAccountRequiresReauthentication()) {
            viewModelScope.launch {
                _toastMessages.emit("当前 Steam 账号需要重新认证，新的下载任务暂时不能开始。")
            }
            return
        }
        val downloadBinding = steamAuthRepository.currentDownloadBinding()
        val enqueuedCount = downloadCenterManager.enqueueDownloads(
            appId = appId,
            gameTitle = gameTitle,
            targets = items.map { item ->
                DownloadCenterManager.QueueTarget(
                    publishedFileId = item.publishedFileId,
                    itemTitle = item.title,
                    boundAccountId = downloadBinding.accountId,
                    boundAccountName = downloadBinding.accountName,
                )
            },
        )

        if (enqueuedCount <= 0) {
            return
        }

        viewModelScope.launch {
            _toastMessages.emit(
                if (enqueuedCount == 1) {
                    "已开始下载，可在下载中心查看进度。"
                } else {
                    "已开始 $enqueuedCount 个下载任务，可在下载中心查看进度。"
                },
            )
        }
    }

    private fun showAddGameMessage(message: String) {
        _uiState.update { state ->
            state.copy(
                addGameState = state.addGameState.copy(
                    message = message,
                    isSearching = false,
                    isLoadingFeatured = false,
                ),
            )
        }
    }

    private fun addGameRequestFailureMessage(
        error: Throwable,
        fallbackMessage: String,
    ): String =
        if (error.isTimeoutRequestFailure()) {
            REQUEST_TIMEOUT_MESSAGE
        } else {
            fallbackMessage
        }

    private fun workshopRequestFailureMessage(
        error: Throwable,
        fallbackMessage: String,
    ): String =
        if (error.isWorkshopConnectionFailure()) {
            WORKSHOP_CONNECTION_FAILURE_MESSAGE
        } else {
            fallbackMessage
        }

    private fun maybeStartAutoUpdateCheck() {
        if (!settingsRepository.isAutoCheckUpdatesEnabled()) {
            return
        }
        runUpdateCheck(userInitiated = false)
    }

    private fun runUpdateCheck(userInitiated: Boolean) {
        if (_uiState.value.settingsState.updateCheckInProgress) {
            return
        }

        syncStoredUpdateState(updateCheckInProgress = true)
        viewModelScope.launch {
            val preferredSource = settingsRepository.getPreferredUpdateSource()
            val result = runCatching {
                updateService.checkForUpdates(
                    currentVersion = BuildConfig.VERSION_NAME,
                    preferredUserSource = preferredSource,
                )
            }.getOrElse { error ->
                UpdateCheckExecutionResult.Failure(
                    errorSummary = error.message ?: "检查更新失败。",
                )
            }

            val toastMessage = when (result) {
                is UpdateCheckExecutionResult.Success -> handleUpdateCheckSuccess(result, userInitiated)
                is UpdateCheckExecutionResult.Failure -> handleUpdateCheckFailure(result, userInitiated)
            }
            if (!toastMessage.isNullOrBlank()) {
                _toastMessages.emit(toastMessage)
            }
        }
    }

    private fun handleUpdateCheckSuccess(
        result: UpdateCheckExecutionResult.Success,
        userInitiated: Boolean,
    ): String? {
        val decision = WorkshopUpdateUiReducer.reduce(result, userInitiated)
        settingsRepository.setLastUpdateCheckAtMs(System.currentTimeMillis())
        settingsRepository.setLastKnownRemoteTag(result.release.normalizedVersion)
        settingsRepository.setLastSuccessfulMetadataSourceId(result.metadataSource.id)
        settingsRepository.setLastUpdateErrorSummary(null)
        if (result.downloadResolution != null) {
            settingsRepository.setLastSuccessfulDownloadSourceId(result.downloadResolution.source.id)
        }

        val promptState = if (decision.showPrompt) {
            buildUpdatePromptState(result.release, result.downloadResolution)
        } else {
            null
        }
        syncStoredUpdateState(
            updateCheckInProgress = false,
            updatePromptState = promptState,
        )

        return when (decision.message) {
            UpdateUiMessage.LATEST -> "当前已是最新版本。"
            UpdateUiMessage.FAILURE -> "检查更新失败。"
            null -> null
        }
    }

    private fun handleUpdateCheckFailure(
        result: UpdateCheckExecutionResult.Failure,
        userInitiated: Boolean,
    ): String? {
        val decision = WorkshopUpdateUiReducer.reduce(result, userInitiated)
        settingsRepository.setLastUpdateCheckAtMs(System.currentTimeMillis())
        settingsRepository.setLastUpdateErrorSummary(result.errorSummary)
        result.release?.let { release ->
            settingsRepository.setLastKnownRemoteTag(release.normalizedVersion)
        }
        result.metadataSource?.let { source ->
            settingsRepository.setLastSuccessfulMetadataSourceId(source.id)
        }
        syncStoredUpdateState(
            updateCheckInProgress = false,
            updatePromptState = null,
        )

        return when (decision.message) {
            UpdateUiMessage.FAILURE -> "检查更新失败：${result.errorSummary}"
            UpdateUiMessage.LATEST -> "当前已是最新版本。"
            null -> null
        }
    }

    private fun buildUpdatePromptState(
        release: UpdateReleaseInfo,
        downloadResolution: UpdateDownloadResolution?,
    ): UpdatePromptState? {
        val resolvedDownload = downloadResolution ?: return null
        return UpdatePromptState(
            currentVersion = BuildConfig.VERSION_NAME,
            latestVersion = release.normalizedVersion,
            publishedAtText = release.publishedAtDisplayText.ifBlank { "未知" },
            downloadSourceDisplayName = resolvedDownload.source.displayName,
            notesText = release.notesText.ifBlank { "暂无更新说明。" },
            downloadUrl = resolvedDownload.resolvedUrl,
        )
    }

    private fun syncStoredUpdateState(
        updateCheckInProgress: Boolean = _uiState.value.settingsState.updateCheckInProgress,
        updatePromptState: UpdatePromptState? = _uiState.value.settingsState.updatePromptState,
    ) {
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    autoCheckUpdatesEnabled = settingsRepository.isAutoCheckUpdatesEnabled(),
                    preferredUpdateSource = settingsRepository.getPreferredUpdateSource(),
                    availableUpdateSources = UpdateSource.userSelectableSources(),
                    currentVersionText = BuildConfig.VERSION_NAME,
                    updateStatusSummary = buildUpdateStatusSummary(),
                    updateCheckInProgress = updateCheckInProgress,
                    updatePromptState = updatePromptState,
                ),
            )
        }
    }

    private fun syncSteamAuthState(
        message: String? = _uiState.value.settingsState.message,
        loginDialogState: SteamLoginDialogUiState? = _uiState.value.settingsState.steamAuthState.loginDialogState,
    ) {
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = steamAuthRepository.loadSnapshot().toUiState(loginDialogState = loginDialogState),
                    message = message,
                ),
            )
        }
    }

    private fun setSteamLoginSubmitting(
        submitting: Boolean,
        errorMessage: String? = null,
    ) {
        _uiState.update { state ->
            val dialog = state.settingsState.steamAuthState.loginDialogState ?: return@update state
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = dialog.copy(
                            isSubmitting = submitting,
                            errorMessage = errorMessage,
                        ),
                    ),
                ),
            )
        }
    }

    private fun applySteamSignInStep(step: SteamSignInStep) {
        when (step) {
            is SteamSignInStep.RequiresGuardCode -> {
                _uiState.update { state ->
                    val dialog = state.settingsState.steamAuthState.loginDialogState ?: SteamLoginDialogUiState()
                    state.copy(
                        settingsState = state.settingsState.copy(
                            steamAuthState = state.settingsState.steamAuthState.copy(
                                loginDialogState = dialog.copy(
                                    password = "",
                                    challengeType = step.challenge.type,
                                    challengeMessage = step.challenge.message,
                                    isSubmitting = false,
                                    errorMessage = null,
                                ),
                            ),
                        ),
                    )
                }
            }

            is SteamSignInStep.AwaitingConfirmation -> {
                _uiState.update { state ->
                    val dialog = state.settingsState.steamAuthState.loginDialogState ?: SteamLoginDialogUiState()
                    state.copy(
                        settingsState = state.settingsState.copy(
                            steamAuthState = state.settingsState.steamAuthState.copy(
                                loginDialogState = dialog.copy(
                                    password = "",
                                    challengeType = step.challenge.type,
                                    challengeMessage = step.challenge.message,
                                    isSubmitting = false,
                                    errorMessage = null,
                                ),
                            ),
                        ),
                    )
                }
                viewModelScope.launch {
                    setSteamLoginSubmitting(true)
                    runCatching { steamAuthRepository.waitForPendingConfirmation() }
                        .onSuccess(::applySteamSignInStep)
                        .onFailure { error ->
                            setSteamLoginSubmitting(false, error.message ?: "Steam 登录失败。")
                        }
                }
            }

            is SteamSignInStep.Success -> {
                syncSteamAuthState(
                    message = "已登录 ${step.account.accountName}。",
                    loginDialogState = null,
                )
            }
        }
    }

    private fun buildUpdateStatusSummary(): String {
        val lastCheckedAtMs = settingsRepository.getLastUpdateCheckAtMs()
        if (lastCheckedAtMs <= 0L) {
            return "尚未执行过更新检查。"
        }

        val lines = mutableListOf<String>()
        lines += "最近检查：${formatUpdateCheckTime(lastCheckedAtMs)}"

        val remoteTag = settingsRepository.getLastKnownRemoteTag()
        if (!remoteTag.isNullOrBlank()) {
            lines += "远端版本：$remoteTag"
        }

        val metadataSource = resolveUpdateSourceDisplayName(settingsRepository.getLastSuccessfulMetadataSourceId())
        if (metadataSource != null) {
            lines += "元数据来源：$metadataSource"
        }

        val errorSummary = settingsRepository.getLastUpdateErrorSummary()
        if (!errorSummary.isNullOrBlank()) {
            lines += "结果：检查失败"
            lines += errorSummary
            return lines.joinToString("\n")
        }

        val hasUpdate = !remoteTag.isNullOrBlank() &&
            WorkshopUpdateVersioning.isRemoteNewer(BuildConfig.VERSION_NAME, remoteTag)
        lines += if (hasUpdate) {
            "结果：发现新版本"
        } else {
            "结果：当前已是最新版本"
        }

        if (hasUpdate) {
            val downloadSource = resolveUpdateSourceDisplayName(settingsRepository.getLastSuccessfulDownloadSourceId())
            if (downloadSource != null) {
                lines += "下载来源：$downloadSource"
            }
        }

        return lines.joinToString("\n")
    }

    private fun resolveUpdateSourceDisplayName(sourceId: String?): String? =
        UpdateSource.fromPersistedValue(sourceId)?.displayName

    private fun formatUpdateCheckTime(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMs))

    private fun navigateTo(
        screen: WorkshopScreenDestination,
        rememberPrevious: Boolean = true,
    ) {
        _uiState.update { state ->
            state.copy(
                previousScreen = if (
                    rememberPrevious &&
                    state.currentScreen != screen &&
                    state.currentScreen != WorkshopScreenDestination.DownloadCenter
                ) {
                    state.currentScreen
                } else {
                    state.previousScreen
                },
                currentScreen = screen,
                selectedDownloadTaskId = if (screen == WorkshopScreenDestination.DownloadTaskDetail) {
                    state.selectedDownloadTaskId
                } else if (
                    state.currentScreen == WorkshopScreenDestination.DownloadTaskDetail &&
                    screen != WorkshopScreenDestination.DownloadCenter
                ) {
                    null
                } else {
                    state.selectedDownloadTaskId
                },
            )
        }
    }

    private fun applyModLibraryEntries(
        state: WorkshopUiState,
        entries: List<DownloadedModEntry>,
        isLoading: Boolean = false,
        errorMessage: String? = null,
    ): WorkshopUiState {
        val selectedEntry = state.modLibraryState.selectedEntry?.let { current ->
            entries.firstOrNull { it.matches(current.appId, current.publishedFileId) }
        }
        val nextScreen = if (state.currentScreen == WorkshopScreenDestination.ModDetail && selectedEntry == null) {
            WorkshopScreenDestination.ModLibrary
        } else {
            state.currentScreen
        }
        val updateCheckState = state.modLibraryState.updateCheckState.filterForEntries(entries)
        return state.copy(
            currentScreen = nextScreen,
            modLibraryState = state.modLibraryState.copy(
                items = entries,
                selectedEntry = selectedEntry,
                updateCheckState = updateCheckState,
                isLoading = isLoading,
                errorMessage = errorMessage,
                message = if (entries.isEmpty()) {
                    "模组库还是空的，下载一个模组后会自动同步到这里。"
                } else {
                    null
                },
            ),
        )
    }

    private fun buildModLibrarySyncSignature(downloadCenterState: DownloadCenterUiState): String =
        downloadCenterState.tasks
            .filter { it.status == DownloadCenterTaskStatus.Success }
            .sortedBy(DownloadCenterTaskUiState::id)
            .joinToString("|") { task ->
                buildString {
                    append(task.id)
                    append(":")
                    append(task.appId)
                    append(":")
                    append(task.publishedFileId)
                    append(":")
                    append(task.files.joinToString(",") { file -> "${file.contentUri}#${file.userVisiblePath}" })
                }
            }

    override fun onCleared() {
        descriptionTranslator.close()
        super.onCleared()
    }

    companion object {
        private const val MAIN_SCREEN_TIMEOUT_MS = 8_000L
        private const val REQUEST_TIMEOUT_MESSAGE = "加载超时，请开启加速器或科学上网后重试。"
        private const val WORKSHOP_CONNECTION_FAILURE_MESSAGE =
            "啊哦，加载超时，您的网络环境可能不支持直连创意工坊，请开启加速器加速 steam 或科学上网后重试。"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkshopViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }

    private fun createInitialUiState(): WorkshopUiState {
        val themeMode = settingsRepository.getThemeMode()
        val threadCount = settingsRepository.getDownloadThreadCount()
        val concurrentTaskCount = settingsRepository.getConcurrentDownloadTaskCount()
        return WorkshopUiState(
            themeMode = themeMode,
            modLibraryState = ModLibraryUiState(
                isLoading = true,
                displayMode = settingsRepository.getModLibraryDisplayMode(),
            ),
            settingsState = SettingsUiState(
                downloadThreadCountInput = threadCount.toString(),
                savedDownloadThreadCount = threadCount,
                concurrentDownloadTaskCountInput = concurrentTaskCount.toString(),
                savedConcurrentDownloadTaskCount = concurrentTaskCount,
                selectedThemeMode = themeMode,
                steamAuthState = steamAuthRepository.loadSnapshot().toUiState(),
                autoCheckUpdatesEnabled = settingsRepository.isAutoCheckUpdatesEnabled(),
                preferredUpdateSource = settingsRepository.getPreferredUpdateSource(),
                availableUpdateSources = UpdateSource.userSelectableSources(),
                currentVersionText = BuildConfig.VERSION_NAME,
                updateStatusSummary = buildUpdateStatusSummary(),
            ),
        )
    }
}

private fun Throwable.isTimeoutRequestFailure(): Boolean =
    this is SocketTimeoutException || this is TimeoutCancellationException

private fun Throwable.isWorkshopConnectionFailure(): Boolean =
    this is IOException || this is TimeoutCancellationException

private fun DownloadedModEntry.toWorkshopBrowseItem(): WorkshopBrowseItem =
    WorkshopBrowseItem(
        appId = appId,
        publishedFileId = publishedFileId,
        title = itemTitle,
        authorName = "",
        previewImageUrl = "",
        descriptionSnippet = "",
    )























