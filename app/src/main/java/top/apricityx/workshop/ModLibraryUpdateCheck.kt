package top.apricityx.workshop

import kotlinx.serialization.Serializable

@Serializable
data class ModLibraryUpdateCheckState(
    val isChecking: Boolean = false,
    val summaryMessage: String? = null,
    val lastCheckedAtMillis: Long? = null,
    val results: Map<String, ModUpdateCheckResult> = emptyMap(),
)

@Serializable
data class ModUpdateCheckResult(
    val status: ModUpdateCheckStatus = ModUpdateCheckStatus.Unknown,
    val remoteUpdatedAtMillis: Long? = null,
    val checkedAtMillis: Long? = null,
    val message: String? = null,
)

@Serializable
enum class ModUpdateCheckStatus {
    Unknown,
    Checking,
    UpToDate,
    UpdateAvailable,
    Failed,
}

fun DownloadedModEntry.modLibraryKey(): String =
    "${appId}-${publishedFileId}-${normalizeModVersionId(versionId)}"

fun evaluateModUpdate(
    entry: DownloadedModEntry,
    remoteUpdatedEpochSeconds: Long?,
    checkedAtMillis: Long,
): ModUpdateCheckResult {
    val remoteUpdatedAtMillis = remoteUpdatedEpochSeconds?.times(1000L)
    if (remoteUpdatedAtMillis == null) {
        return ModUpdateCheckResult(
            status = ModUpdateCheckStatus.Failed,
            checkedAtMillis = checkedAtMillis,
            message = "创意工坊未返回更新时间。",
        )
    }

    return ModUpdateCheckResult(
        status = if (remoteUpdatedAtMillis > (entry.versionUpdatedAtMillis ?: entry.storedAtMillis)) {
            ModUpdateCheckStatus.UpdateAvailable
        } else {
            ModUpdateCheckStatus.UpToDate
        },
        remoteUpdatedAtMillis = remoteUpdatedAtMillis,
        checkedAtMillis = checkedAtMillis,
    )
}

fun buildModUpdateCheckSummary(
    results: Collection<ModUpdateCheckResult>,
): String {
    if (results.isEmpty()) {
        return "没有可检查的模组。"
    }

    val availableCount = results.count { it.status == ModUpdateCheckStatus.UpdateAvailable }
    val upToDateCount = results.count { it.status == ModUpdateCheckStatus.UpToDate }
    val failedCount = results.count { it.status == ModUpdateCheckStatus.Failed }
    return "模组更新检查完成：$availableCount 个版本可更新，$upToDateCount 个版本已最新，$failedCount 个版本失败。"
}

fun ModLibraryUpdateCheckState.filterForEntries(
    entries: List<DownloadedModEntry>,
): ModLibraryUpdateCheckState {
    val validKeys = entries.map(DownloadedModEntry::modLibraryKey).toSet()
    val filteredResults = results.filterKeys(validKeys::contains)
    return copy(
        summaryMessage = when {
            isChecking -> summaryMessage
            filteredResults.isEmpty() -> null
            summaryMessage == null -> null
            else -> buildModUpdateCheckSummary(filteredResults.values)
        },
        results = filteredResults,
    )
}
