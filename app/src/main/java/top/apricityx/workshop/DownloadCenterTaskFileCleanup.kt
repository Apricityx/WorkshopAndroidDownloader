package top.apricityx.workshop

internal fun DownloadCenterUiState.clearExportedFilesForMod(
    entry: DownloadedModEntry,
): DownloadCenterUiState {
    val targetFiles = entry.files.map(ExportedDownloadFile::cleanupKey).toSet()
    if (targetFiles.isEmpty()) {
        return this
    }

    return copy(
        tasks = tasks.map { task ->
            if (
                task.appId == entry.appId &&
                task.publishedFileId == entry.publishedFileId &&
                task.files.isNotEmpty()
            ) {
                task.copy(
                    files = task.files.filterNot { it.cleanupKey() in targetFiles },
                )
            } else {
                task
            }
        },
    )
}

private fun ExportedDownloadFile.cleanupKey(): String =
    contentUri.ifBlank { userVisiblePath }
