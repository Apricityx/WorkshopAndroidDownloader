package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
                versionId = "updated-1772900923",
                versionUpdatedAtMillis = 1_772_900_923_000L,
                storedAtMillis = 1234L,
                files = listOf(
                    ExportedDownloadFile(
                        relativePath = "Skip The Spire.jar",
                        sizeBytes = 42L,
                        modifiedEpochMillis = 1234L,
                        contentUri = "content://downloads/1",
                        userVisiblePath = "Download/workshop/Slay the Spire/Skip The Spire/updated-1772900923/Skip The Spire.jar",
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

    @Test
    fun withFileLock_serializesOperationsAcrossStoreInstances() {
        runBlocking {
            val tempDir = Files.createTempDirectory("mod-library-store-lock").toFile()
            val indexFile = File(tempDir, "index.json")
            val firstStore = ModLibraryStore(indexFile)
            val secondStore = ModLibraryStore(indexFile)
            val firstEntered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()

            val firstOperation = async(Dispatchers.Default) {
                firstStore.withFileLock {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                }
            }
            firstEntered.await()

            val secondOperation = async(Dispatchers.Default) {
                secondStore.withFileLock { "entered" }
            }

            val completedBeforeRelease = withTimeoutOrNull(150L) {
                secondOperation.await()
            }
            assertThat(completedBeforeRelease).isNull()

            releaseFirst.complete(Unit)
            assertThat(secondOperation.await()).isEqualTo("entered")
            firstOperation.await()
            tempDir.deleteRecursively()
        }
    }
}
