package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopModStatusTest {
    @Test
    fun resolve_returnsDownloading_whenTaskIsPending() {
        val resolver = buildWorkshopModStatusResolver(
            downloadedGroups = emptyList(),
            updateResults = emptyMap(),
            pendingDownloadItemKeys = setOf(workshopModKey(480u, 1234uL)),
        )

        assertThat(resolver.resolve(appId = 480u, publishedFileId = 1234uL))
            .isEqualTo(WorkshopModStatus.Downloading)
    }

    @Test
    fun resolve_returnsUpdateAvailable_whenLatestDownloadedVersionHasUpdate() {
        val group = group(
            appId = 480u,
            publishedFileId = 1234uL,
            latestVersionId = "updated-2",
        )
        val resolver = buildWorkshopModStatusResolver(
            downloadedGroups = listOf(group),
            updateResults = mapOf(
                group.latestVersion().modLibraryKey() to
                    ModUpdateCheckResult(status = ModUpdateCheckStatus.UpdateAvailable),
            ),
        )

        assertThat(resolver.resolve(appId = 480u, publishedFileId = 1234uL))
            .isEqualTo(WorkshopModStatus.UpdateAvailable)
    }

    @Test
    fun resolve_returnsLatestDownloaded_whenDownloadedButNeverChecked() {
        val resolver = buildWorkshopModStatusResolver(
            downloadedGroups = listOf(
                group(
                    appId = 480u,
                    publishedFileId = 1234uL,
                    latestVersionId = "updated-2",
                ),
            ),
            updateResults = emptyMap(),
        )

        assertThat(resolver.resolve(appId = 480u, publishedFileId = 1234uL))
            .isEqualTo(WorkshopModStatus.LatestDownloaded)
    }

    @Test
    fun resolve_returnsNotDownloaded_whenMissingFromLocalLibrary() {
        val resolver = buildWorkshopModStatusResolver(
            downloadedGroups = emptyList(),
            updateResults = emptyMap(),
        )

        assertThat(resolver.resolve(appId = 480u, publishedFileId = 1234uL))
            .isEqualTo(WorkshopModStatus.NotDownloaded)
    }

    private fun group(
        appId: UInt,
        publishedFileId: ULong,
        latestVersionId: String,
    ) = DownloadedModGroup(
        appId = appId,
        publishedFileId = publishedFileId,
        gameTitle = "Spacewar",
        itemTitle = "Example Mod",
        versions = listOf(
            DownloadedModEntry(
                appId = appId,
                publishedFileId = publishedFileId,
                gameTitle = "Spacewar",
                itemTitle = "Example Mod",
                versionId = latestVersionId,
                versionUpdatedAtMillis = 2_000L,
                storedAtMillis = 2_000L,
                files = emptyList(),
            ),
        ),
    )
}
