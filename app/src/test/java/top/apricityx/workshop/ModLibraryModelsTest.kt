package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModLibraryModelsTest {
    @Test
    fun groupedForDisplay_mergesVersionsOfSameModIntoOneGroup() {
        val entries = listOf(
            DownloadedModEntry(
                appId = 646570u,
                publishedFileId = 3677098410uL,
                gameTitle = "Slay the Spire",
                itemTitle = "Skip The Spire",
                previewImagePath = "D:/covers/latest.webp",
                versionId = "updated-2",
                versionUpdatedAtMillis = 2_000L,
                storedAtMillis = 2_000L,
                files = listOf(sampleFile("mods/v2.jar", 2_000L)),
            ),
            DownloadedModEntry(
                appId = 480u,
                publishedFileId = 999uL,
                gameTitle = "Spacewar",
                itemTitle = "Example Mod",
                versionId = "updated-3",
                versionUpdatedAtMillis = 1_500L,
                storedAtMillis = 1_500L,
                files = listOf(sampleFile("mods/example.zip", 1_500L)),
            ),
            DownloadedModEntry(
                appId = 646570u,
                publishedFileId = 3677098410uL,
                gameTitle = "Slay the Spire",
                itemTitle = "Skip The Spire",
                versionId = "updated-1",
                versionUpdatedAtMillis = 1_000L,
                storedAtMillis = 1_000L,
                files = listOf(sampleFile("mods/v1.jar", 1_000L)),
            ),
        )

        val grouped = entries.groupedForDisplay()

        assertThat(grouped).hasSize(2)
        assertThat(grouped[0].modGroupKey()).isEqualTo("646570-3677098410")
        assertThat(grouped[0].previewImagePath).isEqualTo("D:/covers/latest.webp")
        assertThat(grouped[0].versions.map(DownloadedModEntry::versionId))
            .containsExactly("updated-2", "updated-1")
            .inOrder()
        assertThat(grouped[1].versions.map(DownloadedModEntry::versionId))
            .containsExactly("updated-3")
    }

    @Test
    fun modGroupKey_isStableAcrossVersions() {
        val firstVersion = DownloadedModEntry(
            appId = 480u,
            publishedFileId = 1234uL,
            gameTitle = "Test Game",
            itemTitle = "Test Mod",
            versionId = "updated-1",
            storedAtMillis = 1_000L,
            files = emptyList(),
        )
        val secondVersion = firstVersion.copy(versionId = "updated-2", storedAtMillis = 2_000L)

        assertThat(firstVersion.modGroupKey()).isEqualTo(secondVersion.modGroupKey())
    }

    @Test
    fun latestVersionsForUpdateCheck_keepsOnlyNewestVersionPerMod() {
        val grouped = listOf(
            DownloadedModGroup(
                appId = 646570u,
                publishedFileId = 3677098410uL,
                gameTitle = "Slay the Spire",
                itemTitle = "Skip The Spire",
                versions = listOf(
                    DownloadedModEntry(
                        appId = 646570u,
                        publishedFileId = 3677098410uL,
                        gameTitle = "Slay the Spire",
                        itemTitle = "Skip The Spire",
                        versionId = "updated-2",
                        versionUpdatedAtMillis = 2_000L,
                        storedAtMillis = 2_000L,
                        files = emptyList(),
                    ),
                    DownloadedModEntry(
                        appId = 646570u,
                        publishedFileId = 3677098410uL,
                        gameTitle = "Slay the Spire",
                        itemTitle = "Skip The Spire",
                        versionId = "updated-1",
                        versionUpdatedAtMillis = 1_000L,
                        storedAtMillis = 1_000L,
                        files = emptyList(),
                    ),
                ),
            ),
            DownloadedModGroup(
                appId = 480u,
                publishedFileId = 999uL,
                gameTitle = "Spacewar",
                itemTitle = "Example Mod",
                versions = listOf(
                    DownloadedModEntry(
                        appId = 480u,
                        publishedFileId = 999uL,
                        gameTitle = "Spacewar",
                        itemTitle = "Example Mod",
                        versionId = "updated-3",
                        versionUpdatedAtMillis = 3_000L,
                        storedAtMillis = 3_000L,
                        files = emptyList(),
                    ),
                ),
            ),
        )

        val latest = grouped.latestVersionsForUpdateCheck()

        assertThat(latest.map(DownloadedModEntry::versionId))
            .containsExactly("updated-2", "updated-3")
            .inOrder()
    }

    private fun sampleFile(
        relativePath: String,
        modifiedAt: Long,
    ) = ExportedDownloadFile(
        relativePath = relativePath,
        sizeBytes = 42L,
        modifiedEpochMillis = modifiedAt,
        contentUri = "content://downloads/$modifiedAt",
        userVisiblePath = "Download/workshop/Test Game/Test Mod/updated-$modifiedAt/$relativePath",
    )
}
