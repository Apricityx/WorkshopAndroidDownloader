package top.apricityx.workshop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import top.apricityx.workshop.SteamLanguagePreference

class WorkshopDetailRepository(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = "https://api.steampowered.com/".toHttpUrl(),
    private val communityBaseUrl: HttpUrl = "https://steamcommunity.com/".toHttpUrl(),
    private val languagePreferenceProvider: () -> SteamLanguagePreference = { SteamLanguagePreference.SimplifiedChinese },
) {
    suspend fun loadWorkshopItemDetail(item: WorkshopBrowseItem): WorkshopItemDetail = withContext(Dispatchers.IO) {
        val detail = loadPublishedFileDetails(item.appId, listOf(item.publishedFileId))
            .firstOrNull()
            ?: error("Workshop detail payload was empty")
        val localizedDetail = runCatching {
            loadLocalizedDetailPage(item)
        }.getOrNull()
        val apiTitle = detail.stringValue("title")
        val apiDescription = SteamHtmlDecoder.decodeWorkshopApiDescription(detail.stringValue("description")).ifBlank {
            item.descriptionSnippet.ifBlank { "暂无描述。" }
        }
        val requiredItems = runCatching {
            enrichRequiredItems(
                fallbackAppId = item.appId,
                items = localizedDetail?.requiredItems.orEmpty(),
            )
        }.getOrElse { error ->
            localizedDetail?.requiredItems
                ?.map { requiredItem ->
                    requiredItem.toWorkshopRequiredItem(
                        appId = item.appId,
                        titleOverride = requiredItem.title,
                        previewImageUrl = "",
                        descriptionSnippet = "",
                    )
                }
                .orEmpty()
        }

        WorkshopItemDetail(
            appId = item.appId,
            publishedFileId = item.publishedFileId,
            title = localizedDetail?.title?.ifBlank { apiTitle }?.ifBlank { item.title } ?: item.title,
            authorName = item.authorName,
            previewImageUrl = detail.stringValue("preview_url").ifBlank { item.previewImageUrl },
            description = localizedDetail?.description?.ifBlank { apiDescription } ?: apiDescription,
            fileSizeBytes = detail.longValue("file_size"),
            timeUpdatedEpochSeconds = detail.longValue("time_updated"),
            subscriptions = detail.longValue("subscriptions"),
            favorited = detail.longValue("favorited"),
            views = detail.longValue("views"),
            tags = detail["tags"].tagNames(),
            requiredItems = requiredItems,
            workshopUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=${item.publishedFileId}&l=${languagePreferenceProvider().requestValue}",
        )
    }

    private fun loadPublishedFileDetails(
        appId: UInt,
        publishedFileIds: List<ULong>,
    ): List<JsonObject> {
        if (publishedFileIds.isEmpty()) {
            return emptyList()
        }

        val formBody = FormBody.Builder()
            .add("itemcount", publishedFileIds.size.toString())
            .add("appid", appId.toString())
            .apply {
                publishedFileIds.forEachIndexed { index, publishedFileId ->
                    add("publishedfileids[$index]", publishedFileId.toString())
                }
            }
            .build()

        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("ISteamRemoteStorage/GetPublishedFileDetails/v1/").build())
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Workshop detail request failed: ${response.code}")
            }

            val payload = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            return payload["response"]
                ?.jsonObject
                ?.get("publishedfiledetails")
                ?.jsonArray
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
        }
    }

    private fun loadLocalizedDetailPage(item: WorkshopBrowseItem): LocalizedWorkshopDetail {
        val request = Request.Builder()
            .url(
                communityBaseUrl.newBuilder()
                    .addPathSegments("sharedfiles/filedetails/")
                    .addQueryParameter("id", item.publishedFileId.toString())
                    .addQueryParameter("l", languagePreferenceProvider().requestValue)
                    .build(),
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Workshop community detail request failed: ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            return LocalizedWorkshopDetail(
                title = workshopTitleRegex.find(payload)?.groupValues?.getOrNull(1)?.let(SteamHtmlDecoder::stripTagsAndDecode).orEmpty(),
                description = extractDivInnerHtml(
                    payload = payload,
                    openingTag = """<div class="workshopItemDescription" id="highlightContent">""",
                )?.let(SteamHtmlDecoder::decodeWorkshopHtmlDescription).orEmpty(),
                requiredItems = extractRequiredItems(payload),
            )
        }
    }

    private data class LocalizedWorkshopDetail(
        val title: String,
        val description: String,
        val requiredItems: List<ParsedRequiredItem>,
    )

    private fun extractRequiredItems(payload: String): List<ParsedRequiredItem> {
        val container = extractDivInnerHtmlById(payload, "RequiredItems") ?: return emptyList()
        return requiredItemLinkRegex.findAll(container)
            .mapNotNull { match ->
                val workshopUrl = SteamHtmlDecoder.decode(match.groupValues[1])
                val publishedFileId = match.groupValues[2].toULongOrNull() ?: return@mapNotNull null
                val title = SteamHtmlDecoder.stripTagsAndDecode(match.groupValues[3])
                if (title.isBlank()) {
                    return@mapNotNull null
                }
                ParsedRequiredItem(
                    publishedFileId = publishedFileId,
                    title = title,
                    workshopUrl = workshopUrl,
                )
            }
            .distinctBy(ParsedRequiredItem::publishedFileId)
            .toList()
    }

    private fun enrichRequiredItems(
        fallbackAppId: UInt,
        items: List<ParsedRequiredItem>,
    ): List<WorkshopRequiredItem> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val detailsById = loadPublishedFileDetails(
            appId = fallbackAppId,
            publishedFileIds = items.map(ParsedRequiredItem::publishedFileId),
        ).associateBy { detail ->
            detail.stringValue("publishedfileid").toULongOrNull()
        }

        return items.map { item ->
            val detail = detailsById[item.publishedFileId]
            item.toWorkshopRequiredItem(
                appId = detail?.uintValue("consumer_app_id") ?: fallbackAppId,
                titleOverride = detail?.stringValue("title").orEmpty(),
                previewImageUrl = detail?.stringValue("preview_url").orEmpty(),
                descriptionSnippet = SteamHtmlDecoder.decodeWorkshopApiDescription(
                    detail?.stringValue("description").orEmpty(),
                ),
            )
        }
    }

    private data class ParsedRequiredItem(
        val publishedFileId: ULong,
        val title: String,
        val workshopUrl: String,
    ) {
        fun toWorkshopRequiredItem(
            appId: UInt,
            titleOverride: String,
            previewImageUrl: String,
            descriptionSnippet: String,
        ): WorkshopRequiredItem =
            WorkshopRequiredItem(
                appId = appId,
                publishedFileId = publishedFileId,
                title = titleOverride.ifBlank { title },
                previewImageUrl = previewImageUrl,
                descriptionSnippet = descriptionSnippet,
                workshopUrl = workshopUrl,
            )
    }

    private companion object {
        val workshopTitleRegex = Regex(
            """<div class="workshopItemTitle">(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val requiredItemLinkRegex = Regex(
            """<a\b[^>]*href="([^"]*filedetails/\?[^"]*\bid=(\d+)[^"]*)"[^>]*>\s*<div\b[^>]*class="requiredItem"[^>]*>(.*?)</div>\s*</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
    }
}

