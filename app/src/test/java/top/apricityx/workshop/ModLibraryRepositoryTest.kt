package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class ModLibraryRepositoryTest {
    @Test
    fun mergeIndexedAndLocalMods_preservesIndexedMetadata_andAddsFallbackMetadata() {
        val previewFile = Files.createTempFile("mod-cover", ".webp").toFile()
        val indexed = listOf(
            DownloadedModEntry(
                appId = 646570u,
                publishedFileId = 3677098410uL,
                gameTitle = "Slay the Spire",
                itemTitle = "Skip The Spire",
                previewImagePath = previewFile.absolutePath,
                storedAtMillis = 100L,
                files = emptyList(),
            ),
        )
        val localMods = listOf(
            ModLibraryRepository.LocalModSnapshot(
                appId = 646570u,
                publishedFileId = 3677098410uL,
                files = listOf(sampleFile("mods/Skip The Spire.jar", 120L)),
            ),
            ModLibraryRepository.LocalModSnapshot(
                appId = 480u,
                publishedFileId = 999uL,
                files = listOf(sampleFile("mods/example.zip", 240L)),
            ),
        )

        val merged = mergeIndexedAndLocalMods(indexed, localMods) { 999L }

        assertThat(merged).hasSize(2)
        assertThat(merged[0].appId).isEqualTo(480u)
        assertThat(merged[0].gameTitle).isEqualTo("App 480")
        assertThat(merged[0].itemTitle).isEqualTo("模组 999")
        assertThat(merged[1].gameTitle).isEqualTo("Slay the Spire")
        assertThat(merged[1].itemTitle).isEqualTo("Skip The Spire")
        assertThat(merged[1].previewImagePath).isEqualTo(previewFile.absolutePath)
        assertThat(merged[1].files.map(ExportedDownloadFile::relativePath)).containsExactly("mods/Skip The Spire.jar")
        previewFile.delete()
    }

    @Test
    fun mergeIndexedAndLocalMods_dropsIndexedEntries_whenFilesMissingLocally() {
        val indexed = listOf(
            DownloadedModEntry(
                appId = 646570u,
                publishedFileId = 3677098410uL,
                gameTitle = "Slay the Spire",
                itemTitle = "Skip The Spire",
                storedAtMillis = 100L,
                files = listOf(sampleFile("Skip The Spire.jar", 100L)),
            ),
        )

        val merged = mergeIndexedAndLocalMods(indexed, emptyList()) { 999L }

        assertThat(merged).isEmpty()
    }

    @Test
    fun deleteFileAndEmptyParents_removes_empty_mod_directory_when_file_already_deleted() {
        val root = Files.createTempDirectory("mod-library-delete").toFile()
        val workshopRoot = File(root, "workshop")
        val modDir = File(workshopRoot, "Noita/Simple Custom GUI")
        val file = File(modDir, "mod.txt")
        checkNotNull(file.parentFile).mkdirs()
        file.writeText("content")

        assertThat(file.delete()).isTrue()

        deleteFileAndEmptyParents(file)

        assertThat(modDir.exists()).isFalse()
        assertThat(workshopRoot.exists()).isFalse()
        root.deleteRecursively()
    }

    @Test
    fun deleteFileAndEmptyParents_keeps_parent_directory_when_siblings_remain() {
        val root = Files.createTempDirectory("mod-library-delete-siblings").toFile()
        val workshopRoot = File(root, "workshop")
        val modDir = File(workshopRoot, "Noita/Simple Custom GUI")
        val removedFile = File(modDir, "remove.txt")
        val keptFile = File(modDir, "keep.txt")
        checkNotNull(removedFile.parentFile).mkdirs()
        removedFile.writeText("remove")
        keptFile.writeText("keep")

        deleteFileAndEmptyParents(removedFile)

        assertThat(removedFile.exists()).isFalse()
        assertThat(keptFile.exists()).isTrue()
        assertThat(modDir.exists()).isTrue()
        root.deleteRecursively()
    }

    private fun sampleFile(
        relativePath: String,
        modifiedAt: Long,
    ) = ExportedDownloadFile(
        relativePath = relativePath,
        sizeBytes = 42L,
        modifiedEpochMillis = modifiedAt,
        contentUri = "content://downloads/$modifiedAt",
        userVisiblePath = "Download/workshop/Example Game/Example Mod/$relativePath",
    )
}
