package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class ModLibraryStoreTest {
    @Test
    fun saveAndLoad_roundTripsEntries() {
        val tempDir = Files.createTempDirectory("mod-library-store").toFile()
        val store = ModLibraryStore(File(tempDir, "index.json"))
        val entries = listOf(
            DownloadedModEntry(
                appId = 646570u,
                publishedFileId = 3677098410uL,
                gameTitle = "Slay the Spire",
                itemTitle = "Skip The Spire",
                previewImagePath = "D:/tmp/cover.webp",
                storedAtMillis = 1234L,
                files = listOf(
                    ExportedDownloadFile(
                        relativePath = "Skip The Spire.jar",
                        sizeBytes = 42L,
                        modifiedEpochMillis = 1234L,
                        contentUri = "content://downloads/1",
                        userVisiblePath = "Download/workshop/Slay the Spire/Skip The Spire/Skip The Spire.jar",
                    ),
                ),
            ),
        )

        store.saveEntries(entries)

        assertThat(store.loadEntries()).isEqualTo(entries)
        tempDir.deleteRecursively()
    }

    @Test
    fun loadEntries_returnsEmptyList_whenFileMissing() {
        val tempDir = Files.createTempDirectory("mod-library-store-empty").toFile()
        val store = ModLibraryStore(File(tempDir, "missing.json"))

        assertThat(store.loadEntries()).isEmpty()
        tempDir.deleteRecursively()
    }
}