private fun extractDivInnerHtml(
    payload: String,
    openingTag: String,
): String? {
    val start = payload.indexOf(openingTag)
    if (start < 0) {
        return null
    }
    var cursor = start + openingTag.length
    var depth = 1
    while (cursor < payload.length) {
        val nextOpen = payload.indexOf("<div", cursor, ignoreCase = true).takeIf { it >= 0 }
        val nextClose = payload.indexOf("</div", cursor, ignoreCase = true).takeIf { it >= 0 }
        val nextIndex = listOfNotNull(nextOpen, nextClose).minOrNull() ?: break
        if (nextIndex == nextOpen) {
            depth += 1
            cursor = nextIndex + 4
            continue
        }
        depth -= 1
        if (depth == 0) {
            return payload.substring(start + openingTag.length, nextIndex)
        }
        cursor = nextIndex + 5
    }
    return null
}

private fun extractDivInnerHtmlById(
    payload: String,
    id: String,
): String? {
    val openingTag = Regex(
        """<div\b[^>]*\bid="${Regex.escape(id)}"[^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(payload) ?: return null
    return extractDivInnerHtml(payload, openingTag.value)
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.longValue(key: String): Long? =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

private fun JsonObject.uintValue(key: String): UInt? =
    this[key]?.jsonPrimitive?.contentOrNull?.toUIntOrNull()

private fun kotlinx.serialization.json.JsonElement?.tagNames(): List<String> =
    (this as? JsonArray)
        ?.mapNotNull { tag ->
            (tag as? JsonObject)?.get("tag")?.jsonPrimitive?.contentOrNull
        }
        .orEmpty()
