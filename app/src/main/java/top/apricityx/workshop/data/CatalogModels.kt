package top.apricityx.workshop.data

data class SteamGame(
    val appId: UInt,
    val name: String,
    val shortDescription: String,
    val headerImageUrl: String,
    val capsuleImageUrl: String,
    val supportsWorkshop: Boolean,
)

data class WorkshopBrowseItem(
    val appId: UInt,
    val publishedFileId: ULong,
    val title: String,
    val authorName: String,
    val previewImageUrl: String,
    val descriptionSnippet: String,
)

data class WorkshopBrowsePage(
    val items: List<WorkshopBrowseItem>,
    val page: Int,
    val hasNextPage: Boolean,
)

data class WorkshopItemDetail(
    val appId: UInt,
    val publishedFileId: ULong,
    val title: String,
    val authorName: String,
    val previewImageUrl: String,
    val description: String,
    val fileSizeBytes: Long?,
    val timeUpdatedEpochSeconds: Long?,
    val subscriptions: Long?,
    val favorited: Long?,
    val views: Long?,
    val tags: List<String>,
    val workshopUrl: String,
)
