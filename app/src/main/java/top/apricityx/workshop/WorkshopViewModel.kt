package top.apricityx.workshop

import android.app.Application
import android.app.KeyguardManager
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import top.apricityx.workshop.steam.protocol.DEFAULT_HTTP_TIMEOUT_SECONDS
import top.apricityx.workshop.steam.protocol.applyDefaultHttpTimeouts
import top.apricityx.workshop.steam.protocol.applySteamHttpCompatibility
import top.apricityx.workshop.steam.protocol.SteamPublishedFileQuery
import top.apricityx.workshop.steam.protocol.STEAM_PUBLISHED_FILE_QUERY_TYPE_RANKED_BY_TEXT_SEARCH
import top.apricityx.workshop.steam.protocol.SteamGuardChallengeType
import top.apricityx.workshop.data.GameLibraryRepository
import top.apricityx.workshop.data.SteamGame
import top.apricityx.workshop.data.SteamGameRepository
import top.apricityx.workshop.data.WorkshopBrowseItem
import top.apricityx.workshop.data.WorkshopBrowseRepository
import top.apricityx.workshop.data.WorkshopDetailRepository
import top.apricityx.workshop.data.WorkshopRequiredItem
import top.apricityx.workshop.data.toWorkshopBrowsePage
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
    private val baiduTranslationCredentialsRepository = BaiduTranslationCredentialsRepository(application)
    private val settingsRepository = DownloadSettingsRepository(application)
    private val steamWebCookieJar = SteamWebSessionCookieJar(
        projectedCookiesProvider = { url ->
            steamAuthRepository.blockingProjectedCookiesFor(
                url = url,
                accountId = steamAuthRepository.activeAccountId(),
            )
        },
        sessionScopeProvider = steamAuthRepository::activeAccountId,
    )
    private val experimentalWorkshopDirectAccessRuntime =
        createExperimentalWorkshopDirectAccessRuntime(application.filesDir)
    private val experimentalGithubDirectAccessRuntime =
        createExperimentalGithubDirectAccessRuntime(application.filesDir)
    private val httpClient = OkHttpClient.Builder()
        .applyDefaultHttpTimeouts()
        .applySteamHttpCompatibility()
        .applyAppNetworkLogging("workshop-web")
        .cookieJar(steamWebCookieJar)
        .hostnameVerifier(experimentalWorkshopDirectAccessRuntime.hostnameVerifier)
        .addInterceptor(
            SteamAuthenticatedCleartextInterceptor(
                hasAuthenticatedSteamSession = {
                    steamAuthRepository.activeAccountId()
                        ?.let(steamAuthRepository::accountSessionFor) != null
                },
                allowAuthenticatedCleartextHttpProvider = settingsRepository::isSteamAuthenticatedCleartextHttpAllowed,
            ),
        )
        .addInterceptor(SteamLanguageInterceptor(settingsRepository::getSteamLanguagePreference))
        .addExperimentalWorkshopDirectAccess(
            runtime = experimentalWorkshopDirectAccessRuntime,
            enabledProvider = {
                ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessAllowed(
                    settingsRepository.isExperimentalWorkshopDirectAccessEnabled(),
                )
            },
            steamCookieJar = steamWebCookieJar,
            fallbackNoticeSink = ExperimentalWorkshopDirectAccessFallbackNotifier,
        )
        .build()
    private val gameRepository = SteamGameRepository(
        client = httpClient,
        languagePreferenceProvider = settingsRepository::getSteamLanguagePreference,
    )
    private val browseRepository = WorkshopBrowseRepository(
        client = httpClient,
        languagePreferenceProvider = settingsRepository::getSteamLanguagePreference,
    )
    private val detailRepository = WorkshopDetailRepository(
        client = httpClient,
        languagePreferenceProvider = settingsRepository::getSteamLanguagePreference,
    )
    private val libraryRepository = GameLibraryRepository(application)
    private val modLibraryRepository = ModLibraryRepository(application)
    private val modLibraryUpdateStateStore =
        ModLibraryUpdateStateStore(File(application.filesDir, "mod-library/update-state.json"))
    private val downloadCenterManager = DownloadCenterManager.getInstance(application)
    private val updateService = WorkshopUpdateService(
        baseClient = httpClient,
        directAccessRuntime = experimentalGithubDirectAccessRuntime,
    )
    private val baiduAiTextTranslationClient = BaiduAiTextTranslationClient()

    private val _uiState = MutableStateFlow(createInitialUiState())
    val uiState: StateFlow<WorkshopUiState> = _uiState.asStateFlow()
    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessages = _toastMessages.asSharedFlow()

    private var lastDownloadCenterModSignature: String? = null
    private var steamConfirmationWaitJob: Job? = null
    private var activeSteamLoginAttemptId: String? = null
    @Volatile
    private var isSteamWebSessionPrimed = false
    @Volatile
    private var primedSteamWebSessionAccountId: String? = null

    init {
        refreshLibrary()
        refreshModLibrary()
        loadFeaturedGames()
        maybeStartAutoUpdateCheck()
        if (downloadCenterManager.uiState.value.activeCount > 0) {
            DownloadForegroundService.start(application)
        }
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
        viewModelScope.launch {
            ExperimentalWorkshopDirectAccessFallbackNotifier.events.collect {
                _uiState.update { state ->
                    if (state.steamDirectAccessFallbackDialogState != null) {
                        state
                    } else {
                        state.copy(
                            steamDirectAccessFallbackDialogState = SteamDirectAccessFallbackDialogUiState(
                                message = STEAM_DIRECT_ACCESS_FALLBACK_DIALOG_MESSAGE,
                            ),
                        )
                    }
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

            WorkshopScreenDestination.WorkshopItemDetail -> {
                val previousDetailState = state.workshopItemDetailBackStack.lastOrNull()
                if (previousDetailState != null) {
                    _uiState.update { current ->
                        current.copy(
                            workshopItemDetailState = previousDetailState,
                            workshopItemDetailBackStack = current.workshopItemDetailBackStack.dropLast(1),
                        )
                    }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            workshopItemDetailState = null,
                            workshopItemDetailBackStack = emptyList(),
                        )
                    }
                    navigateTo(WorkshopScreenDestination.GameWorkshop, rememberPrevious = false)
                }
            }

            WorkshopScreenDestination.ModDetail ->
                navigateTo(WorkshopScreenDestination.ModLibrary, rememberPrevious = false)

            WorkshopScreenDestination.DownloadCenter,
            WorkshopScreenDestination.Settings,
            -> navigateTo(state.previousScreen, rememberPrevious = false)

            WorkshopScreenDestination.BaiduTranslationApiKey ->
                navigateTo(WorkshopScreenDestination.Settings, rememberPrevious = false)

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
        val currentModUpdateConcurrentChecks = settingsRepository.getModUpdateConcurrentCheckCount()
        val currentThemeMode = settingsRepository.getThemeMode()
        val currentFrontendMode = settingsRepository.getFrontendMode()
        val currentSteamLanguagePreference = settingsRepository.getSteamLanguagePreference()
        val allowSteamAuthenticatedCleartextHttp = settingsRepository.isSteamAuthenticatedCleartextHttpAllowed()
        val experimentalWorkshopDirectAccessEnabled =
            settingsRepository.isExperimentalWorkshopDirectAccessEnabled()
        val application = getApplication<Application>()
        val currentSteamAuthState = steamAuthRepository.loadSnapshot().toUiState(
            loginDialogState = _uiState.value.settingsState.steamAuthState.loginDialogState,
        )
        _uiState.update { state ->
            state.copy(
                themeMode = currentThemeMode,
                frontendMode = currentFrontendMode,
                settingsState = state.settingsState.copy(
                    downloadThreadCountInput = currentThreads.toString(),
                    savedDownloadThreadCount = currentThreads,
                    concurrentDownloadTaskCountInput = currentConcurrentTasks.toString(),
                    savedConcurrentDownloadTaskCount = currentConcurrentTasks,
                    modUpdateConcurrentCheckCountInput = currentModUpdateConcurrentChecks.toString(),
                    savedModUpdateConcurrentCheckCount = currentModUpdateConcurrentChecks,
                    selectedThemeMode = currentThemeMode,
                    selectedFrontendMode = currentFrontendMode,
                    selectedSteamLanguagePreference = currentSteamLanguagePreference,
                    allowSteamAuthenticatedCleartextHttp = allowSteamAuthenticatedCleartextHttp,
                    experimentalWorkshopDirectAccessEnabled = experimentalWorkshopDirectAccessEnabled,
                    baiduTranslationApiKeyConfigured = baiduTranslationCredentialsRepository.hasConfiguredCredentials(),
                    steamAuthState = currentSteamAuthState,
                    autoCheckUpdatesEnabled = settingsRepository.isAutoCheckUpdatesEnabled(),
                    preferredUpdateSource = settingsRepository.getPreferredUpdateSource(),
                    availableUpdateSources = UpdateSource.userSelectableSources(),
                    currentVersionText = BuildConfig.VERSION_NAME,
                    updateStatusSummary = buildUpdateStatusSummary(),
                    runtimeLogDirectoryPath = AppRuntimeLogManager.logDirectoryPath(application),
                    latestRuntimeLogPath = AppRuntimeLogManager.latestLogPath(application),
                    message = null,
                ),
                baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
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
        val entries = _uiState.value.modLibraryState.items.latestVersionsForUpdateCheck()
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
            val concurrency = settingsRepository.getModUpdateConcurrentCheckCount()
            val results = supervisorScope {
                val semaphore = Semaphore(concurrency)
                entries.map { entry ->
                    async {
                        semaphore.withPermit {
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
                            val key = entry.modLibraryKey()
                            _uiState.update { state ->
                                val nextUpdateCheckState = state.modLibraryState.updateCheckState.copy(
                                    results = state.modLibraryState.updateCheckState.results + (key to result),
                                ).filterForEntries(state.modLibraryState.items.latestVersionsForUpdateCheck())
                                state.copy(
                                    modLibraryState = state.modLibraryState.copy(
                                        updateCheckState = nextUpdateCheckState,
                                    ),
                                )
                            }
                            key to result
                        }
                    }
                }.awaitAll().toMap(linkedMapOf())
            }

            val summaryMessage = buildModUpdateCheckSummary(results.values)
            val checkedAtMillis = System.currentTimeMillis()
            var persistedUpdateCheckState: ModLibraryUpdateCheckState? = null
            _uiState.update { state ->
                val nextUpdateCheckState = state.modLibraryState.updateCheckState.copy(
                    isChecking = false,
                    summaryMessage = summaryMessage,
                    lastCheckedAtMillis = checkedAtMillis,
                    results = results,
                ).filterForEntries(state.modLibraryState.items.latestVersionsForUpdateCheck())
                persistedUpdateCheckState = nextUpdateCheckState
                state.copy(
                    modLibraryState = state.modLibraryState.copy(
                        updateCheckState = nextUpdateCheckState,
                    ),
                )
            }
            persistedUpdateCheckState?.let(::persistModLibraryUpdateStateIfStable)
            _toastMessages.emit(summaryMessage)
        }
    }

    fun toggleModLibraryDisplayMode() {
        val nextMode = _uiState.value.modLibraryState.displayMode.next()
        settingsRepository.setModLibraryDisplayMode(nextMode)
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(displayMode = nextMode),
            )
        }
    }

    fun toggleModLibraryFilterPanel() {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    filterPanelExpanded = !state.modLibraryState.filterPanelExpanded,
                ),
            )
        }
    }

    fun updateModLibrarySearchQuery(value: String) {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    filterState = state.modLibraryState.filterState.copy(searchQuery = value),
                ),
            )
        }
    }

    fun updateModLibraryGameFilter(gameTitle: String?) {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    filterState = state.modLibraryState.filterState.copy(
                        selectedGameTitle = gameTitle?.takeIf(String::isNotBlank),
                    ),
                ),
            )
        }
    }

    fun updateModLibrarySortOption(sortOption: ModLibrarySortOption) {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    sortOption = sortOption,
                ),
            )
        }
    }

    fun clearModLibraryFilters() {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    filterState = ModLibraryFilterState(),
                ),
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

    fun updateModUpdateConcurrentCheckCountInput(value: String) {
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    modUpdateConcurrentCheckCountInput = value.filter(Char::isDigit),
                    message = null,
                ),
            )
        }
    }

    fun updateAllowSteamAuthenticatedCleartextHttp(allowed: Boolean) {
        settingsRepository.setSteamAuthenticatedCleartextHttpAllowed(allowed)
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    allowSteamAuthenticatedCleartextHttp = allowed,
                    message = null,
                ),
            )
        }
        viewModelScope.launch {
            _toastMessages.emit(
                if (allowed) {
                    "已允许带 Steam 登录态的 HTTP 请求，请仅在可信网络环境下使用。"
                } else {
                    "已禁止带 Steam 登录态的 HTTP 请求。"
                },
            )
        }
    }

    fun updateExperimentalWorkshopDirectAccess(enabled: Boolean) {
        settingsRepository.setExperimentalWorkshopDirectAccessEnabled(enabled)
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    experimentalWorkshopDirectAccessEnabled = enabled,
                    message = null,
                ),
            )
        }
        viewModelScope.launch {
            _toastMessages.emit(
                if (enabled) {
                    if (ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessDisabledForCurrentProcess()) {
                        "已开启实验性创意工坊直连策略，但当前会话已禁用 Watt 链路，重启应用后生效。"
                    } else {
                        "已开启实验性创意工坊直连策略，如果存在问题，请导出一份日志发给开发者。"
                    }
                } else {
                    "已关闭实验性创意工坊直连策略。"
                },
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
                    message = null,
                ),
            )
        }
    }

    fun updateFrontendMode(frontendMode: AppFrontendMode) {
        settingsRepository.setFrontendMode(frontendMode)
        _uiState.update { state ->
            state.copy(
                frontendMode = frontendMode,
                settingsState = state.settingsState.copy(
                    selectedFrontendMode = frontendMode,
                    message = null,
                ),
            )
        }
        viewModelScope.launch {
            _toastMessages.emit("已切换为${frontendMode.displayName()}前端。")
        }
    }

    fun updateSteamLanguagePreference(languagePreference: SteamLanguagePreference) {
        settingsRepository.setSteamLanguagePreference(languagePreference)
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    selectedSteamLanguagePreference = languagePreference,
                    message = "已切换 Steam 语言偏好：${languagePreference.displayName()}。",
                ),
            )
        }
    }

    fun openBaiduTranslationApiKeyScreen() {
        val savedCredentials = baiduTranslationCredentialsRepository.getCredentials()
        _uiState.update { state ->
            state.copy(
                baiduTranslationApiKeyState = BaiduTranslationApiKeyUiState(
                    appIdInput = savedCredentials.appId,
                    apiKeyInput = savedCredentials.apiKey,
                    hasSavedCredentials = savedCredentials.isConfigured(),
                    testFailureReason = null,
                    message = null,
                ),
                settingsState = state.settingsState.copy(message = null),
            )
        }
        navigateTo(WorkshopScreenDestination.BaiduTranslationApiKey, rememberPrevious = false)
    }

    fun updateBaiduTranslationAppIdInput(value: String) {
        _uiState.update { state ->
            state.copy(
                baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
                    appIdInput = value.trim(),
                    testFailureReason = null,
                    message = null,
                ),
            )
        }
    }

    fun updateBaiduTranslationApiKeyInput(value: String) {
        _uiState.update { state ->
            state.copy(
                baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
                    apiKeyInput = value.trim(),
                    testFailureReason = null,
                    message = null,
                ),
            )
        }
    }

    fun saveBaiduTranslationApiKey() {
        val currentState = _uiState.value.baiduTranslationApiKeyState
        val credentials = BaiduTranslationCredentials(
            appId = currentState.appIdInput,
            apiKey = currentState.apiKeyInput,
        )
        baiduTranslationCredentialsRepository.setCredentials(credentials)
        val savedCredentials = baiduTranslationCredentialsRepository.getCredentials()
        val hasSavedCredentials = savedCredentials.isConfigured()
        val statusMessage = when {
            hasSavedCredentials -> "已保存百度大模型文本翻译的 AppID 和 API Key。"
            savedCredentials.appId.isBlank() && savedCredentials.apiKey.isBlank() ->
                "已清除百度大模型文本翻译的 AppID 和 API Key。"
            else -> "已保存当前填写内容，但要同时提供 AppID 和 API Key 才能调用百度大模型文本翻译。"
        }
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    baiduTranslationApiKeyConfigured = hasSavedCredentials,
                    message = statusMessage,
                ),
                baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
                    appIdInput = savedCredentials.appId,
                    apiKeyInput = savedCredentials.apiKey,
                    hasSavedCredentials = hasSavedCredentials,
                    testFailureReason = null,
                    message = statusMessage,
                ),
            )
        }
    }

    fun testBaiduTranslationConfiguration() {
        val currentState = _uiState.value.baiduTranslationApiKeyState
        if (currentState.isTesting) {
            return
        }

        val credentials = BaiduTranslationCredentials(
            appId = currentState.appIdInput.trim(),
            apiKey = currentState.apiKeyInput.trim(),
        )
        _uiState.update { state ->
            state.copy(
                baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
                    isTesting = true,
                    testResultText = null,
                    testFailureReason = null,
                    message = null,
                ),
            )
        }

        viewModelScope.launch {
            val validationMessage = validateBaiduTranslationCredentials(credentials)
            if (validationMessage != null) {
                _uiState.update { state ->
                    state.copy(
                        baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
                            isTesting = false,
                            testResultText = null,
                            testFailureReason = validationMessage,
                            message = null,
                        ),
                    )
                }
                _toastMessages.emit(validationMessage)
                return@launch
            }

            runCatching {
                translateWithBaiduCredentials(
                    text = BAIDU_TRANSLATION_SAMPLE_TEXT,
                    credentials = credentials,
                    targetLocale = Locale.CHINESE,
                )
            }.onSuccess { translatedText ->
                _uiState.update { state ->
                    state.copy(
                        baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
                            isTesting = false,
                            testResultText = translatedText,
                            testFailureReason = null,
                            message = "百度大模型文本翻译测试成功。",
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        baiduTranslationApiKeyState = state.baiduTranslationApiKeyState.copy(
                            isTesting = false,
                            testResultText = null,
                            testFailureReason = error.message ?: "百度大模型文本翻译测试失败，请稍后重试。",
                            message = null,
                        ),
                    )
                }
            }
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

    fun dismissUsageNoticeDialog() {
        settingsRepository.setUsageNoticeAcknowledged()
        _uiState.update { state ->
            state.copy(showUsageNoticeDialog = false)
        }
    }

    fun dismissSteamDirectAccessFallbackDialog() {
        _uiState.update { state ->
            state.copy(steamDirectAccessFallbackDialogState = null)
        }
    }

    fun openSteamLoginDialog() {
        cancelPendingSteamLoginFlow("UI: reset previous login flow before opening Steam login dialog.")
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
        cancelPendingSteamLoginFlow("UI: Steam login dialog dismissed by user.")
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

    fun updateSteamLoginRefreshToken(value: String) {
        _uiState.update { state ->
            val dialog = state.settingsState.steamAuthState.loginDialogState ?: return@update state
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = dialog.copy(
                            refreshToken = value,
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

    fun switchSteamLoginInputMode(inputMode: SteamLoginInputMode) {
        cancelPendingSteamLoginFlow("UI: switched Steam login input mode to ${inputMode.name}.")
        _uiState.update { state ->
            val dialog = state.settingsState.steamAuthState.loginDialogState ?: return@update state
            if (dialog.inputMode == inputMode && dialog.challengeType == null) {
                return@update state
            }
            state.copy(
                settingsState = state.settingsState.copy(
                    steamAuthState = state.settingsState.steamAuthState.copy(
                        loginDialogState = dialog.copy(
                            inputMode = inputMode,
                            password = if (inputMode == SteamLoginInputMode.RefreshToken) "" else dialog.password,
                            guardCode = "",
                            challengeType = null,
                            challengeMessage = null,
                            isPollingConfirmation = false,
                            isSubmitting = false,
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
        ensureSteamLoginAttempt(dialog)
        viewModelScope.launch {
            appendSteamLoginDebugLine(
                when {
                    dialog.inputMode == SteamLoginInputMode.RefreshToken &&
                        dialog.challengeType == null ->
                        "UI: submitting refresh-token login accountHint=${dialog.username.maskSteamLoginValue()} refreshLength=${dialog.refreshToken.trim().length} replaceAccount=${dialog.targetAccountId != null}."

                    dialog.challengeType == SteamGuardChallengeType.EmailCode ||
                        dialog.challengeType == SteamGuardChallengeType.DeviceCode ->
                        "UI: submitting Steam Guard code type=${dialog.challengeType.name} codeLength=${dialog.guardCode.trim().length}."

                    dialog.challengeType == SteamGuardChallengeType.DeviceConfirmation ||
                        dialog.challengeType == SteamGuardChallengeType.EmailConfirmation ->
                        "UI: user requested to continue waiting for Steam confirmation."

                    else ->
                        "UI: submitting credential login username=${dialog.username.maskSteamLoginValue()} passwordLength=${dialog.password.length} replaceAccount=${dialog.targetAccountId != null}."
                },
            )
            setSteamLoginSubmitting(true)
            val result = runCatching {
                when {
                    dialog.inputMode == SteamLoginInputMode.RefreshToken &&
                        dialog.challengeType == null ->
                        steamAuthRepository.signInWithRefreshToken(
                            refreshToken = dialog.refreshToken.trim(),
                            accountNameHint = dialog.username.trim(),
                            replaceAccountId = dialog.targetAccountId,
                            debugLogger = ::appendSteamLoginDebugLine,
                        )

                    else -> when (dialog.challengeType) {
                    SteamGuardChallengeType.EmailCode,
                    SteamGuardChallengeType.DeviceCode,
                    -> steamAuthRepository.submitPendingGuardCode(
                        code = dialog.guardCode.trim(),
                        debugLogger = ::appendSteamLoginDebugLine,
                    )

                    SteamGuardChallengeType.DeviceConfirmation,
                    SteamGuardChallengeType.EmailConfirmation,
                    -> steamAuthRepository.waitForPendingConfirmation(
                        debugLogger = ::appendSteamLoginDebugLine,
                    )

                    else -> steamAuthRepository.beginSignIn(
                        username = dialog.username.trim(),
                        password = dialog.password,
                        replaceAccountId = dialog.targetAccountId,
                        debugLogger = ::appendSteamLoginDebugLine,
                    )
                    }
                }
            }
            result.onSuccess(::applySteamSignInStep)
                .onFailure { error ->
                    appendSteamLoginFailure("UI: Steam login step failed.", error)
                    setSteamLoginSubmitting(false, error.message ?: "Steam 登录失败。")
                }
        }
    }

    fun switchToAnonymousSteamAccount() {
        steamAuthRepository.setActiveAccount(null)
        syncSteamAuthState(message = "已切换为匿名浏览。")
        primeSteamWebSessionAsync(force = true)
    }

    fun setActiveSteamAccount(accountId: String) {
        steamAuthRepository.setActiveAccount(accountId)
        syncSteamAuthState(message = null)
        primeSteamWebSessionAsync(force = true)
    }

    fun reauthenticateSteamAccount(accountId: String) {
        val account = steamAuthRepository.loadSnapshot().accounts.firstOrNull { it.accountId == accountId } ?: return
        cancelPendingSteamLoginFlow("UI: reset previous login flow before starting reauthentication.")
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
        val parsedModUpdateConcurrentChecks = settingsState.modUpdateConcurrentCheckCountInput.toIntOrNull()

        if (parsedThreadCount == null || parsedConcurrentTasks == null || parsedModUpdateConcurrentChecks == null) {
            _uiState.update { state ->
                state.copy(
                    settingsState = state.settingsState.copy(
                        message = "请输入有效的下载与检查设置。",
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
        val clampedModUpdateConcurrentChecks = parsedModUpdateConcurrentChecks.coerceIn(
            DownloadSettingsRepository.MIN_MOD_UPDATE_CONCURRENT_CHECKS,
            DownloadSettingsRepository.MAX_MOD_UPDATE_CONCURRENT_CHECKS,
        )

        settingsRepository.setDownloadThreadCount(clampedThreadCount)
        settingsRepository.setConcurrentDownloadTaskCount(clampedConcurrentTasks)
        settingsRepository.setModUpdateConcurrentCheckCount(clampedModUpdateConcurrentChecks)
        _uiState.update { state ->
            state.copy(
                settingsState = state.settingsState.copy(
                    downloadThreadCountInput = clampedThreadCount.toString(),
                    savedDownloadThreadCount = clampedThreadCount,
                    concurrentDownloadTaskCountInput = clampedConcurrentTasks.toString(),
                    savedConcurrentDownloadTaskCount = clampedConcurrentTasks,
                    modUpdateConcurrentCheckCountInput = clampedModUpdateConcurrentChecks.toString(),
                    savedModUpdateConcurrentCheckCount = clampedModUpdateConcurrentChecks,
                    message = null,
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

    fun openModDetail(entry: DownloadedModGroup) {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    selectedEntry = entry,
                    detailDescriptionTranslation = ModLibraryDescriptionTranslationUiState(),
                ),
            )
        }
        navigateTo(WorkshopScreenDestination.ModDetail)
        ensureModDetailPreviewCached(entry)
    }

    fun openDownloadedWorkshopItem(item: WorkshopBrowseItem) {
        val entry = _uiState.value.modLibraryState.items.firstOrNull { group ->
            group.matches(item.appId, item.publishedFileId) && group.hasStoredVersions()
        }
        if (entry == null) {
            viewModelScope.launch {
                _toastMessages.emit("这个模组还没有下载完成。")
            }
            refreshModLibrary(showLoading = false)
            return
        }
        openModDetail(entry)
    }

    fun openModLibraryChangeNotes(group: DownloadedModGroup) {
        val targetGroupKey = group.modGroupKey()
        val dialogMarkdown = _uiState.value.modLibraryState.changeNotesDialogState
            ?.takeIf { dialogState -> dialogState.group.matches(group) }
            ?.markdown
            .orEmpty()
        val cachedMarkdown = dialogMarkdown.takeIf(String::isNotBlank)
            ?: group.changeNotes.takeIf { group.changeNotesFetched }
            .orEmpty()
        val shouldLoad = !group.changeNotesFetched && dialogMarkdown.isBlank()

        _uiState.update { state ->
            val resolvedGroup = state.modLibraryState.items.firstOrNull { it.matches(group) } ?: group
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    changeNotesDialogState = ModLibraryChangeNotesDialogUiState(
                        group = resolvedGroup,
                        markdown = cachedMarkdown,
                        isLoading = shouldLoad,
                        errorMessage = null,
                    ),
                ),
            )
        }

        if (!shouldLoad) {
            return
        }

        viewModelScope.launch {
            runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    detailRepository.loadChangeNotesMarkdown(group.publishedFileId)
                }
            }.onSuccess { markdown ->
                val updatedEntries = runCatching {
                    modLibraryRepository.updateChangeNotes(
                        appId = group.appId,
                        publishedFileId = group.publishedFileId,
                        changeNotes = markdown,
                    )
                }.getOrNull()
                _uiState.update { state ->
                    val nextState = updatedEntries?.let { entries ->
                        applyModLibraryEntries(state, entries)
                    } ?: state
                    val dialogState = nextState.modLibraryState.changeNotesDialogState ?: return@update nextState
                    if (dialogState.group.modGroupKey() != targetGroupKey) {
                        return@update nextState
                    }
                    val resolvedGroup = nextState.modLibraryState.items.firstOrNull { it.matches(group) } ?: dialogState.group
                    nextState.copy(
                        modLibraryState = nextState.modLibraryState.copy(
                            changeNotesDialogState = dialogState.copy(
                                group = resolvedGroup,
                                markdown = markdown,
                                isLoading = false,
                                errorMessage = null,
                            ),
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    val dialogState = state.modLibraryState.changeNotesDialogState ?: return@update state
                    if (dialogState.group.modGroupKey() != targetGroupKey) {
                        return@update state
                    }
                    state.copy(
                        modLibraryState = state.modLibraryState.copy(
                            changeNotesDialogState = dialogState.copy(
                                isLoading = false,
                                errorMessage = workshopRequestFailureMessage(
                                    error = error,
                                    fallbackMessage = error.message ?: "加载更新日志失败。",
                                ),
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun dismissModLibraryChangeNotes() {
        _uiState.update { state ->
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    changeNotesDialogState = null,
                ),
            )
        }
    }

    private fun ensureModDetailPreviewCached(group: DownloadedModGroup) {
        if (group.previewImagePath?.let(::File)?.isFile == true) {
            return
        }
        val appId = group.appId
        val publishedFileId = group.publishedFileId
        viewModelScope.launch {
            val resolvedPreviewUrl = runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    group.previewImageUrl.takeIf(String::isNotBlank)
                        ?: detailRepository.loadWorkshopItemDetail(
                            item = group.toWorkshopBrowseItem(),
                            includeChangeNotes = false,
                        ).previewImageUrl
                }
            }.getOrNull()
                ?.trim()
                .orEmpty()
            if (resolvedPreviewUrl.isBlank()) {
                return@launch
            }

            val urlUpdatedEntries = if (group.previewImageUrl.isBlank()) {
                runCatching {
                    modLibraryRepository.updatePreviewImageMetadata(
                        appId = appId,
                        publishedFileId = publishedFileId,
                        previewImageUrl = resolvedPreviewUrl,
                    )
                }.getOrNull()
            } else {
                null
            }
            if (urlUpdatedEntries != null) {
                _uiState.update { state -> applyModLibraryEntries(state, urlUpdatedEntries) }
            }

            val cachedEntries = runCatching {
                modLibraryRepository.cachePreviewImage(
                    appId = appId,
                    publishedFileId = publishedFileId,
                    previewImageUrl = resolvedPreviewUrl,
                )
            }.getOrNull() ?: return@launch
            _uiState.update { state -> applyModLibraryEntries(state, cachedEntries) }
        }
    }

    fun updateMod(entry: DownloadedModEntry) {
        enqueueWorkshopItems(
            appId = entry.appId,
            gameTitle = entry.gameTitle,
            items = listOf(entry.toWorkshopBrowseItem()),
        )
    }

    fun requestRenameMod(entry: DownloadedModGroup) {
        _uiState.update {
            it.copy(
                pendingRenameMod = entry,
                renameModTitleInput = entry.itemTitle,
            )
        }
    }

    fun updateRenameModTitleInput(value: String) {
        _uiState.update { it.copy(renameModTitleInput = value) }
    }

    fun confirmRenameMod() {
        val entry = _uiState.value.pendingRenameMod ?: return
        val newTitle = _uiState.value.renameModTitleInput.trim()
        if (newTitle.isBlank()) {
            return
        }
        if (newTitle == entry.itemTitle) {
            _uiState.update {
                it.copy(
                    pendingRenameMod = null,
                    renameModTitleInput = "",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                pendingRenameMod = null,
                renameModTitleInput = "",
            )
        }
        viewModelScope.launch {
            runCatching {
                modLibraryRepository.renameMod(
                    appId = entry.appId,
                    publishedFileId = entry.publishedFileId,
                    newTitle = newTitle,
                )
            }.onSuccess { entries ->
                var persistedUpdateCheckState: ModLibraryUpdateCheckState? = null
                _uiState.update { state ->
                    val nextState = applyModLibraryEntries(
                        state = state.copy(
                            pendingRenameMod = null,
                            renameModTitleInput = "",
                        ),
                        entries = entries,
                    )
                    persistedUpdateCheckState = nextState.modLibraryState.updateCheckState
                    nextState
                }
                persistedUpdateCheckState?.let(::persistModLibraryUpdateStateIfStable)
                _toastMessages.emit("已将「${entry.itemTitle}」重命名为「$newTitle」。")
            }.onFailure { error ->
                _toastMessages.emit(error.message ?: "重命名模组失败。")
                refreshModLibrary(showLoading = false)
            }
        }
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
                downloadCenterManager.clearExportedFilesForMod(entry)
                _toastMessages.emit(
                    if (entry.isTrackingOnly) {
                        "已从模组库移除 ${entry.itemTitle}。"
                    } else {
                        "已删除 ${entry.itemTitle} 的本地文件。"
                    },
                )
                var persistedUpdateCheckState: ModLibraryUpdateCheckState? = null
                _uiState.update { state ->
                    val nextState = applyModLibraryEntries(
                        state = state.copy(pendingRemoveMod = null),
                        entries = entries,
                    )
                    persistedUpdateCheckState = nextState.modLibraryState.updateCheckState
                    nextState
                }
                persistedUpdateCheckState?.let(::persistModLibraryUpdateStateIfStable)
            }.onFailure { error ->
                _toastMessages.emit(error.message ?: "删除模组失败。")
                refreshModLibrary(showLoading = false)
            }
        }
    }

    fun dismissRemoveModDialog() {
        _uiState.update { it.copy(pendingRemoveMod = null) }
    }

    fun dismissRenameModDialog() {
        _uiState.update {
            it.copy(
                pendingRenameMod = null,
                renameModTitleInput = "",
            )
        }
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
                workshopItemDetailBackStack = emptyList(),
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

    fun toggleGameWorkshopMoreActions() {
        _uiState.update { state ->
            val currentWorkshopState = state.gameWorkshopState ?: return@update state
            state.copy(
                gameWorkshopState = currentWorkshopState.copy(
                    isMoreActionsExpanded = currentWorkshopState.isMoreActionsExpanded.not(),
                ),
            )
        }
    }

    fun dismissGameWorkshopMoreActions() {
        _uiState.update { state ->
            val currentWorkshopState = state.gameWorkshopState ?: return@update state
            if (!currentWorkshopState.isMoreActionsExpanded) {
                return@update state
            }
            state.copy(
                gameWorkshopState = currentWorkshopState.copy(
                    isMoreActionsExpanded = false,
                ),
            )
        }
    }

    fun openGameWorkshopDirectDownloadDialog() {
        _uiState.update { state ->
            val currentWorkshopState = state.gameWorkshopState ?: return@update state
            state.copy(
                gameWorkshopState = currentWorkshopState.copy(
                    isMoreActionsExpanded = false,
                    showDirectDownloadDialog = true,
                ),
            )
        }
    }

    fun dismissGameWorkshopDirectDownloadDialog() {
        _uiState.update { state ->
            val currentWorkshopState = state.gameWorkshopState ?: return@update state
            if (!currentWorkshopState.showDirectDownloadDialog) {
                return@update state
            }
            state.copy(
                gameWorkshopState = currentWorkshopState.copy(
                    showDirectDownloadDialog = false,
                ),
            )
        }
    }

    fun openWorkshopItemDetail(item: WorkshopBrowseItem) {
        val targetAppId = item.appId
        val targetPublishedFileId = item.publishedFileId
        _uiState.update { state ->
            val shouldPushCurrentDetail = state.currentScreen == WorkshopScreenDestination.WorkshopItemDetail &&
                state.workshopItemDetailState?.item?.matches(targetAppId, targetPublishedFileId) == false
            state.copy(
                workshopItemDetailBackStack = if (shouldPushCurrentDetail) {
                    state.workshopItemDetailBackStack + requireNotNull(state.workshopItemDetailState)
                } else if (state.currentScreen == WorkshopScreenDestination.WorkshopItemDetail) {
                    state.workshopItemDetailBackStack
                } else {
                    emptyList()
                },
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
                    detailRepository.loadWorkshopItemDetail(
                        item = item,
                        includeChangeNotes = true,
                    )
                }
            }.onSuccess { detail ->
                val shouldLoadComments = detail.shouldLoadWorkshopComments()
                _uiState.update { state ->
                    state.updateWorkshopItemDetailState(targetAppId, targetPublishedFileId) { current ->
                        current.copy(
                            detail = detail,
                            isLoading = false,
                            isLoadingComments = shouldLoadComments,
                            commentErrorMessage = detail.commentUnavailableMessage(),
                            message = null,
                            showConnectionErrorState = false,
                        )
                    }
                }
                if (shouldLoadComments) {
                    loadWorkshopCommentsPage(
                        appId = item.appId,
                        publishedFileId = item.publishedFileId,
                        page = 1,
                    )
                }
            }.onFailure { error ->
                val showConnectionErrorState = error.isWorkshopConnectionFailure()
                _uiState.update { state ->
                    state.updateWorkshopItemDetailState(targetAppId, targetPublishedFileId) { current ->
                        current.copy(
                            isLoading = false,
                            message = workshopRequestFailureMessage(
                                error = error,
                                fallbackMessage = error.message ?: "加载模组详情失败。",
                            ),
                            showConnectionErrorState = showConnectionErrorState,
                        )
                    }
                }
            }
        }
    }

    fun retryWorkshopItemDetail() {
        val item = _uiState.value.workshopItemDetailState?.item ?: return
        openWorkshopItemDetail(item)
    }

    fun loadPreviousWorkshopCommentsPage() {
        shiftWorkshopCommentsPage(delta = -1)
    }

    fun loadNextWorkshopCommentsPage() {
        shiftWorkshopCommentsPage(delta = 1)
    }

    fun retryWorkshopCommentsPage() {
        val detailState = _uiState.value.workshopItemDetailState ?: return
        val detail = detailState.detail ?: return
        if (detailState.isLoading || detailState.isLoadingComments) {
            return
        }

        loadWorkshopCommentsPage(
            appId = detailState.item.appId,
            publishedFileId = detailState.item.publishedFileId,
            page = detail.commentPage.coerceAtLeast(1),
        )
    }

    private fun shiftWorkshopCommentsPage(delta: Int) {
        val detailState = _uiState.value.workshopItemDetailState ?: return
        val detail = detailState.detail ?: return
        if (detailState.isLoading || detailState.isLoadingComments) {
            return
        }

        val targetPage = (detail.commentPage + delta).coerceAtLeast(1)
        if (targetPage == detail.commentPage) {
            return
        }
        if (delta < 0 && !detail.hasPreviousCommentPage) {
            return
        }
        if (delta > 0 && !detail.hasNextCommentPage) {
            return
        }

        loadWorkshopCommentsPage(
            appId = detailState.item.appId,
            publishedFileId = detailState.item.publishedFileId,
            page = targetPage,
        )
    }

    private fun loadWorkshopCommentsPage(
        appId: UInt,
        publishedFileId: ULong,
        page: Int,
    ) {
        val detailSnapshot = _uiState.value.workshopItemDetailState?.detail
            ?.takeIf { detail ->
                detail.appId == appId && detail.publishedFileId == publishedFileId
            }
            ?: return
        val commentUnavailableMessage = detailSnapshot.commentUnavailableMessage()
        if (!detailSnapshot.shouldLoadWorkshopComments()) {
            _uiState.update { state ->
                state.updateWorkshopItemDetailState(appId, publishedFileId) { current ->
                    current.copy(
                        isLoadingComments = false,
                        commentErrorMessage = commentUnavailableMessage,
                    )
                }
            }
            return
        }

        _uiState.update { state ->
            state.updateWorkshopItemDetailState(appId, publishedFileId) { current ->
                current.copy(
                    isLoadingComments = true,
                    commentErrorMessage = null,
                )
            }
        }

        viewModelScope.launch {
            runCatching {
                withTimeout(WORKSHOP_COMMENTS_TIMEOUT_MS) {
                    detailRepository.loadWorkshopCommentPage(
                        detail = detailSnapshot,
                        page = page,
                    )
                }
            }.onSuccess { commentPage ->
                _uiState.update { state ->
                    state.updateWorkshopItemDetailState(appId, publishedFileId) { current ->
                        val currentDetail = current.detail ?: return@updateWorkshopItemDetailState current.copy(
                            isLoadingComments = false,
                        )
                        current.copy(
                            detail = currentDetail.copy(
                                commentsUrl = commentPage.commentsUrl,
                                commentCount = commentPage.commentCount,
                                commentPage = commentPage.page,
                                commentTotalPages = commentPage.totalPages,
                                hasPreviousCommentPage = commentPage.hasPreviousPage,
                                hasNextCommentPage = commentPage.hasNextPage,
                                comments = commentPage.comments,
                            ),
                            isLoadingComments = false,
                            commentErrorMessage = null,
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.updateWorkshopItemDetailState(appId, publishedFileId) { current ->
                        current.copy(
                            isLoadingComments = false,
                            commentErrorMessage = workshopRequestFailureMessage(
                                error = error,
                                fallbackMessage = error.message ?: "加载评论失败。",
                            ),
                        )
                    }
                }
            }
        }
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
        val credentials = configuredBaiduCredentialsOrToast() ?: return

        val targetAppId = detailState.item.appId
        val targetPublishedFileId = detailState.item.publishedFileId
        _uiState.update { state ->
            state.updateWorkshopItemDetailState(targetAppId, targetPublishedFileId) { current ->
                current.copy(
                    isTranslatingDescription = true,
                    translationErrorMessage = null,
                )
            }
        }

        viewModelScope.launch {
            runCatching {
                translateWithBaiduCredentials(
                    text = description,
                    credentials = credentials,
                    reference = buildBaiduModDescriptionReference(
                        modTitle = detailState.detail?.title ?: detailState.item.title,
                        gameTitle = resolveGameTitleForTranslation(targetAppId).orEmpty(),
                    ),
                )
            }.onSuccess { translatedText ->
                _uiState.update { state ->
                    state.updateWorkshopItemDetailState(targetAppId, targetPublishedFileId) { current ->
                        current.copy(
                            isTranslatingDescription = false,
                            translatedDescription = translatedText,
                            translationErrorMessage = null,
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.updateWorkshopItemDetailState(targetAppId, targetPublishedFileId) { current ->
                        current.copy(
                            isTranslatingDescription = false,
                            translationErrorMessage = error.message ?: "翻译描述失败，请稍后重试。",
                        )
                    }
                }
            }
        }
    }

    fun translateModLibraryDescription() {
        val modLibraryState = _uiState.value.modLibraryState
        val selectedEntry = modLibraryState.selectedEntry ?: return
        if (modLibraryState.detailDescriptionTranslation.isTranslatingDescription) {
            return
        }

        val description = selectedEntry.description.trim()
        if (description.isBlank()) {
            viewModelScope.launch {
                _toastMessages.emit("当前没有可翻译的简介。")
            }
            return
        }
        val credentials = configuredBaiduCredentialsOrToast() ?: return

        val targetModGroupKey = selectedEntry.modGroupKey()
        _uiState.update { state ->
            val current = state.modLibraryState.selectedEntry ?: return@update state
            if (current.modGroupKey() != targetModGroupKey) {
                return@update state
            }
            state.copy(
                modLibraryState = state.modLibraryState.copy(
                    detailDescriptionTranslation = state.modLibraryState.detailDescriptionTranslation.copy(
                        isTranslatingDescription = true,
                        translationErrorMessage = null,
                    ),
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                translateWithBaiduCredentials(
                    text = description,
                    credentials = credentials,
                    reference = buildBaiduModDescriptionReference(
                        modTitle = selectedEntry.itemTitle,
                        gameTitle = selectedEntry.gameTitle,
                    ),
                )
            }.onSuccess { translatedText ->
                _uiState.update { state ->
                    val current = state.modLibraryState.selectedEntry ?: return@update state
                    if (current.modGroupKey() != targetModGroupKey) {
                        return@update state
                    }
                    state.copy(
                        modLibraryState = state.modLibraryState.copy(
                            detailDescriptionTranslation = state.modLibraryState.detailDescriptionTranslation.copy(
                                isTranslatingDescription = false,
                                translatedDescription = translatedText,
                                translationErrorMessage = null,
                            ),
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    val current = state.modLibraryState.selectedEntry ?: return@update state
                    if (current.modGroupKey() != targetModGroupKey) {
                        return@update state
                    }
                    state.copy(
                        modLibraryState = state.modLibraryState.copy(
                            detailDescriptionTranslation = state.modLibraryState.detailDescriptionTranslation.copy(
                                isTranslatingDescription = false,
                                translationErrorMessage = error.message ?: "翻译简介失败，请稍后重试。",
                            ),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun translateWithBaiduCredentials(
        text: String,
        credentials: BaiduTranslationCredentials,
        targetLocale: Locale = Locale.getDefault(),
        reference: String? = null,
    ): String {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return normalizedText
        }

        validateBaiduTranslationCredentials(credentials)?.let { validationMessage ->
            throw IllegalStateException(validationMessage)
        }

        val targetLanguage = mapLocaleLanguageToBaiduLanguage(targetLocale) ?: BAIDU_DEFAULT_TARGET_LANGUAGE

        return baiduAiTextTranslationClient.translate(
            text = normalizedText,
            sourceLanguage = BAIDU_AUTO_DETECT_LANGUAGE,
            targetLanguage = targetLanguage,
            credentials = credentials,
            reference = reference,
        )
    }

    private fun validateBaiduTranslationCredentials(
        credentials: BaiduTranslationCredentials,
    ): String? =
        when {
            credentials.appId.isBlank() && credentials.apiKey.isBlank() ->
                "未填写百度大模型文本翻译的 AppID 和 API Key。"

            credentials.appId.isBlank() ->
                "未填写百度大模型文本翻译的 AppID。"

            credentials.apiKey.isBlank() ->
                "未填写百度大模型文本翻译的 API Key。"

            else -> null
        }

    private fun configuredBaiduCredentialsOrToast(): BaiduTranslationCredentials? {
        val credentials = baiduTranslationCredentialsRepository.getCredentials()
        val validationMessage = validateBaiduTranslationCredentials(credentials) ?: return credentials
        viewModelScope.launch {
            _toastMessages.emit(validationMessage)
        }
        return null
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
        val searchQuery = workshopState.searchQuery.trim()
        val sortOption = if (searchQuery.isNotBlank()) {
            WorkshopBrowseSortOption.MostPopular
        } else {
            workshopState.selectedSortOption
        }
        val timeWindow = if (searchQuery.isNotBlank()) {
            WorkshopBrowseTimeWindow.AllTime
        } else {
            workshopState.selectedTimeWindow
        }
        loadWorkshopPage(
            game = workshopState.game,
            searchQuery = searchQuery,
            sortOption = sortOption,
            timeWindow = timeWindow,
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

    fun downloadSingleItem(item: WorkshopBrowseItem): Boolean =
        enqueueWorkshopItems(
            appId = item.appId,
            gameTitle = _uiState.value.gameWorkshopState?.game?.name ?: "Workshop",
            items = listOf(item),
        )

    fun addWorkshopItemToModLibrary(item: WorkshopBrowseItem) {
        viewModelScope.launch {
            val isAlreadyInLibrary = _uiState.value.modLibraryState.items.any { group ->
                group.matches(item.appId, item.publishedFileId)
            }
            if (isAlreadyInLibrary) {
                _toastMessages.emit("该模组已经在模组库中。")
                return@launch
            }

            val detailState = _uiState.value.workshopItemDetailState
            val cachedDetail = detailState
                ?.takeIf { state ->
                    state.item.appId == item.appId &&
                        state.item.publishedFileId == item.publishedFileId
                }
                ?.detail
            val detail = cachedDetail ?: runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                    detailRepository.loadWorkshopItemDetail(
                        item = item,
                        includeChangeNotes = false,
                    )
                }
            }.getOrNull()
            val remoteUpdatedAtMillis = detail?.timeUpdatedEpochSeconds
                ?.takeIf { it > 0L }
                ?.times(1000L)

            runCatching {
                modLibraryRepository.upsertTrackedMod(
                    appId = item.appId,
                    publishedFileId = item.publishedFileId,
                    gameTitle = resolveGameTitleForDownload(item.appId),
                    itemTitle = detail?.title?.ifBlank { item.title } ?: item.title,
                    description = detail?.description.orEmpty(),
                    changeNotes = detail?.changeNotes.orEmpty(),
                    changeNotesFetched = detail?.changeNotes?.isNotBlank() == true,
                    previewImageUrl = detail?.previewImageUrl?.ifBlank { item.previewImageUrl } ?: item.previewImageUrl,
                    versionId = buildModVersionId(remoteUpdatedAtMillis),
                    versionUpdatedAtMillis = remoteUpdatedAtMillis,
                )
            }.onSuccess { entries ->
                var persistedUpdateCheckState: ModLibraryUpdateCheckState? = null
                _uiState.update { state ->
                    val nextState = applyModLibraryEntries(state = state, entries = entries)
                    persistedUpdateCheckState = nextState.modLibraryState.updateCheckState
                    nextState
                }
                persistedUpdateCheckState?.let(::persistModLibraryUpdateStateIfStable)
                _toastMessages.emit("已将 ${item.title} 添加到模组库。")
            }.onFailure { error ->
                _toastMessages.emit(error.message ?: "添加到模组库失败。")
                refreshModLibrary(showLoading = false)
            }
        }
    }

    fun downloadItems(items: List<WorkshopBrowseItem>): Boolean {
        val distinctItems = items.distinctBy(WorkshopBrowseItem::downloadKey)
        if (distinctItems.isEmpty()) {
            return false
        }
        if (steamAuthRepository.activeAccountRequiresReauthentication()) {
            viewModelScope.launch {
                _toastMessages.emit("当前 Steam 账号需要重新认证，新的下载任务暂时不能开始。")
            }
            return false
        }

        val downloadBinding = steamAuthRepository.currentDownloadBinding()
        val enqueuedCount = distinctItems
            .groupBy(WorkshopBrowseItem::appId)
            .entries
            .sumOf { (appId, groupedItems) ->
                downloadCenterManager.enqueueDownloads(
                    appId = appId,
                    gameTitle = resolveGameTitleForDownload(appId),
                    targets = groupedItems.map { groupedItem ->
                        DownloadCenterManager.QueueTarget(
                            publishedFileId = groupedItem.publishedFileId,
                            itemTitle = groupedItem.title,
                            boundAccountId = downloadBinding.accountId,
                            boundAccountName = downloadBinding.accountName,
                        )
                    },
                )
            }

        if (enqueuedCount <= 0) {
            return false
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
        return true
    }

    suspend fun loadRequiredItemsForDownload(item: WorkshopBrowseItem): List<WorkshopRequiredItem> {
        val downloadedItemKeys = _uiState.value.modLibraryState.items
            .asSequence()
            .filter(DownloadedModGroup::hasStoredVersions)
            .map { group -> group.appId to group.publishedFileId }
            .toSet()
        val activeDownloadItemKeys = _uiState.value.downloadCenterState.activeTasks
            .map(DownloadCenterTaskUiState::downloadKey)
            .toSet()
        val currentDetail = _uiState.value.workshopItemDetailState
            ?.takeIf { detailState ->
                detailState.item.appId == item.appId &&
                    detailState.item.publishedFileId == item.publishedFileId
            }
            ?.detail
        if (currentDetail != null) {
            return currentDetail.requiredItems.filterPendingRequiredItems(
                downloadedItemKeys = downloadedItemKeys,
                activeDownloadItemKeys = activeDownloadItemKeys,
            )
        }

        return runCatching {
            withTimeout(MAIN_SCREEN_TIMEOUT_MS) {
                detailRepository.loadWorkshopItemDetail(item).requiredItems.filterPendingRequiredItems(
                    downloadedItemKeys = downloadedItemKeys,
                    activeDownloadItemKeys = activeDownloadItemKeys,
                )
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun applyAdbCommand(command: AdbCommand) {
        when (command) {
            is AdbDownloadCommand -> applyAdbDownloadCommand(command)
            is AdbWorkshopSearchProbeCommand -> applyAdbWorkshopSearchProbeCommand(command)
        }
    }

    private fun applyAdbDownloadCommand(command: AdbDownloadCommand) {
        workshopLogInfo(
            "ADB command received appId=${command.appIdText} publishedFileId=${command.publishedFileIdText} autoStart=${command.autoStart}",
        )

        if (!command.autoStart) {
            return
        }

        val validationError = WorkshopInputValidator.validate(command.appIdText, command.publishedFileIdText)
        if (validationError != null) {
            workshopLogWarn("ADB command rejected: $validationError")
            return
        }

        val appId = command.appIdText.toUInt()
        val publishedFileId = requireNotNull(
            WorkshopPublishedFileIdParser.parse(command.publishedFileIdText),
        )
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
        workshopLogInfo(
            "ADB download task enqueued count=$enqueued appId=$appId publishedFileId=$publishedFileId",
        )
    }

    private fun applyAdbWorkshopSearchProbeCommand(command: AdbWorkshopSearchProbeCommand) {
        workshopLogInfo(
            "ADB workshop search probe received appId=${command.appIdText} searchQuery=${command.searchQuery} expectedPublishedFileId=${command.expectedPublishedFileIdText.ifBlank { "-" }}",
        )

        val appId = command.appIdText.toUIntOrNull()
        if (appId == null) {
            workshopLogWarn("ADB workshop search probe rejected: invalid appId=${command.appIdText}")
            return
        }
        val expectedPublishedFileId = command.expectedPublishedFileIdText
            .takeIf(String::isNotBlank)
            ?.toULongOrNull()
        if (command.expectedPublishedFileIdText.isNotBlank() && expectedPublishedFileId == null) {
            workshopLogWarn(
                "ADB workshop search probe rejected: invalid expectedPublishedFileId=${command.expectedPublishedFileIdText}",
            )
            return
        }
        val normalizedQuery = command.searchQuery.trim()
        if (normalizedQuery.isBlank()) {
            workshopLogWarn("ADB workshop search probe rejected: empty search query")
            return
        }

        viewModelScope.launch {
            runCatching {
                withTimeout(MAIN_SCREEN_TIMEOUT_MS * 2) {
                    browseWorkshopPage(
                        appId = appId,
                        searchQuery = normalizedQuery,
                        sortOption = WorkshopBrowseSortOption.MostPopular,
                        timeWindow = WorkshopBrowseTimeWindow.OneWeek,
                        page = 1,
                    )
                }
            }.onSuccess { page ->
                val topItems = page.items.take(10).joinToString(" | ") { item ->
                    "${item.publishedFileId}:${item.title.take(32)}"
                }
                val expectedFound = expectedPublishedFileId?.let { publishedFileId ->
                    page.items.any { item -> item.publishedFileId == publishedFileId }
                }
                workshopLogInfo(
                    "ADB workshop search probe result appId=$appId query=$normalizedQuery itemCount=${page.items.size} hasNext=${page.hasNextPage} expectedId=${expectedPublishedFileId ?: "-"} expectedFound=${expectedFound ?: "n/a"} topItems=$topItems",
                )
            }.onFailure { error ->
                workshopLogWarn(
                    "ADB workshop search probe failed appId=$appId query=$normalizedQuery errorType=${error.javaClass.simpleName} errorMessage=${error.message}",
                    error,
                )
            }
        }
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
                var persistedUpdateCheckState: ModLibraryUpdateCheckState? = null
                _uiState.update { state ->
                    val nextState = applyModLibraryEntries(
                        state = state,
                        entries = entries,
                        isLoading = false,
                        errorMessage = null,
                    )
                    persistedUpdateCheckState = nextState.modLibraryState.updateCheckState
                    nextState
                }
                persistedUpdateCheckState?.let(::persistModLibraryUpdateStateIfStable)
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
                withTimeout(WORKSHOP_BROWSE_TIMEOUT_MS) {
                    browseWorkshopPage(
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
                workshopLogWarn(
                    "Workshop browse failed appId=${game.appId} page=$page query=${searchQuery.trim()} directAccess=${
                        ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessAllowed(
                            settingsRepository.isExperimentalWorkshopDirectAccessEnabled(),
                        )
                    } error=${error::class.java.simpleName}:${error.message}",
                    error,
                )
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
    ): Boolean {
        if (steamAuthRepository.activeAccountRequiresReauthentication()) {
            viewModelScope.launch {
                _toastMessages.emit("当前 Steam 账号需要重新认证，新的下载任务暂时不能开始。")
            }
            return false
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
            return false
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
        return true
    }

    private fun resolveGameTitleForDownload(appId: UInt): String =
        _uiState.value.gameWorkshopState
            ?.game
            ?.takeIf { game -> game.appId == appId }
            ?.name
            ?: _uiState.value.libraryGames.firstOrNull { game -> game.appId == appId }?.name
            ?: _uiState.value.modLibraryState.items.firstOrNull { group -> group.appId == appId }?.gameTitle
            ?: "Workshop"

    private fun resolveGameTitleForTranslation(appId: UInt): String? =
        _uiState.value.gameWorkshopState
            ?.game
            ?.takeIf { game -> game.appId == appId }
            ?.name
            ?: _uiState.value.libraryGames.firstOrNull { game -> game.appId == appId }?.name
            ?: _uiState.value.modLibraryState.items.firstOrNull { group -> group.appId == appId }?.gameTitle

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
        if (error is SteamAuthenticatedCleartextBlockedException) {
            error.message ?: fallbackMessage
        } else if (error.isTimeoutRequestFailure()) {
            REQUEST_TIMEOUT_MESSAGE
        } else {
            fallbackMessage
        }

    private fun workshopRequestFailureMessage(
        error: Throwable,
        fallbackMessage: String,
    ): String =
        if (error is SteamAuthenticatedCleartextBlockedException) {
            error.message ?: fallbackMessage
        } else if (error.isWorkshopConnectionFailure()) {
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
        val downloadOptions = UpdateSource
            .oneShotDownloadSelectionSources(resolvedDownload.source)
            .distinctBy(UpdateSource::id)
            .map { source ->
                UpdateDownloadOptionState(
                    label = if (source == UpdateSource.OFFICIAL) {
                        "GitHub Release（直链）"
                    } else {
                        source.displayName
                    },
                    url = source.buildUrl(release.assetDownloadUrl),
                    source = source,
                )
            }
        return UpdatePromptState(
            currentVersion = BuildConfig.VERSION_NAME,
            latestVersion = release.normalizedVersion,
            publishedAtText = release.publishedAtDisplayText.ifBlank { "未知" },
            downloadSourceDisplayName = resolvedDownload.source.displayName,
            notesText = release.notesText.ifBlank { "暂无更新说明。" },
            downloadUrl = resolvedDownload.resolvedUrl,
            defaultDownloadSourceId = resolvedDownload.source.id,
            downloadOptions = downloadOptions,
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

    private fun ensureSteamLoginAttempt(dialog: SteamLoginDialogUiState): String {
        activeSteamLoginAttemptId?.let { return it }
        val attemptId = UUID.randomUUID().toString()
        activeSteamLoginAttemptId = attemptId
        appendSteamLoginDebugLine(
            "UI: started Steam login attempt mode=${dialog.mode.name} inputMode=${dialog.inputMode.name} " +
                "challenge=${dialog.challengeType?.name ?: "None"} accountHint=${dialog.username.maskSteamLoginValue()} " +
                "targetAccountPresent=${dialog.targetAccountId != null}.",
        )
        return attemptId
    }

    private fun appendSteamLoginDebugLine(line: String) {
        val attemptId = activeSteamLoginAttemptId ?: return
        runCatching {
            workshopLogInfo("STEAM_LOGIN id=$attemptId $line")
        }
    }

    private fun appendSteamLoginFailure(
        summary: String,
        error: Throwable,
    ) {
        val attemptId = activeSteamLoginAttemptId ?: return
        runCatching {
            workshopLogWarn("STEAM_LOGIN id=$attemptId $summary", error)
        }
        activeSteamLoginAttemptId = null
    }

    private fun finishSteamLoginAttempt(summary: String? = null) {
        val attemptId = activeSteamLoginAttemptId ?: return
        summary?.let {
            runCatching {
                workshopLogInfo("STEAM_LOGIN id=$attemptId $it")
            }
        }
        activeSteamLoginAttemptId = null
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
                            isPollingConfirmation = dialog.isPollingConfirmation && submitting,
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
                cancelSteamConfirmationWaitJob()
                appendSteamLoginDebugLine(
                    "UI: Steam requires guard code type=${step.challenge.type.name} messagePresent=${!step.challenge.message.isNullOrBlank()}.",
                )
                _uiState.update { state ->
                    val dialog = state.settingsState.steamAuthState.loginDialogState ?: SteamLoginDialogUiState()
                    state.copy(
                        settingsState = state.settingsState.copy(
                            steamAuthState = state.settingsState.steamAuthState.copy(
                                loginDialogState = dialog.copy(
                                    inputMode = SteamLoginInputMode.Credentials,
                                    password = "",
                                    challengeType = step.challenge.type,
                                    challengeMessage = step.challenge.message,
                                    isPollingConfirmation = false,
                                    isSubmitting = false,
                                    errorMessage = null,
                                ),
                            ),
                        ),
                    )
                }
            }

            is SteamSignInStep.AwaitingConfirmation -> {
                cancelSteamConfirmationWaitJob()
                appendSteamLoginDebugLine(
                    "UI: Steam requires confirmation type=${step.challenge.type.name} messagePresent=${!step.challenge.message.isNullOrBlank()}.",
                )
                _uiState.update { state ->
                    val dialog = state.settingsState.steamAuthState.loginDialogState ?: SteamLoginDialogUiState()
                    state.copy(
                        settingsState = state.settingsState.copy(
                            steamAuthState = state.settingsState.steamAuthState.copy(
                                loginDialogState = dialog.copy(
                                    inputMode = SteamLoginInputMode.Credentials,
                                    password = "",
                                    challengeType = step.challenge.type,
                                    challengeMessage = step.challenge.message,
                                    isPollingConfirmation = true,
                                    isSubmitting = false,
                                    errorMessage = null,
                                ),
                            ),
                        ),
                    )
                }
                startSteamConfirmationWait()
            }

            is SteamSignInStep.Success -> {
                cancelSteamConfirmationWaitJob()
                appendSteamLoginDebugLine(
                    "UI: Steam login succeeded account=${step.account.accountName.maskSteamLoginValue()} steamId=${step.account.steamId}.",
                )
                finishSteamLoginAttempt("UI: Steam login flow finished successfully.")
                syncSteamAuthState(
                    message = "已登录 ${step.account.accountName}。",
                    loginDialogState = null,
                )
                primeSteamWebSessionAsync(force = true)
            }
        }
    }

    private fun primeSteamWebSessionAsync(force: Boolean = false) {
        if (force) {
            isSteamWebSessionPrimed = false
        }
        viewModelScope.launch {
            runCatching {
                ensureSteamWebSessionPrimed()
            }
        }
    }

    private suspend fun browseWorkshopPage(
        appId: UInt,
        searchQuery: String,
        sortOption: WorkshopBrowseSortOption,
        timeWindow: WorkshopBrowseTimeWindow,
        page: Int,
    ) =
        browseWorkshopPageWithAuthenticatedFallback(
            appId = appId,
            searchQuery = searchQuery.trim(),
            sortOption = sortOption,
            timeWindow = timeWindow,
            page = page,
        )

    private suspend fun browseWorkshopPageWithAuthenticatedFallback(
        appId: UInt,
        searchQuery: String,
        sortOption: WorkshopBrowseSortOption,
        timeWindow: WorkshopBrowseTimeWindow,
        page: Int,
    ): top.apricityx.workshop.data.WorkshopBrowsePage {
        if (searchQuery.isNotBlank()) {
            val accountId = steamAuthRepository.activeAccountId()
            val publishedFileLanguage = settingsRepository.getSteamLanguagePreference().toSteamPublishedFileLanguage()
            val authenticatedResult = runCatching {
                steamAuthRepository.queryPublishedFiles(
                    accountId = accountId,
                    query = SteamPublishedFileQuery(
                        appId = appId,
                        searchText = searchQuery,
                        page = page,
                        pageSize = WORKSHOP_ITEMS_PER_PAGE,
                        queryType = STEAM_PUBLISHED_FILE_QUERY_TYPE_RANKED_BY_TEXT_SEARCH,
                        language = publishedFileLanguage,
                    ),
                )
            }.onFailure { error ->
                workshopLogWarn(
                    "Workshop authenticated published-file query failed; falling back to community browse appId=$appId page=$page query=$searchQuery language=$publishedFileLanguage error=${error.message}",
                    error,
                )
            }.getOrNull()
            if (authenticatedResult != null) {
                workshopLogInfo(
                    "Workshop search using authenticated published-file query appId=$appId page=$page query=$searchQuery language=$publishedFileLanguage total=${authenticatedResult.total} returned=${authenticatedResult.items.size}",
                )
                return authenticatedResult.toWorkshopBrowsePage(
                    page = page,
                    pageSize = WORKSHOP_ITEMS_PER_PAGE,
                )
            }
        }

        ensureSteamWebSessionPrimed()
        val browseUrl = buildWorkshopBrowseUrl(
            appId = appId,
            searchQuery = searchQuery,
            sortOption = sortOption,
            timeWindow = timeWindow,
            page = page,
        )
        if (searchQuery.isNotBlank()) {
            logSteamWebCookiesForUrl("community-browse-before-search", browseUrl)
        }
        val pageResult = browseRepository.browseGameWorkshop(
            appId = appId,
            searchQuery = searchQuery,
            sortOption = sortOption,
            timeWindow = timeWindow,
            page = page,
        )
        if (searchQuery.isNotBlank()) {
            logSteamWebCookiesForUrl("community-browse-after-search", browseUrl)
        }
        return pageResult
    }

    private suspend fun ensureSteamWebSessionPrimed() {
        val accountId = steamAuthRepository.activeAccountId()
        val hasAuthenticatedSteamSession = accountId
            ?.let(steamAuthRepository::accountSessionFor) != null
        val appContext = getApplication<Application>()
        val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
        val userManager = appContext.getSystemService(UserManager::class.java)
        workshopLogInfo(
            "Steam web session prime start accountIdPresent=${accountId != null} hasAuthenticatedSession=$hasAuthenticatedSteamSession deviceLocked=${keyguardManager?.isDeviceLocked} userUnlocked=${userManager?.isUserUnlocked}",
        )
        if (!hasAuthenticatedSteamSession) {
            primedSteamWebSessionAccountId = accountId
            isSteamWebSessionPrimed = true
            return
        }
        if (isSteamWebSessionPrimed && primedSteamWebSessionAccountId == accountId) {
            return
        }
        val storePreferencesUrl = "https://store.steampowered.com/account/preferences/".toHttpUrl()
        val communityLoginUrl = "https://steamcommunity.com/login/home/?goto=workshop%2F".toHttpUrl()
        withContext(Dispatchers.IO) {
            listOf(storePreferencesUrl, communityLoginUrl).forEach { url ->
                runCatching {
                    primeSteamWebSessionUrl(url)
                }.onFailure { error ->
                    workshopLogWarn(
                        "Steam web session prime skipped for host=${url.host} error=${error.message}",
                    )
                }
            }
            logSteamWebCookiesForUrl("store-after-prime", storePreferencesUrl)
            logSteamWebCookiesForUrl("community-after-prime", communityLoginUrl)
        }
        primedSteamWebSessionAccountId = accountId
        isSteamWebSessionPrimed = true
    }

    private fun primeSteamWebSessionUrl(url: HttpUrl) {
        httpClient.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", STEAM_WEB_SESSION_USER_AGENT)
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) {
                error("Steam web session prime request failed: ${response.code}")
            }
        }
    }

    private fun logSteamWebCookiesForUrl(
        label: String,
        url: HttpUrl,
    ) {
        val cookieSummary = steamWebCookieJar.loadForRequest(url)
            .sortedWith(compareBy({ it.name }, { it.domain }, { it.path }))
            .joinToString(",") { cookie -> "${cookie.name}@${cookie.domain}${cookie.path}" }
            .ifBlank { "-" }
        workshopLogInfo(
            "Steam web cookies[$label] host=${url.host} count=${steamWebCookieJar.loadForRequest(url).size} entries=$cookieSummary",
        )
    }

    private fun buildWorkshopBrowseUrl(
        appId: UInt,
        searchQuery: String,
        sortOption: WorkshopBrowseSortOption,
        timeWindow: WorkshopBrowseTimeWindow,
        page: Int,
    ): HttpUrl =
        "https://steamcommunity.com/".toHttpUrl()
            .newBuilder()
            .addPathSegments("workshop/browse/")
            .addQueryParameter("appid", appId.toString())
            .addQueryParameter("searchtext", searchQuery)
            .addQueryParameter("childpublishedfileid", "0")
            .addQueryParameter("l", settingsRepository.getSteamLanguagePreference().requestValue)
            .addQueryParameter("browsesort", sortOption.browseSortValue)
            .addQueryParameter("section", "readytouseitems")
            .addQueryParameter("actualsort", sortOption.actualSortValue)
            .addQueryParameter("p", page.toString())
            .addQueryParameter("numperpage", WORKSHOP_ITEMS_PER_PAGE.toString())
            .apply {
                if (sortOption.supportsTimeWindow) {
                    addQueryParameter("days", timeWindow.daysValue.toString())
                }
            }
            .build()

    private fun performSteamWebTransferLogin(
        storeSessionId: String,
        webLoginContext: SteamWebLoginContext,
        redirectUrl: String,
    ): Boolean {
        val sanitizedRedirectUrl = sanitizeSteamTransferLoginRedirect(redirectUrl)
        val finalizeLoginBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("nonce", webLoginContext.accessToken)
            .addFormDataPart("sessionid", storeSessionId)
            .addFormDataPart("redir", sanitizedRedirectUrl)
            .build()
        val finalizeLoginResponse = httpClient.newCall(
            Request.Builder()
                .url("https://login.steampowered.com/jwt/finalizelogin")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://store.steampowered.com")
                .header("Referer", sanitizedRedirectUrl)
                .header("User-Agent", STEAM_WEB_SESSION_USER_AGENT)
                .post(finalizeLoginBody)
                .build(),
        ).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Steam finalizelogin request failed: ${response.code}")
            }
            val payloadSummary = summarizeSteamFinalizeLoginPayload(payload)
            workshopLogInfo(
                "Steam finalizelogin response status=${response.code} contentType=${response.body?.contentType()} summary=$payloadSummary",
            )
            parseSteamFinalizeLoginResponse(payload)
        }
        val finalizedSteamId = finalizeLoginResponse.steamId
            ?.takeIf(String::isNotBlank)
            ?: webLoginContext.steamId.toString().also {
                workshopLogWarn(
                    "Steam finalizelogin response did not include steamID; falling back to authenticated account steamId.",
                )
            }
        if (finalizeLoginResponse.transferInfo.isEmpty()) {
            error("Steam finalizelogin response did not include transfer_info.")
        }

        var allTransfersSucceeded = true
        finalizeLoginResponse.transferInfo.forEach { transferInfo ->
            val transferUrl = runCatching { transferInfo.url.toHttpUrl() }
                .getOrElse { error ->
                    allTransfersSucceeded = false
                    workshopLogWarn(
                        "Steam transfer-login returned an invalid transfer URL: ${transferInfo.url}",
                        error,
                    )
                    return@forEach
                }
            val transferBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
            transferInfo.params.forEach { (key, value) ->
                transferBodyBuilder.addFormDataPart(key, value)
            }
            transferBodyBuilder.addFormDataPart("steamID", finalizedSteamId)

            httpClient.newCall(
                Request.Builder()
                    .url(transferUrl)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", "https://store.steampowered.com")
                    .header("Referer", sanitizedRedirectUrl)
                    .header("User-Agent", STEAM_WEB_SESSION_USER_AGENT)
                    .post(transferBodyBuilder.build())
                    .build(),
            ).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    allTransfersSucceeded = false
                    workshopLogWarn(
                        "Steam transfer-login failed for ${transferUrl.host} with status=${response.code}.",
                    )
                    return@use
                }
                val responseCookies = response.headers("Set-Cookie")
                    .mapNotNull { header -> header.substringBefore("=").takeIf(String::isNotBlank) }
                    .distinct()
                    .joinToString(",")
                    .ifBlank { "-" }
                val transferResult = parseSteamSetTokenResult(payload)
                workshopLogInfo(
                    "Steam transfer-login response host=${transferUrl.host} status=${response.code} result=${transferResult ?: "blank"} setCookieNames=$responseCookies",
                )
                if (transferResult != null && transferResult != 1) {
                    allTransfersSucceeded = false
                    workshopLogWarn(
                        "Steam transfer-login returned result=$transferResult for ${transferUrl.host}.",
                    )
                }
            }
        }
        return allTransfersSucceeded
    }

    private fun startSteamConfirmationWait() {
        cancelSteamConfirmationWaitJob()
        appendSteamLoginDebugLine("UI: starting background wait for Steam confirmation.")
        steamConfirmationWaitJob = viewModelScope.launch {
            runCatching {
                steamAuthRepository.waitForPendingConfirmation(
                    debugLogger = ::appendSteamLoginDebugLine,
                )
            }
                .onSuccess(::applySteamSignInStep)
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    appendSteamLoginFailure("UI: Steam confirmation wait failed.", error)
                    _uiState.update { state ->
                        val dialog = state.settingsState.steamAuthState.loginDialogState ?: return@update state
                        state.copy(
                            settingsState = state.settingsState.copy(
                                steamAuthState = state.settingsState.steamAuthState.copy(
                                    loginDialogState = dialog.copy(
                                        isPollingConfirmation = false,
                                        errorMessage = error.message ?: "Steam 登录失败。",
                                    ),
                                ),
                            ),
                        )
                    }
                }
        }
    }

    private fun cancelSteamConfirmationWaitJob() {
        steamConfirmationWaitJob?.cancel()
        steamConfirmationWaitJob = null
    }

    private fun cancelPendingSteamLoginFlow() {
        cancelPendingSteamLoginFlow(reason = null)
    }

    private fun cancelPendingSteamLoginFlow(reason: String? = null) {
        reason?.let(::appendSteamLoginDebugLine)
        cancelSteamConfirmationWaitJob()
        steamAuthRepository.cancelPendingSignIn()
        if (activeSteamLoginAttemptId != null) {
            finishSteamLoginAttempt()
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
            val rawPrevious = if (
                rememberPrevious &&
                state.currentScreen != screen &&
                state.currentScreen != WorkshopScreenDestination.DownloadCenter
            ) {
                state.currentScreen
            } else {
                state.previousScreen
            }
            val sanitizedPrevious = if (rawPrevious == WorkshopScreenDestination.DownloadTaskDetail) {
                state.previousScreen.takeIf { it != WorkshopScreenDestination.DownloadTaskDetail }
                    ?: WorkshopScreenDestination.GameLibrary
            } else {
                rawPrevious
            }
            state.copy(
                previousScreen = sanitizedPrevious,
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

    private fun WorkshopUiState.updateWorkshopItemDetailState(
        appId: UInt,
        publishedFileId: ULong,
        transform: (WorkshopItemDetailUiState) -> WorkshopItemDetailUiState,
    ): WorkshopUiState {
        val currentDetailState = workshopItemDetailState
        if (currentDetailState?.item?.matches(appId, publishedFileId) == true) {
            return copy(workshopItemDetailState = transform(currentDetailState))
        }

        val backStackIndex = workshopItemDetailBackStack.indexOfLast { detailState ->
            detailState.item.matches(appId, publishedFileId)
        }
        if (backStackIndex < 0) {
            return this
        }

        val updatedBackStack = workshopItemDetailBackStack.toMutableList()
        updatedBackStack[backStackIndex] = transform(updatedBackStack[backStackIndex])
        return copy(workshopItemDetailBackStack = updatedBackStack)
    }

    private fun WorkshopBrowseItem.matches(
        appId: UInt,
        publishedFileId: ULong,
    ): Boolean = this.appId == appId && this.publishedFileId == publishedFileId

    private fun applyModLibraryEntries(
        state: WorkshopUiState,
        entries: List<DownloadedModEntry>,
        isLoading: Boolean = false,
        errorMessage: String? = null,
    ): WorkshopUiState {
        val groupedEntries = entries.groupedForDisplay()
        val selectedEntry = state.modLibraryState.selectedEntry?.let { current ->
            groupedEntries.firstOrNull { it.matches(current) }
        }
        val pendingRenameMod = state.pendingRenameMod?.let { current ->
            groupedEntries.firstOrNull { it.matches(current) }
        }
        val nextScreen = if (state.currentScreen == WorkshopScreenDestination.ModDetail && selectedEntry == null) {
            WorkshopScreenDestination.ModLibrary
        } else {
            state.currentScreen
        }
        val updateCheckState = state.modLibraryState.updateCheckState
            .filterForEntries(groupedEntries.latestVersionsForUpdateCheck())
        val detailDescriptionTranslation = state.modLibraryState.detailDescriptionTranslation.takeIf {
            shouldPreserveModLibraryDescriptionTranslation(
                previous = state.modLibraryState.selectedEntry,
                next = selectedEntry,
            )
        } ?: ModLibraryDescriptionTranslationUiState()
        val changeNotesDialogState = preserveModLibraryChangeNotesDialogState(
            previous = state.modLibraryState.changeNotesDialogState,
            groupedEntries = groupedEntries,
        )
        return state.copy(
            currentScreen = nextScreen,
            pendingRenameMod = pendingRenameMod,
            renameModTitleInput = if (pendingRenameMod == null) {
                ""
            } else {
                state.renameModTitleInput
            },
            modLibraryState = state.modLibraryState.copy(
                items = groupedEntries,
                selectedEntry = selectedEntry,
                detailDescriptionTranslation = detailDescriptionTranslation,
                changeNotesDialogState = changeNotesDialogState,
                updateCheckState = updateCheckState,
                isLoading = isLoading,
                errorMessage = errorMessage,
                message = if (groupedEntries.isEmpty()) {
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

    companion object {
        private const val MAIN_SCREEN_TIMEOUT_MS = DEFAULT_HTTP_TIMEOUT_SECONDS * 1_000L
        private const val WORKSHOP_BROWSE_TIMEOUT_MS = DEFAULT_HTTP_TIMEOUT_SECONDS * 1_000L
        private const val WORKSHOP_COMMENTS_TIMEOUT_MS = DEFAULT_HTTP_TIMEOUT_SECONDS * 1_000L
        private const val WORKSHOP_ITEMS_PER_PAGE = 30
        private const val STEAM_WEB_SESSION_USER_AGENT = "WorkshopOnAndroid/1.0"
        private const val REQUEST_TIMEOUT_MESSAGE = "加载超时，请开启加速器或科学上网后重试。"
        private const val WORKSHOP_CONNECTION_FAILURE_MESSAGE =
            "啊哦，加载超时，您的网络环境可能不支持直连创意工坊，请开启加速器加速 steam 或科学上网后重试。"
        private const val STEAM_DIRECT_ACCESS_FALLBACK_DIALOG_MESSAGE =
            "您的网络环境不支持使用 Steam 加速链路，请使用加速器后重试。\n\n当前已自动回退到 Steam 原始链路。"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkshopViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }

    private fun createInitialUiState(): WorkshopUiState {
        val themeMode = settingsRepository.getThemeMode()
        val frontendMode = settingsRepository.getFrontendMode()
        val steamLanguagePreference = settingsRepository.getSteamLanguagePreference()
        val threadCount = settingsRepository.getDownloadThreadCount()
        val concurrentTaskCount = settingsRepository.getConcurrentDownloadTaskCount()
        val modUpdateConcurrentChecks = settingsRepository.getModUpdateConcurrentCheckCount()
        val savedBaiduCredentials = baiduTranslationCredentialsRepository.getCredentials()
        val hasSavedBaiduCredentials = savedBaiduCredentials.isConfigured()
        val allowSteamAuthenticatedCleartextHttp = settingsRepository.isSteamAuthenticatedCleartextHttpAllowed()
        val experimentalWorkshopDirectAccessEnabled =
            settingsRepository.isExperimentalWorkshopDirectAccessEnabled()
        val application = getApplication<Application>()
        return WorkshopUiState(
            themeMode = themeMode,
            frontendMode = frontendMode,
            modLibraryState = ModLibraryUiState(
                isLoading = true,
                updateCheckState = modLibraryUpdateStateStore.loadState(),
                displayMode = settingsRepository.getModLibraryDisplayMode(),
            ),
            showUsageNoticeDialog = !settingsRepository.hasAcknowledgedUsageNotice(),
            settingsState = SettingsUiState(
                downloadThreadCountInput = threadCount.toString(),
                savedDownloadThreadCount = threadCount,
                concurrentDownloadTaskCountInput = concurrentTaskCount.toString(),
                savedConcurrentDownloadTaskCount = concurrentTaskCount,
                modUpdateConcurrentCheckCountInput = modUpdateConcurrentChecks.toString(),
                savedModUpdateConcurrentCheckCount = modUpdateConcurrentChecks,
                selectedThemeMode = themeMode,
                selectedFrontendMode = frontendMode,
                selectedSteamLanguagePreference = steamLanguagePreference,
                allowSteamAuthenticatedCleartextHttp = allowSteamAuthenticatedCleartextHttp,
                experimentalWorkshopDirectAccessEnabled = experimentalWorkshopDirectAccessEnabled,
                baiduTranslationApiKeyConfigured = hasSavedBaiduCredentials,
                steamAuthState = steamAuthRepository.loadSnapshot().toUiState(),
                autoCheckUpdatesEnabled = settingsRepository.isAutoCheckUpdatesEnabled(),
                preferredUpdateSource = settingsRepository.getPreferredUpdateSource(),
                availableUpdateSources = UpdateSource.userSelectableSources(),
                currentVersionText = BuildConfig.VERSION_NAME,
                updateStatusSummary = buildUpdateStatusSummary(),
                runtimeLogDirectoryPath = AppRuntimeLogManager.logDirectoryPath(application),
                latestRuntimeLogPath = AppRuntimeLogManager.latestLogPath(application),
            ),
            baiduTranslationApiKeyState = BaiduTranslationApiKeyUiState(
                appIdInput = savedBaiduCredentials.appId,
                apiKeyInput = savedBaiduCredentials.apiKey,
                hasSavedCredentials = hasSavedBaiduCredentials,
            ),
        )
    }

    private fun persistModLibraryUpdateStateIfStable(updateCheckState: ModLibraryUpdateCheckState) {
        if (updateCheckState.isChecking) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            modLibraryUpdateStateStore.saveState(updateCheckState)
        }
    }
}

internal fun shouldPreserveModLibraryDescriptionTranslation(
    previous: DownloadedModGroup?,
    next: DownloadedModGroup?,
): Boolean =
    previous != null &&
        next != null &&
        previous.modGroupKey() == next.modGroupKey() &&
        previous.description == next.description

private fun preserveModLibraryChangeNotesDialogState(
    previous: ModLibraryChangeNotesDialogUiState?,
    groupedEntries: List<DownloadedModGroup>,
): ModLibraryChangeNotesDialogUiState? {
    val dialogState = previous ?: return null
    val resolvedGroup = groupedEntries.firstOrNull { it.matches(dialogState.group) } ?: return null
    val resolvedMarkdown = dialogState.markdown.ifBlank { resolvedGroup.changeNotes }
    return dialogState.copy(
        group = resolvedGroup,
        markdown = resolvedMarkdown,
        isLoading = dialogState.isLoading && resolvedMarkdown.isBlank(),
        errorMessage = if (resolvedMarkdown.isNotBlank()) null else dialogState.errorMessage,
    )
}

private fun Throwable.isTimeoutRequestFailure(): Boolean =
    this is SocketTimeoutException || this is TimeoutCancellationException

private fun Throwable.isWorkshopConnectionFailure(): Boolean =
    this is IOException || this is TimeoutCancellationException

private fun String?.maskSteamLoginValue(): String =
    this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { value ->
            when {
                value.length <= 2 -> "*".repeat(value.length)
                else -> "${value.first()}***${value.last()}"
            }
        }
        ?: "-"

private fun DownloadedModEntry.toWorkshopBrowseItem(): WorkshopBrowseItem =
    WorkshopBrowseItem(
        appId = appId,
        publishedFileId = publishedFileId,
        title = itemTitle,
        authorName = "",
        previewImageUrl = previewImageUrl,
        descriptionSnippet = description,
    )

private fun DownloadedModGroup.toWorkshopBrowseItem(): WorkshopBrowseItem =
    WorkshopBrowseItem(
        appId = appId,
        publishedFileId = publishedFileId,
        title = itemTitle,
        authorName = "",
        previewImageUrl = previewImageUrl,
        descriptionSnippet = description,
    )

private val WorkshopBrowseItem.downloadKey: Pair<UInt, ULong>
    get() = appId to publishedFileId

private fun top.apricityx.workshop.data.WorkshopItemDetail.shouldLoadWorkshopComments(): Boolean =
    commentThreadContext != null && commentCount != 0L

private fun top.apricityx.workshop.data.WorkshopItemDetail.commentUnavailableMessage(): String? =
    when {
        commentCount == 0L -> null
        commentThreadContext == null -> "暂时无法读取评论内容，你可以直接在 Steam 中打开对应评论页查看。"
        else -> null
    }

private val DownloadCenterTaskUiState.downloadKey: Pair<UInt, ULong>
    get() = appId to publishedFileId

private fun List<WorkshopRequiredItem>.filterPendingRequiredItems(
    downloadedItemKeys: Set<Pair<UInt, ULong>>,
    activeDownloadItemKeys: Set<Pair<UInt, ULong>>,
): List<WorkshopRequiredItem> =
    filterNot { requiredItem ->
        (requiredItem.appId to requiredItem.publishedFileId) in downloadedItemKeys ||
            (requiredItem.appId to requiredItem.publishedFileId) in activeDownloadItemKeys
    }

private const val BAIDU_AUTO_DETECT_LANGUAGE = "auto"
private const val BAIDU_DEFAULT_TARGET_LANGUAGE = "zh"






