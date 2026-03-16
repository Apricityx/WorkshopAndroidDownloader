package top.apricityx.workshop.data

import kotlinx.serialization.Serializable

@Serializable
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
    val fileSizeBytes: Long? = null,
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
    val requiredItems: List<WorkshopRequiredItem> = emptyList(),
    val workshopUrl: String,
)

data class WorkshopRequiredItem(
    val appId: UInt,
    val publishedFileId: ULong,
    val title: String,
    val previewImageUrl: String,
    val descriptionSnippet: String,
    val authorName: String = "",
    val workshopUrl: String,
) {
    fun toBrowseItem(): WorkshopBrowseItem =
        WorkshopBrowseItem(
            appId = appId,
            publishedFileId = publishedFileId,
            title = title,
            authorName = authorName.ifBlank { "未知作者" },
            previewImageUrl = previewImageUrl,
            descriptionSnippet = descriptionSnippet,
        )
}
