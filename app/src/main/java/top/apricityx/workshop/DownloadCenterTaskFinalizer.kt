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
        val metadata = readWorkshopDownloadMetadata(stagingDir)
        val resolvedItemTitle = metadata?.title?.takeIf(String::isNotBlank) ?: task.itemTitle
        val exportedFiles = publicExportManager.exportDownloadedFiles(
            gameTitle = task.gameTitle,
            itemTitle = resolvedItemTitle,
            stagingDir = stagingDir,
            files = downloadedFiles,
            log = log,
        )
        val previewImagePath = cachePreviewImage(
            task = task,
            previewImageUrl = metadata?.previewImageUrl,
            log = log,
        )
        syncModLibrary(
            task = task,
            itemTitle = resolvedItemTitle,
            previewImagePath = previewImagePath,
            exportedFiles = exportedFiles,
            log = log,
        )
        return FinalizedDownloadArtifacts(
            itemTitle = resolvedItemTitle,
            exportedFiles = exportedFiles,
        )
    }

    private suspend fun cachePreviewImage(
        task: DownloadCenterTaskUiState,
        previewImageUrl: String?,
        log: suspend (String) -> Unit,
    ): String? =
        runCatching {
            previewImageCache.cachePreviewImage(
                appId = task.appId,
                publishedFileId = task.publishedFileId,
                imageUrl = previewImageUrl,
            )
        }.getOrElse { error ->
            log("模组封面缓存失败：${error.summary()}")
            null
        }

    private suspend fun syncModLibrary(
        task: DownloadCenterTaskUiState,
        itemTitle: String,
        previewImagePath: String?,
        exportedFiles: List<ExportedDownloadFile>,
        log: suspend (String) -> Unit,
    ) {
        runCatching {
            modLibraryRepository.upsertDownloadedMod(
                appId = task.appId,
                publishedFileId = task.publishedFileId,
                gameTitle = task.gameTitle,
                itemTitle = itemTitle,
                previewImagePath = previewImagePath,
                files = exportedFiles,
            )
        }.onFailure { error ->
            log("模组库索引更新失败：${error.summary()}")
        }
    }
}

data class FinalizedDownloadArtifacts(
    val itemTitle: String,
    val exportedFiles: List<ExportedDownloadFile>,
)

private fun Throwable.summary(): String =
    message ?: this::class.simpleName ?: "未知错误"
