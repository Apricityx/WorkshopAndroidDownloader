package top.apricityx.workshop

fun filterModLibraryGroups(
    items: List<DownloadedModGroup>,
    filterState: ModLibraryFilterState,
): List<DownloadedModGroup> =
    items.filter { group ->
        group.matchesSearchQuery(filterState.searchQuery) &&
            group.matchesGameFilter(filterState.selectedGameTitle)
    }

fun sortModLibraryGroups(
    items: List<DownloadedModGroup>,
    sortOption: ModLibrarySortOption,
): List<DownloadedModGroup> =
    when (sortOption) {
        ModLibrarySortOption.LatestSynced -> items.sortedWith(
            compareByDescending<DownloadedModGroup> { it.latestVersion().storedAtMillis }
                .thenByDescending { it.latestVersion().versionUpdatedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.gameTitle.lowercase() }
                .thenBy { it.itemTitle.lowercase() },
        )

        ModLibrarySortOption.ModTitle -> items.sortedWith(
            compareBy<DownloadedModGroup> { it.itemTitle.lowercase() }
                .thenBy { it.gameTitle.lowercase() }
                .thenByDescending { it.latestVersion().storedAtMillis },
        )

        ModLibrarySortOption.GameTitle -> items.sortedWith(
            compareBy<DownloadedModGroup> { it.gameTitle.lowercase() }
                .thenBy { it.itemTitle.lowercase() }
                .thenByDescending { it.latestVersion().storedAtMillis },
        )
    }

fun availableModLibraryGames(items: List<DownloadedModGroup>): List<String> =
    items.map(DownloadedModGroup::gameTitle)
        .filter(String::isNotBlank)
        .distinct()
        .sortedBy(String::lowercase)

fun DownloadedModGroup.latestUpdateStatus(
    updateResults: Map<String, ModUpdateCheckResult>,
): ModUpdateCheckStatus =
    updateResults[latestVersion().modLibraryKey()]?.status ?: ModUpdateCheckStatus.Unknown

private fun DownloadedModGroup.matchesSearchQuery(searchQuery: String): Boolean {
    val normalizedTerms = searchQuery.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (normalizedTerms.isEmpty()) {
        return true
    }

    val haystack = buildString {
        append(itemTitle)
        append('\n')
        append(gameTitle)
        append('\n')
        append(appId)
        append('\n')
        append(publishedFileId)
        versions.forEach { version ->
            append('\n')
            append(version.versionId)
            append('\n')
            append(version.versionLabel())
        }
    }.lowercase()

    return normalizedTerms.all(haystack::contains)
}

private fun DownloadedModGroup.matchesGameFilter(selectedGameTitle: String?): Boolean =
    selectedGameTitle.isNullOrBlank() || gameTitle == selectedGameTitle
