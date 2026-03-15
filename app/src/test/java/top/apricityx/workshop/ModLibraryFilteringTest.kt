package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModLibraryFilteringTest {
    @Test
    fun filterModLibraryGroups_matchesSearchAcrossModGameAndIds() {
        val target = group(
            appId = 646570u,
            publishedFileId = 3677098410uL,
            gameTitle = "Slay the Spire",
            itemTitle = "Skip The Spire",
            latestVersionId = "updated-2",
        )
        val other = group(
            appId = 480u,
            publishedFileId = 999uL,
            gameTitle = "Spacewar",
            itemTitle = "Example Mod",
            latestVersionId = "updated-3",
        )

        val byName = filterModLibraryGroups(
            items = listOf(target, other),
            filterState = ModLibraryFilterState(searchQuery = "skip spire"),
        )
        val byId = filterModLibraryGroups(
            items = listOf(target, other),
            filterState = ModLibraryFilterState(searchQuery = "646570 3677098410"),
        )

        assertThat(byName).containsExactly(target)
        assertThat(byId).containsExactly(target)
    }

    @Test
    fun filterModLibraryGroups_filtersBySelectedGame() {
        val target = group(
            appId = 646570u,
            publishedFileId = 3677098410uL,
            gameTitle = "Slay the Spire",
            itemTitle = "Skip The Spire",
            latestVersionId = "updated-2",
        )
        val other = group(
            appId = 881100u,
            publishedFileId = 100uL,
            gameTitle = "Noita",
            itemTitle = "Spell Lab",
            latestVersionId = "updated-5",
        )

        val filtered = filterModLibraryGroups(
            items = listOf(target, other),
            filterState = ModLibraryFilterState(selectedGameTitle = "Noita"),
        )

        assertThat(filtered).containsExactly(other)
    }

    @Test
    fun sortModLibraryGroups_ordersByModTitleAscending() {
        val c = group(646570u, 1uL, "Slay the Spire", "Zeta Mod", "updated-2")
        val a = group(480u, 2uL, "Spacewar", "Alpha Mod", "updated-3")
        val b = group(881100u, 3uL, "Noita", "Beta Mod", "updated-4")

        val sorted = sortModLibraryGroups(
            items = listOf(c, a, b),
            sortOption = ModLibrarySortOption.ModTitle,
        )

        assertThat(sorted.map(DownloadedModGroup::itemTitle))
            .containsExactly("Alpha Mod", "Beta Mod", "Zeta Mod")
            .inOrder()
    }

    @Test
    fun sortModLibraryGroups_ordersByGameTitleAscending() {
        val c = group(646570u, 1uL, "Slay the Spire", "Zeta Mod", "updated-2")
        val a = group(480u, 2uL, "Noita", "Alpha Mod", "updated-3")
        val b = group(881100u, 3uL, "Spacewar", "Beta Mod", "updated-4")

        val sorted = sortModLibraryGroups(
            items = listOf(c, a, b),
            sortOption = ModLibrarySortOption.GameTitle,
        )

        assertThat(sorted.map(DownloadedModGroup::gameTitle))
            .containsExactly("Noita", "Slay the Spire", "Spacewar")
            .inOrder()
    }

    @Test
    fun availableModLibraryGames_returnsDistinctSortedTitles() {
        val games = availableModLibraryGames(
            listOf(
                group(646570u, 1uL, "Slay the Spire", "A", "updated-2"),
                group(480u, 2uL, "Spacewar", "B", "updated-3"),
                group(646570u, 3uL, "Slay the Spire", "C", "updated-4"),
            ),
        )

        assertThat(games).containsExactly("Slay the Spire", "Spacewar").inOrder()
    }

    private fun group(
        appId: UInt,
        publishedFileId: ULong,
        gameTitle: String,
        itemTitle: String,
        latestVersionId: String,
    ) = DownloadedModGroup(
        appId = appId,
        publishedFileId = publishedFileId,
        gameTitle = gameTitle,
        itemTitle = itemTitle,
        versions = listOf(
            entry(
                appId = appId,
                publishedFileId = publishedFileId,
                gameTitle = gameTitle,
                itemTitle = itemTitle,
                versionId = latestVersionId,
                storedAtMillis = 2_000L,
            ),
        ),
    )

    private fun entry(
        appId: UInt,
        publishedFileId: ULong,
        gameTitle: String,
        itemTitle: String,
        versionId: String,
        storedAtMillis: Long,
    ) = DownloadedModEntry(
        appId = appId,
        publishedFileId = publishedFileId,
        gameTitle = gameTitle,
        itemTitle = itemTitle,
        versionId = versionId,
        versionUpdatedAtMillis = storedAtMillis,
        storedAtMillis = storedAtMillis,
        files = emptyList(),
    )
}
