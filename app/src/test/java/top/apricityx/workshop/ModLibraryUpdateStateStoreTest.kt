package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class ModLibraryUpdateStateStoreTest {
    @Test
    fun saveAndLoad_roundTripsStableUpdateState() {
        val tempDir = Files.createTempDirectory("mod-update-state").toFile()
        val store = ModLibraryUpdateStateStore(File(tempDir, "state.json"))
        val state = ModLibraryUpdateCheckState(
            isChecking = false,
            summaryMessage = "模组更新检查完成：1 个版本可更新，0 个版本已最新，0 个版本失败。",
            lastCheckedAtMillis = 1234L,
            results = mapOf(
                "646570-3677098410-updated-1" to ModUpdateCheckResult(
                    status = ModUpdateCheckStatus.UpdateAvailable,
                    remoteUpdatedAtMillis = 2_000L,
                    checkedAtMillis = 1_234L,
                ),
            ),
        )

        store.saveState(state)

        assertThat(store.loadState()).isEqualTo(state)
        tempDir.deleteRecursively()
    }

    @Test
    fun saveState_dropsTransientCheckingResults() {
        val tempDir = Files.createTempDirectory("mod-update-state-sanitize").toFile()
        val store = ModLibraryUpdateStateStore(File(tempDir, "state.json"))

        store.saveState(
            ModLibraryUpdateCheckState(
                isChecking = true,
                summaryMessage = null,
                lastCheckedAtMillis = 1234L,
                results = mapOf(
                    "checking" to ModUpdateCheckResult(status = ModUpdateCheckStatus.Checking),
                    "latest" to ModUpdateCheckResult(status = ModUpdateCheckStatus.UpToDate),
                ),
            ),
        )

        val loaded = store.loadState()

        assertThat(loaded.isChecking).isFalse()
        assertThat(loaded.results.keys).containsExactly("latest")
        assertThat(loaded.summaryMessage).isEqualTo("模组更新检查完成：0 个版本可更新，1 个版本已最新，0 个版本失败。")
        tempDir.deleteRecursively()
    }
}
