package top.apricityx.workshop

import android.app.Application
import java.io.File
import top.apricityx.workshop.workshop.DownloadedFileInfo

class DownloadCenterTaskFinalizer(
    application: Application,
    private val publicExportManager: WorkshopPublicExportManager = WorkshopPublicExportManager(application),
    private val modLibraryRepository: ModLibraryRepository = ModLibraryRepository(application),
    private val previewImageCache: WorkshopPreviewImageCache = WorkshopPreviewImageCache(application),
) {
    suspend fun finalizeSuccessfulDownload(
        task: DownloadCenterTaskUiState,
        stagingDir: File,
        downloadedFiles: List<DownloadedFileInfo>,
        log: suspend (String) -> Unit,
    ): FinalizedDownloadArtifacts {
        log("开始整理下载结果。stagingDir=${stagingDir.absolutePath} files=${downloadedFiles.size}")
        val metadata = readWorkshopDownloadMetadata(stagingDir)
        val resolvedItemTitle = metadata?.title?.takeIf(String::isNotBlank) ?: task.itemTitle
        log("解析元数据完成。resolvedItemTitle=$resolvedItemTitle")
        val exportedFiles = publicExportManager.exportDownloadedFiles(
            gameTitle = task.gameTitle,
            itemTitle = resolvedItemTitle,
            stagingDir = stagingDir,
            files = downloadedFiles,
            log = log,
        )
        log("公共导出完成。exportedFiles=${exportedFiles.size}")
        val previewImagePath = cachePreviewImage(
            task = task,
            previewImageUrl = metadata?.previewImageUrl,
            log = log,
        )
        log("封面缓存阶段结束。previewImagePath=${previewImagePath ?: "<none>"}")
        syncModLibrary(
            task = task,
            itemTitle = resolvedItemTitle,
            previewImagePath = previewImagePath,
            exportedFiles = exportedFiles,
            log = log,
        )
        log("模组库同步阶段结束。")
        return FinalizedDownloadArtifacts(
            itemTitle = resolvedItemTitle,
            exportedFiles = exportedFiles,
        )
    }

    private suspend fun cachePreviewImage(
        task: DownloadCenterTaskUiState,
        previewImageUrl: String?,
        log: suspend (String) -> Unit,
    ): String? {
        log("开始缓存模组封面。previewImageUrl=${previewImageUrl ?: "<none>"}")
        return runCatching {
            previewImageCache.cachePreviewImage(
                appId = task.appId,
                publishedFileId = task.publishedFileId,
                imageUrl = previewImageUrl,
            )
        }.fold(
            onSuccess = { path ->
                log("模组封面缓存完成。path=${path ?: "<none>"}")
                path
            },
            onFailure = { error ->
            log("模组封面缓存失败：${error.summary()}")
            null
            },
        )
    }

    private suspend fun syncModLibrary(
        task: DownloadCenterTaskUiState,
        itemTitle: String,
        previewImagePath: String?,
        exportedFiles: List<ExportedDownloadFile>,
        log: suspend (String) -> Unit,
    ) {
        log("开始同步模组库索引。exportedFiles=${exportedFiles.size}")
        val result = runCatching {
            modLibraryRepository.upsertDownloadedMod(
                appId = task.appId,
                publishedFileId = task.publishedFileId,
                gameTitle = task.gameTitle,
                itemTitle = itemTitle,
                previewImagePath = previewImagePath,
                files = exportedFiles,
            )
        }
        if (result.isSuccess) {
            log("模组库索引更新完成。")
        } else {
            log("模组库索引更新失败：${result.exceptionOrNull()?.summary() ?: "未知错误"}")
        }
    }
}

data class FinalizedDownloadArtifacts(
    val itemTitle: String,
    val exportedFiles: List<ExportedDownloadFile>,
)

private fun Throwable.summary(): String =
    message ?: this::class.simpleName ?: "未知错误"
