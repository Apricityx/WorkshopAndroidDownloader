package top.apricityx.workshop

private val SearchQueryDelimiterRegex = Regex("\\s+")

fun filterModLibraryGroups(
    items: List<DownloadedModGroup>,
    filterState: ModLibraryFilterState,
): List<DownloadedModGroup> {
    val normalizedTerms = normalizeSearchTerms(filterState.searchQuery)
    val selectedGameTitle = filterState.selectedGameTitle?.takeUnless(String::isBlank)
    if (normalizedTerms.isEmpty() && selectedGameTitle == null) {
        return items
    }

    return items.filter { group ->
        group.matchesGameFilter(selectedGameTitle) &&
            group.matchesSearchTerms(normalizedTerms)
    }
}

fun sortModLibraryGroups(
    items: List<DownloadedModGroup>,
    sortOption: ModLibrarySortOption,
    updateResults: Map<String, ModUpdateCheckResult> = emptyMap(),
): List<DownloadedModGroup> {
    if (items.size < 2) {
        return items
    }

    val updateAvailableComparator = compareByDescending<DownloadedModGroup> {
        it.latestUpdateStatus(updateResults) == ModUpdateCheckStatus.UpdateAvailable
    }
    return when (sortOption) {
        ModLibrarySortOption.LatestSynced -> items.sortedWith(updateAvailableComparator)
        ModLibrarySortOption.ModTitle -> items.sortedWith(
            updateAvailableComparator
                .thenBy { it.normalizedItemTitle }
                .thenBy { it.normalizedGameTitle }
                .thenByDescending { it.updateReferenceEntry().storedAtMillis },
        )

        ModLibrarySortOption.GameTitle -> items.sortedWith(
            updateAvailableComparator
                .thenBy { it.normalizedGameTitle }
                .thenBy { it.normalizedItemTitle }
                .thenByDescending { it.updateReferenceEntry().storedAtMillis },
        )
    }
}

fun availableModLibraryGames(items: List<DownloadedModGroup>): List<String> =
    items.asSequence()
        .map(DownloadedModGroup::gameTitle)
        .filter(String::isNotBlank)
        .distinct()
        .sortedBy(String::lowercase)
        .toList()

fun DownloadedModGroup.latestUpdateStatus(
    updateResults: Map<String, ModUpdateCheckResult>,
): ModUpdateCheckStatus =
    updateResults[cachedUpdateReferenceKey]?.status ?: ModUpdateCheckStatus.Unknown

private fun normalizeSearchTerms(searchQuery: String): List<String> =
    searchQuery.trim()
        .lowercase()
        .split(SearchQueryDelimiterRegex)
        .filter(String::isNotBlank)

private fun DownloadedModGroup.matchesSearchTerms(normalizedTerms: List<String>): Boolean {
    if (normalizedTerms.isEmpty()) {
        return true
    }

    return normalizedTerms.all(cachedSearchIndex::contains)
}

private fun DownloadedModGroup.matchesGameFilter(selectedGameTitle: String?): Boolean =
    selectedGameTitle == null || gameTitle == selectedGameTitle
