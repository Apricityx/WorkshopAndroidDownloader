package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModLibraryUpdateCheckTest {
    @Test
    fun evaluateModUpdate_marksUpdateAvailableWhenRemoteIsNewer() {
        val entry = DownloadedModEntry(
            appId = 480u,
            publishedFileId = 1234uL,
            gameTitle = "Test Game",
            itemTitle = "Test Mod",
            versionId = "updated-1",
            versionUpdatedAtMillis = 1_000L,
            storedAtMillis = 1_000L,
            files = emptyList(),
        )

        val result = evaluateModUpdate(
            entry = entry,
            remoteUpdatedEpochSeconds = 2L,
            checkedAtMillis = 3_000L,
        )

        assertThat(result.status).isEqualTo(ModUpdateCheckStatus.UpdateAvailable)
        assertThat(result.remoteUpdatedAtMillis).isEqualTo(2_000L)
    }

    @Test
    fun modLibraryKey_includesVersionId() {
        val entry = DownloadedModEntry(
            appId = 480u,
            publishedFileId = 1234uL,
            gameTitle = "Test Game",
            itemTitle = "Test Mod",
            versionId = "updated-2",
            storedAtMillis = 1_000L,
            files = emptyList(),
        )

        assertThat(entry.modLibraryKey()).isEqualTo("480-1234-updated-2")
    }

    @Test
    fun buildModUpdateCheckSummary_countsEachStatus() {
        val summary = buildModUpdateCheckSummary(
            listOf(
                ModUpdateCheckResult(status = ModUpdateCheckStatus.UpdateAvailable),
                ModUpdateCheckResult(status = ModUpdateCheckStatus.UpToDate),
                ModUpdateCheckResult(status = ModUpdateCheckStatus.Failed),
            ),
        )

        assertThat(summary).isEqualTo("模组更新检查完成：1 个模组可更新，1 个模组已最新，1 个模组失败。")
    }
}
