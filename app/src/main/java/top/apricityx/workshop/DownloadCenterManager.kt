package top.apricityx.workshop

import android.app.Application
import com.elvishew.xlog.XLog.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import top.apricityx.workshop.workshop.DownloadEvent
import top.apricityx.workshop.workshop.DownloadState
import top.apricityx.workshop.workshop.WorkshopDownloadEngine
import top.apricityx.workshop.workshop.WorkshopDownloadRequest
import top.apricityx.workshop.steam.protocol.CmServer
import top.apricityx.workshop.steam.protocol.OkHttpSteamCmSession
import top.apricityx.workshop.steam.protocol.SessionContext
import top.apricityx.workshop.steam.protocol.SteamAccountSession
import top.apricityx.workshop.steam.protocol.SteamCmSession

class DownloadCenterManager private constructor(
    private val application: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debugLogManager = DownloadDebugLogManager(application)
    private val taskFinalizer = DownloadCenterTaskFinalizer(application)
    private val settingsRepository = DownloadSettingsRepository(application)
    private val steamAuthRepository = SteamAuthRepository(application)
    private val store = DownloadCenterStore(File(application.filesDir, "download-center/tasks.json"))
    private val _uiState = MutableStateFlow(
        DownloadCenterUiState(tasks = recoverPersistedTasks(store.loadTasks())),
    )
    val uiState: StateFlow<DownloadCenterUiState> = _uiState.asStateFlow()

    private val progressSamples = mutableMapOf<String, ProgressRateSample>()
    private val runningTaskJobs = mutableMapOf<String, Job>()

    @Volatile
    private var runnerJob: Job? = null

    private var persistJob: Job? = null

    init {
        persistNow()
        if (_uiState.value.tasks.any { it.status == DownloadCenterTaskStatus.Queued }) {
            ensureRunner()
        }
    }

    fun enqueueDownloads(
        appId: UInt,
        gameTitle: String,
        targets: List<QueueTarget>,
    ): Int {
        if (targets.isEmpty()) {
            return 0
        }

        val now = System.currentTimeMillis()
        val newTasks = targets.mapIndexed { index, target ->
            val taskId = UUID.randomUUID().toString()
            DownloadCenterTaskUiState(
                id = taskId,
                appId = appId,
                publishedFileId = target.publishedFileId,
                gameTitle = gameTitle,
                itemTitle = target.itemTitle,
                boundAccountId = target.boundAccountId,
                boundAccountName = target.boundAccountName,
                status = DownloadCenterTaskStatus.Queued,
                logs = listOf(
                    "已加入下载队列。",
                    "绑定账号：${target.boundAccountName}",
                    "调试日志：${debugLogManager.logFilePath(taskId)}",
                ),
                enqueuedAtMillis = now + index,
                updatedAtMillis = now + index,
            )
        }

        mutateState { state ->
            state.copy(tasks = newTasks + state.tasks)
        }

        newTasks.forEach { task ->
            debugLogManager.initializeTaskLog(task)
            task.logs.forEach { line ->
                debugLogManager.append(task.id, line)
            }
            debugLogManager.append(
                task.id,
                "Task queued appId=${task.appId} publishedFileId=${task.publishedFileId} title=${task.itemTitle} account=${task.boundAccountName}",
            )
            Log.i(
                WorkshopAppContract.logTag,
                "Queued download task appId=${task.appId} publishedFileId=${task.publishedFileId} title=${task.itemTitle} account=${task.boundAccountName}",
            )
        }
        ensureRunner()
        return newTasks.size
    }

    fun pauseTask(taskId: String) {
        val task = _uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != DownloadCenterTaskStatus.Queued && task.status != DownloadCenterTaskStatus.Running) {
            return
        }

        clearProgressSample(taskId)
        updateTask(taskId) {
            it.copy(
                status = DownloadCenterTaskStatus.Paused,
                phase = DownloadState.Paused,
                errorMessage = null,
                progress = it.progress.copy(speedBytesPerSecond = null),
                logs = (it.logs + "任务已暂停，可稍后继续下载。").takeLast(MAX_LOG_LINES),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        debugLogManager.append(taskId, "Task paused by user")

        if (task.status == DownloadCenterTaskStatus.Running) {
            cancelRunningTask(taskId, "Paused by user")
        }
    }

    fun resumeTask(taskId: String) {
        val task = _uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != DownloadCenterTaskStatus.Paused && task.status != DownloadCenterTaskStatus.Failed) {
            return
        }

        val retryBinding = if (task.status == DownloadCenterTaskStatus.Failed) {
            steamAuthRepository.currentDownloadBinding()
        } else {
            SteamDownloadBinding(
                accountId = task.boundAccountId,
                accountName = task.boundAccountName,
            )
        }
        val bindingChanged = task.status == DownloadCenterTaskStatus.Failed &&
            (
                task.boundAccountId != retryBinding.accountId ||
                    task.boundAccountName != retryBinding.accountName
            )
        val retryBindingLog = if (bindingChanged) {
            "重试时已切换为当前账号：${retryBinding.accountName}。"
        } else {
            null
        }
        val queueResumeLog = if (task.status == DownloadCenterTaskStatus.Failed) {
            "任务已重新加入队列，重试时会使用当前登录账号继续下载。"
        } else {
            "任务已重新加入队列，将从已缓存的进度继续。"
        }

        clearProgressSample(taskId)
        updateTask(taskId) {
            it.copy(
                boundAccountId = retryBinding.accountId,
                boundAccountName = retryBinding.accountName,
                status = DownloadCenterTaskStatus.Queued,
                phase = DownloadState.Idle,
                errorMessage = null,
                progress = it.progress.copy(speedBytesPerSecond = null),
                logs = (it.logs + listOfNotNull(retryBindingLog, queueResumeLog)).takeLast(MAX_LOG_LINES),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        retryBindingLog?.let { debugLogManager.append(taskId, it) }
        debugLogManager.append(taskId, "Retry binding account=${retryBinding.accountName} id=${retryBinding.accountId ?: "anonymous"}")
        debugLogManager.append(taskId, "Task resumed and re-queued")
        ensureRunner()
    }

    fun removeTask(taskId: String) {
        val task = _uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        clearProgressSample(taskId)
        val wasActiveTask = isTaskRunning(taskId)

        if (wasActiveTask) {
            cancelRunningTask(taskId, "Canceled by user")
        }
        debugLogManager.append(taskId, "Task removed from download center")

        mutateState { state ->
            state.copy(tasks = state.tasks.filterNot { it.id == taskId })
        }

        runCatching {
            stagingDirFor(task).deleteRecursively()
        }.onFailure { error ->
            Log.w(
                WorkshopAppContract.logTag,
                "Failed to delete staging files for taskId=$taskId",
                error,
            )
        }

        if (!wasActiveTask && _uiState.value.tasks.any { it.status == DownloadCenterTaskStatus.Queued }) {
            ensureRunner()
        }
    }

    fun clearFinishedTasks() {
        mutateState { state ->
            state.copy(
                tasks = state.tasks.filterNot {
                    it.status == DownloadCenterTaskStatus.Success || it.status == DownloadCenterTaskStatus.Failed
                },
            )
        }
    }

    fun clearExportedFilesForMod(entry: DownloadedModEntry) {
        mutateState { state ->
            state.clearExportedFilesForMod(
                entry = entry,
            )
        }
    }

    fun hasRecoverableTasksForAccount(accountId: String): Boolean =
        _uiState.value.tasks.any { task ->
            task.boundAccountId == accountId &&
                (
                    task.status == DownloadCenterTaskStatus.Queued ||
                        task.status == DownloadCenterTaskStatus.Running ||
                        task.status == DownloadCenterTaskStatus.Paused ||
                        task.status == DownloadCenterTaskStatus.Failed
                )
        }

    private fun ensureRunner() {
        if (runnerJob?.isActive == true) {
            return
        }

        synchronized(this) {
            if (runnerJob?.isActive == true) {
                return
            }

            runnerJob = scope.launch {
                while (true) {
                    cleanupFinishedTaskJobs()
                    val maxConcurrentTasks = settingsRepository.getConcurrentDownloadTaskCount()
                    val availableSlots = (maxConcurrentTasks - runningTaskCount()).coerceAtLeast(0)
                    if (availableSlots > 0) {
                        nextQueuedTasks(limit = availableSlots).forEach(::startTask)
                    }

                    if (runningTaskCount() == 0 && !_uiState.value.tasks.any { it.status == DownloadCenterTaskStatus.Queued }) {
                        break
                    }

                    delay(RUNNER_POLL_INTERVAL_MILLIS)
                }
            }
        }
    }

    private suspend fun processTask(task: DownloadCenterTaskUiState) {
        clearProgressSample(task.id)
        updateTask(task.id) {
            it.copy(
                status = DownloadCenterTaskStatus.Running,
                phase = DownloadState.Resolving,
                errorMessage = null,
                progress = it.progress.copy(speedBytesPerSecond = null),
                logs = (it.logs + "开始下载。").takeLast(MAX_LOG_LINES),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        Log.i(
            WorkshopAppContract.logTag,
            "Task started appId=${task.appId} publishedFileId=${task.publishedFileId} title=${task.itemTitle}",
        )
        debugLogManager.append(
            task.id,
            "Task started appId=${task.appId} publishedFileId=${task.publishedFileId} title=${task.itemTitle}",
        )

        val outputDir = File(application.filesDir, "workshop/${task.appId}/${task.publishedFileId}")
        debugLogManager.append(task.id, "Staging directory: ${outputDir.absolutePath}")
        debugLogManager.append(
            task.id,
            "Download settings threads=${settingsRepository.getDownloadThreadCount()} concurrentTasks=${settingsRepository.getConcurrentDownloadTaskCount()}",
        )
        debugLogManager.append(task.id, "Bound account id=${task.boundAccountId ?: "anonymous"} name=${task.boundAccountName}")
        debugLogManager.append(task.id, "Steam CM websocket client=OkHttp 5 default path")
        debugLogManager.append(task.id, "Runtime app log dir=${AppRuntimeLogManager.logDirectoryPath(application)}")
        debugLogManager.append(task.id, "Runtime crash log=${AppRuntimeLogManager.crashLogPath(application)}")
        var taskSucceeded = false
        val accountSession = steamAuthRepository.accountSessionFor(task.boundAccountId)
        if (task.boundAccountId != null && accountSession == null) {
            val message = "绑定的 Steam 账号已失效，请到设置里重新认证后再重试。"
            clearProgressSample(task.id)
            updateTask(task.id) {
                it.copy(
                    status = DownloadCenterTaskStatus.Failed,
                    phase = DownloadState.Failed,
                    errorMessage = message,
                    progress = it.progress.copy(speedBytesPerSecond = null),
                    logs = (it.logs + message).takeLast(MAX_LOG_LINES),
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            debugLogManager.append(task.id, message)
            return
        }
        val taskClient = OkHttpClient.Builder()
            .addInterceptor(
                SteamCookieInterceptor(
                    authRepository = steamAuthRepository,
                    accountIdProvider = { task.boundAccountId },
                    fallbackToActiveAccount = false,
                ),
            )
            .build()
        val engine = WorkshopDownloadEngine.createDefault(
            client = taskClient,
            sessionFactory = { OkHttpSteamCmSession(taskClient) },
            sessionConnector = buildSessionConnector(accountSession),
            maxConcurrentChunks = settingsRepository.getDownloadThreadCount(),
            allowPublicCdnFallbackOnSessionFailure = accountSession == null,
        )

        try {
            engine.download(
                WorkshopDownloadRequest(
                    appId = task.appId,
                    publishedFileId = task.publishedFileId,
                    outputDir = outputDir,
                ),
            ).collect { event ->
                when (event) {
                    is DownloadEvent.StateChanged -> {
                        debugLogManager.append(task.id, "State changed to ${event.state}")
                        updateTask(task.id) {
                            it.copy(
                                phase = event.state,
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        Log.i(WorkshopAppContract.logTag, "State=${event.state}")
                    }

                    is DownloadEvent.LogAppended -> appendTaskLog(task.id, event.line)

                    is DownloadEvent.Progress -> {
                        val speedBytes = calculateSpeedBytesPerSecond(task.id, event.writtenBytes)
                        updateTask(task.id) { current ->
                            current.copy(
                                progress = current.progress.merge(event, speedBytes),
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                    }

                    is DownloadEvent.FileCompleted -> {
                        updateTask(task.id) { current ->
                            current.copy(
                                progress = current.progress.copy(
                                    completedFiles = maxOf(
                                        current.progress.completedFiles,
                                        eventProgressCompletedFiles(current.progress),
                                    ),
                                ),
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        appendTaskLog(task.id, "文件完成：${event.file.relativePath}")
                    }

                    is DownloadEvent.Completed -> {
                        debugLogManager.append(
                            task.id,
                            "Download completed with ${event.files.size} files; entering finalizer",
                        )
                        runCatching {
                            taskFinalizer.finalizeSuccessfulDownload(
                                task = task,
                                stagingDir = outputDir,
                                downloadedFiles = event.files,
                            ) { line ->
                                appendTaskLog(task.id, line)
                            }
                        }.onSuccess { finalizedDownload ->
                            taskSucceeded = true
                            clearProgressSample(task.id)
                            updateTask(task.id) { current ->
                                current.copy(
                                    itemTitle = finalizedDownload.itemTitle,
                                    status = DownloadCenterTaskStatus.Success,
                                    phase = DownloadState.Success,
                                    files = finalizedDownload.exportedFiles,
                                    progress = current.progress.complete(filesCount = event.files.size),
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
                            }
                            Log.i(
                                WorkshopAppContract.logTag,
                                "Download completed files=${finalizedDownload.exportedFiles.size}",
                            )
                            debugLogManager.append(
                                task.id,
                                "Finalizer completed exportedFiles=${finalizedDownload.exportedFiles.size}",
                            )
                        }.onFailure { error ->
                            val message = "Export failed: ${error.message ?: error::class.simpleName}"
                            clearProgressSample(task.id)
                            updateTask(task.id) {
                                it.copy(
                                    status = DownloadCenterTaskStatus.Failed,
                                    phase = DownloadState.Failed,
                                    errorMessage = message,
                                    progress = it.progress.copy(speedBytesPerSecond = null),
                                    logs = (it.logs + message).takeLast(MAX_LOG_LINES),
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
                            }
                            Log.e(WorkshopAppContract.logTag, "Download failed $message", error)
                            debugLogManager.appendError(task.id, message, error)
                        }
                    }

                    is DownloadEvent.Failed -> {
                        clearProgressSample(task.id)
                        updateTask(task.id) {
                            it.copy(
                                status = DownloadCenterTaskStatus.Failed,
                                phase = DownloadState.Failed,
                                errorMessage = event.message,
                                progress = it.progress.copy(speedBytesPerSecond = null),
                                logs = (it.logs + event.message).takeLast(MAX_LOG_LINES),
                                updatedAtMillis = System.currentTimeMillis(),
                            )
                        }
                        Log.e(WorkshopAppContract.logTag, "Download failed ${event.message}")
                        debugLogManager.append(task.id, "Download failed: ${event.message}")
                    }
                }
            }
        } catch (error: CancellationException) {
            clearProgressSample(task.id)
            val current = _uiState.value.tasks.firstOrNull { it.id == task.id }
            if (current?.status == DownloadCenterTaskStatus.Paused) {
                Log.i(WorkshopAppContract.logTag, "Task paused taskId=${task.id}")
                debugLogManager.append(task.id, "Task cancellation consumed as pause")
                return
            }
            debugLogManager.appendError(task.id, "Task cancelled unexpectedly", error)
            throw error
        }

        if (!taskSucceeded) {
            val current = _uiState.value.tasks.firstOrNull { it.id == task.id } ?: return
            if (current.status == DownloadCenterTaskStatus.Running) {
                clearProgressSample(task.id)
                updateTask(task.id) {
                    it.copy(
                        status = DownloadCenterTaskStatus.Failed,
                        phase = DownloadState.Failed,
                        errorMessage = it.errorMessage ?: "下载失败。",
                        progress = it.progress.copy(speedBytesPerSecond = null),
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
                Log.e(WorkshopAppContract.logTag, "Download failed ${current.errorMessage ?: "unknown"}")
                debugLogManager.append(
                    task.id,
                    "Task finished without success; final status=${current.status} error=${current.errorMessage ?: "unknown"}",
                )
            }
        }
    }

    private fun appendTaskLog(
        taskId: String,
        line: String,
    ) {
        updateTask(taskId) {
            it.copy(
                logs = (it.logs + line).takeLast(MAX_LOG_LINES),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        debugLogManager.append(taskId, line)
        Log.i(WorkshopAppContract.logTag, line)
    }

    private fun updateTask(
        taskId: String,
        transform: (DownloadCenterTaskUiState) -> DownloadCenterTaskUiState,
    ) {
        mutateState { state ->
            state.copy(
                tasks = state.tasks.map { task ->
                    if (task.id == taskId) transform(task) else task
                },
            )
        }
    }

    private fun mutateState(
        transform: (DownloadCenterUiState) -> DownloadCenterUiState,
    ) {
        _uiState.update(transform)
        schedulePersist()
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MILLIS)
            persistNow()
        }
    }

    private fun persistNow() {
        runCatching {
            store.saveTasks(_uiState.value.tasks)
        }.onFailure { error ->
            Log.e(WorkshopAppContract.logTag, "Failed to persist download center tasks", error)
        }
    }

    private fun recoverPersistedTasks(
        tasks: List<DownloadCenterTaskUiState>,
    ): List<DownloadCenterTaskUiState> {
        val now = System.currentTimeMillis()
        return tasks.mapIndexed { index, task ->
            when (task.status) {
                DownloadCenterTaskStatus.Success,
                DownloadCenterTaskStatus.Failed,
                -> task.copy(progress = task.progress.copy(speedBytesPerSecond = null))

                DownloadCenterTaskStatus.Paused ->
                    task.copy(
                        phase = DownloadState.Paused,
                        progress = task.progress.copy(speedBytesPerSecond = null),
                        updatedAtMillis = now + index,
                    )

                DownloadCenterTaskStatus.Queued,
                DownloadCenterTaskStatus.Running,
                -> task.copy(
                    status = DownloadCenterTaskStatus.Queued,
                    phase = DownloadState.Idle,
                    errorMessage = null,
                    progress = task.progress.copy(speedBytesPerSecond = null),
                    logs = (task.logs + "应用重启后已恢复任务，将从已缓存的下载进度继续。").takeLast(MAX_LOG_LINES),
                    updatedAtMillis = now + index,
                )
            }
        }
    }

    private fun calculateSpeedBytesPerSecond(
        taskId: String,
        writtenBytes: Long,
    ): Long? {
        val now = System.currentTimeMillis()
        synchronized(progressSamples) {
            val previous = progressSamples[taskId]
            progressSamples[taskId] = ProgressRateSample(writtenBytes = writtenBytes, timestampMillis = now)
            if (previous == null || now <= previous.timestampMillis || writtenBytes < previous.writtenBytes) {
                return null
            }

            val bytesDelta = writtenBytes - previous.writtenBytes
            val timeDelta = now - previous.timestampMillis
            if (bytesDelta <= 0L || timeDelta <= 0L) {
                return null
            }
            return (bytesDelta * 1000L) / timeDelta
        }
    }

    private fun clearProgressSample(taskId: String) {
        synchronized(progressSamples) {
            progressSamples.remove(taskId)
        }
    }

    private fun startTask(task: DownloadCenterTaskUiState) {
        synchronized(runningTaskJobs) {
            if (runningTaskJobs[task.id]?.isActive == true) {
                return
            }

            runningTaskJobs[task.id] = scope.launch {
                try {
                    processTask(task)
                } finally {
                    synchronized(runningTaskJobs) {
                        runningTaskJobs.remove(task.id)
                    }
                }
            }
        }
    }

    private fun nextQueuedTasks(limit: Int): List<DownloadCenterTaskUiState> {
        val runningIds = synchronized(runningTaskJobs) {
            runningTaskJobs
                .filterValues(Job::isActive)
                .keys
                .toSet()
        }

        return _uiState.value.tasks
            .filter { it.status == DownloadCenterTaskStatus.Queued && it.id !in runningIds }
            .sortedBy(DownloadCenterTaskUiState::enqueuedAtMillis)
            .take(limit)
    }

    private fun cleanupFinishedTaskJobs() {
        synchronized(runningTaskJobs) {
            runningTaskJobs.entries.removeAll { !it.value.isActive }
        }
    }

    private fun cancelRunningTask(
        taskId: String,
        reason: String,
    ) {
        synchronized(runningTaskJobs) {
            runningTaskJobs[taskId]?.cancel(CancellationException(reason))
        }
    }

    private fun runningTaskCount(): Int =
        synchronized(runningTaskJobs) {
            runningTaskJobs.count { it.value.isActive }
        }

    private fun isTaskRunning(taskId: String): Boolean =
        synchronized(runningTaskJobs) {
            runningTaskJobs[taskId]?.isActive == true
        }

    private fun stagingDirFor(task: DownloadCenterTaskUiState): File =
        File(application.filesDir, "workshop/${task.appId}/${task.publishedFileId}")

    data class QueueTarget(
        val publishedFileId: ULong,
        val itemTitle: String,
        val boundAccountId: String? = null,
        val boundAccountName: String = "匿名",
    )

    private data class ProgressRateSample(
        val writtenBytes: Long,
        val timestampMillis: Long,
    )

    companion object {
        private const val MAX_LOG_LINES = 160
        private const val PERSIST_DEBOUNCE_MILLIS = 250L
        private const val RUNNER_POLL_INTERVAL_MILLIS = 250L

        @Volatile
        private var instance: DownloadCenterManager? = null

        fun getInstance(application: Application): DownloadCenterManager =
            instance ?: synchronized(this) {
                instance ?: DownloadCenterManager(application).also { instance = it }
            }
    }
}

private fun buildSessionConnector(
    accountSession: SteamAccountSession?,
): suspend (SteamCmSession, List<CmServer>) -> SessionContext =
    if (accountSession == null) {
        { session, servers -> session.connectAnonymous(servers) }
    } else {
        { session, servers -> session.connectWithRefreshToken(servers, accountSession) }
    }

private fun DownloadCenterProgressSnapshot.merge(
    event: DownloadEvent.Progress,
    measuredSpeedBytesPerSecond: Long?,
): DownloadCenterProgressSnapshot =
    copy(
        writtenBytes = event.writtenBytes,
        totalBytes = event.totalBytes ?: totalBytes,
        completedChunks = event.completedChunks ?: completedChunks,
        totalChunks = event.totalChunks ?: totalChunks,
        completedFiles = event.completedFiles ?: completedFiles,
        totalFiles = event.totalFiles ?: totalFiles,
        speedBytesPerSecond = measuredSpeedBytesPerSecond ?: speedBytesPerSecond,
    )

private fun DownloadCenterProgressSnapshot.complete(
    filesCount: Int,
): DownloadCenterProgressSnapshot =
    copy(
        writtenBytes = totalBytes ?: writtenBytes,
        totalBytes = totalBytes ?: writtenBytes,
        completedChunks = totalChunks ?: completedChunks,
        totalChunks = totalChunks ?: completedChunks,
        completedFiles = totalFiles ?: filesCount,
        totalFiles = totalFiles ?: filesCount,
        speedBytesPerSecond = null,
    )

private fun eventProgressCompletedFiles(
    progress: DownloadCenterProgressSnapshot,
): Int =
    if (progress.totalFiles != null) {
        (progress.completedFiles + 1).coerceAtMost(progress.totalFiles)
    } else {
        progress.completedFiles + 1
    }





