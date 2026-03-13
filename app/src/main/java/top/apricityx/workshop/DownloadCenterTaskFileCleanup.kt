package top.apricityx.workshop

internal fun DownloadCenterUiState.clearExportedFilesForMod(
    appId: UInt,
    publishedFileId: ULong,
): DownloadCenterUiState =
    copy(
        tasks = tasks.map { task ->
            if (
                task.appId == appId &&
                task.publishedFileId == publishedFileId &&
                task.files.isNotEmpty()
            ) {
                task.copy(files = emptyList())
            } else {
                task
            }
        },
    )
