package top.apricityx.workshop

import top.apricityx.workshop.data.WorkshopBrowseItem

typealias WorkshopModKey = Pair<UInt, ULong>

enum class WorkshopModStatus {
    LatestDownloaded,
    UpdateAvailable,
    NotDownloaded,
    Downloading,
}

fun workshopModKey(
    appId: UInt,
    publishedFileId: ULong,
): WorkshopModKey = appId to publishedFileId

fun DownloadedModEntry.workshopModKey(): WorkshopModKey =
    workshopModKey(appId = appId, publishedFileId = publishedFileId)

fun DownloadedModGroup.workshopModKey(): WorkshopModKey =
    workshopModKey(appId = appId, publishedFileId = publishedFileId)

fun DownloadCenterTaskUiState.workshopModKey(): WorkshopModKey =
    workshopModKey(appId = appId, publishedFileId = publishedFileId)

fun WorkshopBrowseItem.workshopModKey(): WorkshopModKey =
    workshopModKey(appId = appId, publishedFileId = publishedFileId)

data class WorkshopModStatusResolver(
    private val latestDownloadedEntries: Map<WorkshopModKey, DownloadedModEntry>,
    private val updateResultsByModKey: Map<WorkshopModKey, ModUpdateCheckResult>,
    private val downloadingModKeys: Set<WorkshopModKey>,
) {
    fun resolve(
        appId: UInt,
        publishedFileId: ULong,
    ): WorkshopModStatus {
        val key = workshopModKey(appId = appId, publishedFileId = publishedFileId)
        if (key in downloadingModKeys) {
            return WorkshopModStatus.Downloading
        }

        if (latestDownloadedEntries[key] == null) {
            return WorkshopModStatus.NotDownloaded
        }
        return if (updateResultsByModKey[key]?.status == ModUpdateCheckStatus.UpdateAvailable) {
            WorkshopModStatus.UpdateAvailable
        } else {
            WorkshopModStatus.LatestDownloaded
        }
    }

    fun resolve(item: WorkshopBrowseItem): WorkshopModStatus =
        resolve(appId = item.appId, publishedFileId = item.publishedFileId)
}

fun buildWorkshopModStatusResolver(
    downloadedGroups: List<DownloadedModGroup>,
    updateResults: Map<String, ModUpdateCheckResult>,
    pendingDownloadItemKeys: Set<WorkshopModKey> = emptySet(),
    activeDownloadItemKeys: Set<WorkshopModKey> = emptySet(),
): WorkshopModStatusResolver {
    val latestDownloadedEntries = downloadedGroups.associate { group ->
        group.workshopModKey() to group.latestVersion()
    }
    val updateResultsByModKey = latestDownloadedEntries.mapNotNull { (key, entry) ->
        updateResults[entry.modLibraryKey()]?.let { result ->
            key to result
        }
    }.toMap()
    return WorkshopModStatusResolver(
        latestDownloadedEntries = latestDownloadedEntries,
        updateResultsByModKey = updateResultsByModKey,
        downloadingModKeys = pendingDownloadItemKeys + activeDownloadItemKeys,
    )
}
