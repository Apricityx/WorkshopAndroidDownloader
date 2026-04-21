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
                description = "Latest description",
                changeNotes = "### Update: latest",
                changeNotesFetched = true,
                previewImagePath = "D:/covers/latest.webp",
                previewImageUrl = "https://cdn.example.com/latest.webp",
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
                description = "Other description",
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
                description = "Older description",
                changeNotes = "### Update: older",
                versionId = "updated-1",
                versionUpdatedAtMillis = 1_000L,
                storedAtMillis = 1_000L,
                files = listOf(sampleFile("mods/v1.jar", 1_000L)),
            ),
        )

        val grouped = entries.groupedForDisplay()

        assertThat(grouped).hasSize(2)
        assertThat(grouped[0].modGroupKey()).isEqualTo("646570-3677098410")
        assertThat(grouped[0].description).isEqualTo("Latest description")
        assertThat(grouped[0].changeNotes).isEqualTo("### Update: latest")
        assertThat(grouped[0].changeNotesFetched).isTrue()
        assertThat(grouped[0].previewImagePath).isEqualTo("D:/covers/latest.webp")
        assertThat(grouped[0].previewImageUrl).isEqualTo("https://cdn.example.com/latest.webp")
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
            description = "Description",
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
                        description = "Newest",
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
                        description = "Oldest",
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
                        description = "Only",
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

    @Test
    fun versionCount_ignoresTrackingOnlyEntry() {
        val group = DownloadedModGroup(
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
                    storedAtMillis = 3_000L,
                    files = emptyList(),
                    isTrackingOnly = true,
                ),
                DownloadedModEntry(
                    appId = 646570u,
                    publishedFileId = 3677098410uL,
                    gameTitle = "Slay the Spire",
                    itemTitle = "Skip The Spire",
                    versionId = "updated-1",
                    versionUpdatedAtMillis = 1_000L,
                    storedAtMillis = 2_000L,
                    files = listOf(sampleFile("mods/v1.jar", 2_000L)),
                ),
            ),
        )

        assertThat(group.versionCount()).isEqualTo(1)
        assertThat(group.latestVersionOrNull()?.versionId).isEqualTo("updated-1")
        assertThat(group.updateReferenceEntry().versionId).isEqualTo("updated-1")
    }

    @Test
    fun latestVersionsForUpdateCheck_usesTrackingEntry_whenNoStoredVersionsExist() {
        val trackedGroup = DownloadedModGroup(
            appId = 480u,
            publishedFileId = 1234uL,
            gameTitle = "Test Game",
            itemTitle = "Tracked Mod",
            versions = listOf(
                DownloadedModEntry(
                    appId = 480u,
                    publishedFileId = 1234uL,
                    gameTitle = "Test Game",
                    itemTitle = "Tracked Mod",
                    versionId = "updated-5",
                    versionUpdatedAtMillis = 5_000L,
                    storedAtMillis = 6_000L,
                    files = emptyList(),
                    isTrackingOnly = true,
                ),
            ),
        )

        val latest = listOf(trackedGroup).latestVersionsForUpdateCheck()

        assertThat(trackedGroup.versionCount()).isEqualTo(0)
        assertThat(trackedGroup.latestVersionOrNull()).isNull()
        assertThat(latest).hasSize(1)
        assertThat(latest.single().versionId).isEqualTo("updated-5")
        assertThat(latest.single().isTrackingOnly).isTrue()
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
