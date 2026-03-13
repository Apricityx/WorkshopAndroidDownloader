package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopPublicExportManagerTest {
    @Test
    fun `build download relative path nests under game and mod titles`() {
        val relativePath = WorkshopPublicExportManager.buildDownloadRelativePath(
            gameTitle = "Slay the Spire",
            itemTitle = "Skip The Spire",
            relativeFilePath = "mods/example/file.txt",
        )

        assertThat(relativePath).isEqualTo("Download/workshop/Slay the Spire/Skip The Spire/mods/example/")
    }

    @Test
    fun `build user visible path includes file name`() {
        val visiblePath = WorkshopPublicExportManager.buildUserVisiblePath(
            gameTitle = "Slay the Spire",
            itemTitle = "Skip The Spire",
            relativeFilePath = "mods/example/file.txt",
        )

        assertThat(visiblePath).isEqualTo("Download/workshop/Slay the Spire/Skip The Spire/mods/example/file.txt")
    }

    @Test
    fun `build mod subdirectory path sanitizes invalid path characters`() {
        val path = WorkshopPublicExportManager.buildModSubdirectoryPath(
            gameTitle = "Game: Name",
            itemTitle = "Mod*Name?",
        )

        assertThat(path).isEqualTo("workshop/Game_ Name/Mod_Name_/")
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
