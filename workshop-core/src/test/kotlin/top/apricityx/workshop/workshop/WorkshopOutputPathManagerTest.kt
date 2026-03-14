package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class WorkshopOutputPathManagerTest {
    @Test
    fun `treats directory manifest entry as directory`() {
        val directory = ManifestFile(
            path = "2026.1",
            size = 0,
            flags = DEPOT_FILE_FLAG_DIRECTORY,
            shaContent = ByteArray(20),
            linkTarget = null,
            chunks = emptyList(),
        )
        val child = ManifestFile(
            path = "2026.1/DynamicWorlds.tmod",
            size = 1,
            flags = 0u,
            shaContent = ByteArray(20),
            linkTarget = null,
            chunks = emptyList(),
        )
        val manifest = DepotManifest(
            depotId = 1281930u,
            manifestId = 1uL,
            createdAt = Instant.EPOCH,
            encryptedCrc = 0u,
            filenamesEncrypted = false,
            files = listOf(directory, child),
        )
        val outputDir = Files.createTempDirectory("workshop-dir-entry").toFile()

        val prepared = WorkshopOutputPathManager.prepare(outputDir, manifest, directory)

        assertThat(prepared).isInstanceOf(PreparedManifestEntry.DirectoryEntry::class.java)
        prepared as PreparedManifestEntry.DirectoryEntry
        assertThat(prepared.target.isDirectory).isTrue()
    }

    @Test
    fun `replaces stale file when child path needs a directory`() {
        val directory = ManifestFile(
            path = "2026.1",
            size = 0,
            flags = DEPOT_FILE_FLAG_DIRECTORY,
            shaContent = ByteArray(20),
            linkTarget = null,
            chunks = emptyList(),
        )
        val child = ManifestFile(
            path = "2026.1/DynamicWorlds.tmod",
            size = 1,
            flags = 0u,
            shaContent = ByteArray(20),
            linkTarget = null,
            chunks = emptyList(),
        )
        val manifest = DepotManifest(
            depotId = 1281930u,
            manifestId = 1uL,
            createdAt = Instant.EPOCH,
            encryptedCrc = 0u,
            filenamesEncrypted = false,
            files = listOf(directory, child),
        )
        val outputDir = Files.createTempDirectory("workshop-stale-file").toFile()
        Files.writeString(outputDir.toPath().resolve("2026.1"), "")

        val prepared = WorkshopOutputPathManager.prepare(outputDir, manifest, child)

        assertThat(prepared).isInstanceOf(PreparedManifestEntry.FileEntry::class.java)
        prepared as PreparedManifestEntry.FileEntry
        assertThat(prepared.target.parentFile.isDirectory).isTrue()
        assertThat(prepared.target.parentFile.name).isEqualTo("2026.1")
    }
}
