package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopPublicExportManagerTest {
    @Test
    fun `build download relative path nests under downloads workshop`() {
        val relativePath = WorkshopPublicExportManager.buildDownloadRelativePath(
            appId = 646570u,
            publishedFileId = 3677098410uL,
            relativeFilePath = "mods/example/file.txt",
        )

        assertThat(relativePath).isEqualTo("Download/workshop/646570/3677098410/mods/example/")
    }

    @Test
    fun `build user visible path includes file name`() {
        val visiblePath = WorkshopPublicExportManager.buildUserVisiblePath(
            appId = 646570u,
            publishedFileId = 3677098410uL,
            relativeFilePath = "mods/example/file.txt",
        )

        assertThat(visiblePath).isEqualTo("Download/workshop/646570/3677098410/mods/example/file.txt")
    }

    @Test
    fun `build displayed relative path replaces basename only`() {
        val displayedPath = WorkshopPublicExportManager.buildDisplayedRelativePath(
            relativeFilePath = "mods/example/file.txt",
            displayName = "Skip The Spire.jar",
        )

        assertThat(displayedPath).isEqualTo("mods/example/Skip The Spire.jar")
    }
}
