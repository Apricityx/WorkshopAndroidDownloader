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
        val languagePreference = languagePreferenceProvider()
        val detail = loadPublishedFileDetails(item.appId, listOf(item.publishedFileId))
            .firstOrNull()
            ?: error("Workshop detail payload was empty")
        val localizedDetail = runCatching {
            loadLocalizedDetailPage(item, languagePreference.requestValue)
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
            workshopUrl = buildWorkshopUrl(item.publishedFileId, languagePreference.requestValue),
            commentThreadContext = localizedDetail?.commentThreadContext,
            commentsUrl = buildWorkshopCommentsUrl(item.publishedFileId, languagePreference.requestValue, page = 1),
            commentCount = localizedDetail?.commentCount,
            commentTotalPages = localizedDetail?.commentCount?.let(::resolveAppCommentTotalPages),
            hasNextCommentPage = localizedDetail?.commentCount?.let { count -> count > COMMENT_PAGE_SIZE } == true,
        )
    }

    suspend fun loadWorkshopCommentPage(
        detail: WorkshopItemDetail,
        page: Int,
    ): WorkshopCommentPage = withContext(Dispatchers.IO) {
        loadWorkshopCommentPage(
            detail = detail,
            page = page,
            languageRequestValue = languagePreferenceProvider().requestValue,
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

    private fun loadLocalizedDetailPage(
        item: WorkshopBrowseItem,
        languageRequestValue: String,
    ): LocalizedWorkshopDetail {
        val request = Request.Builder()
            .url(
                communityBaseUrl.newBuilder()
                    .addPathSegments("sharedfiles/filedetails/")
                    .addQueryParameter("id", item.publishedFileId.toString())
                    .addQueryParameter("l", languageRequestValue)
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
                commentThreadContext = extractCommentThreadContext(payload),
                commentCount = extractCommentCount(payload),
            )
        }
    }

    private fun loadWorkshopCommentPage(
        detail: WorkshopItemDetail,
        page: Int,
        languageRequestValue: String,
    ): WorkshopCommentPage {
        val commentThreadContext = detail.commentThreadContext ?: error("Workshop comment thread context was missing")
        val safePage = page.coerceAtLeast(1)
        val start = (safePage - 1) * COMMENT_PAGE_SIZE
        val formBody = FormBody.Builder()
            .add("start", start.toString())
            .add("count", COMMENT_PAGE_SIZE.toString())
            .apply {
                commentThreadContext.sessionId?.takeIf(String::isNotBlank)?.let { add("sessionid", it) }
                commentThreadContext.extendedData?.takeIf(String::isNotBlank)?.let { add("extended_data", it) }
                commentThreadContext.feature2?.takeIf { it.isNotBlank() && it != "-1" }?.let { add("feature2", it) }
            }
            .build()
        val request = Request.Builder()
            .url(
                communityBaseUrl.newBuilder()
                    .addPathSegments(
                        "comment/PublishedFile_Public/render/${commentThreadContext.ownerId}/${commentThreadContext.featureId}/",
                    )
                    .addQueryParameter("l", languageRequestValue)
                    .build(),
            )
            .post(formBody)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Workshop comments request failed: ${response.code}")
            }
            val payload = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val commentCount = payload.longValue("total_count")
            val pageSize = payload.intValue("pagesize") ?: COMMENT_PAGE_SIZE
            val responseStart = payload.intValue("start")
            val commentsHtml = payload.stringValue("comments_html")
            val comments = extractComments(commentsHtml)
            val resolvedPage = if (responseStart != null && pageSize > 0) {
                (responseStart / pageSize) + 1
            } else {
                safePage
            }
            val totalPages = resolveCommentTotalPages(
                commentCount = commentCount,
                pageSize = pageSize,
            )
            return WorkshopCommentPage(
                commentsUrl = buildWorkshopCommentsUrl(detail.publishedFileId, languageRequestValue, resolveSteamCommentsPage(resolvedPage)),
                commentCount = commentCount,
                page = resolvedPage,
                totalPages = totalPages,
                hasPreviousPage = resolvedPage > 1,
                hasNextPage = when {
                    totalPages != null -> resolvedPage < totalPages
                    else -> comments.size >= pageSize
                },
                comments = comments,
            )
        }
    }

    private data class LocalizedWorkshopDetail(
        val title: String,
        val description: String,
        val requiredItems: List<ParsedRequiredItem>,
        val commentThreadContext: WorkshopCommentThreadContext? = null,
        val commentCount: Long? = null,
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

    private fun extractCommentCount(payload: String): Long? =
        totalCommentCountRegex.find(payload)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: totalCommentCountLabelRegex.find(payload)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(",", "")
                ?.trim()
                ?.toLongOrNull()

    private fun extractCommentThreadContext(payload: String): WorkshopCommentThreadContext? {
        val commentInit = commentInitDataRegex.find(payload)?.groupValues?.getOrNull(1) ?: return null
        val commentInitObject = runCatching { json.parseToJsonElement(commentInit).jsonObject }.getOrNull() ?: return null
        val ownerId = commentInitObject.stringValue("owner").ifBlank { return null }
        val featureId = commentInitObject.stringValue("feature").ifBlank { return null }
        val feature2 = commentInitObject.stringValue("feature2").ifBlank { null }
        val extendedData = commentInitObject.stringValue("extended_data").ifBlank { null }
        val sessionId = sessionIdRegex.find(payload)?.groupValues?.getOrNull(1).orEmpty().ifBlank { null }
        return WorkshopCommentThreadContext(
            ownerId = ownerId,
            featureId = featureId,
            feature2 = feature2,
            extendedData = extendedData,
            sessionId = sessionId,
        )
    }

    private fun resolveCommentTotalPages(
        commentCount: Long?,
        pageSize: Int,
    ): Int? {
        if (pageSize <= 0) {
            return null
        }
        return commentCount?.let { count ->
            if (count <= 0L) {
                1
            } else {
                ((count + pageSize - 1) / pageSize).toInt()
            }
        }
    }

    private fun resolveAppCommentTotalPages(commentCount: Long): Int =
        if (commentCount <= 0L) {
            1
        } else {
            ((commentCount + COMMENT_PAGE_SIZE - 1) / COMMENT_PAGE_SIZE).toInt()
        }

    private fun resolveSteamCommentsPage(appCommentPage: Int): Int =
        (((appCommentPage - 1) * COMMENT_PAGE_SIZE) / STEAM_COMMENTS_PAGE_SIZE) + 1

    private fun extractComments(payload: String): List<WorkshopComment> =
        commentBlockOpeningRegex.findAll(payload)
            .mapNotNull { openingMatch ->
                val id = openingMatch.groupValues[1]
                val block = extractDivBlock(
                    payload = payload,
                    openingTagStart = openingMatch.range.first,
                    openingTagLength = openingMatch.value.length,
                ) ?: return@mapNotNull null
                val authorMatch = commentAuthorRegex.find(block)
                val profileUrl = authorMatch?.groupValues?.getOrNull(1)?.let(SteamHtmlDecoder::decode)?.trim().orEmpty()
                val authorName = authorMatch?.groupValues?.getOrNull(2)?.let(SteamHtmlDecoder::stripTagsAndDecode).orEmpty()
                val postedEpochSeconds = commentTimestampDataRegex.find(block)?.groupValues?.getOrNull(1)?.toLongOrNull()
                val postedDisplayText = commentTimestampTextRegex.find(block)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(SteamHtmlDecoder::stripTagsAndDecode)
                    .orEmpty()
                val content = commentTextRegex.find(block)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(SteamHtmlDecoder::decodeWorkshopComment)
                    .orEmpty()
                if (content.isBlank()) {
                    return@mapNotNull null
                }
                WorkshopComment(
                    id = id,
                    authorName = authorName.ifBlank { "未知用户" },
                    profileUrl = profileUrl,
                    content = content,
                    postedEpochSeconds = postedEpochSeconds,
                    postedDisplayText = postedDisplayText,
                )
            }
            .distinctBy(WorkshopComment::id)
            .toList()

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

    private fun buildWorkshopUrl(
        publishedFileId: ULong,
        languageRequestValue: String,
    ): String = "https://steamcommunity.com/sharedfiles/filedetails/?id=$publishedFileId&l=$languageRequestValue"

    private fun buildWorkshopCommentsUrl(
        publishedFileId: ULong,
        languageRequestValue: String,
        page: Int,
    ): String = buildString {
        append("https://steamcommunity.com/sharedfiles/filedetails/comments/")
        append(publishedFileId)
        append("?l=")
        append(languageRequestValue)
        if (page > 1) {
            append("&ctp=")
            append(page)
        }
    }

    private companion object {
        const val COMMENT_PAGE_SIZE = 5
        const val STEAM_COMMENTS_PAGE_SIZE = 50
        val workshopTitleRegex = Regex(
            """<div class="workshopItemTitle">(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val commentInitDataRegex = Regex(
            """InitializeCommentThread\(\s*"PublishedFile_Public"\s*,\s*"[^"]+"\s*,\s*(\{.*?\})\s*,\s*'https://steamcommunity\.com/comment/PublishedFile_Public/'""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val sessionIdRegex = Regex(
            """g_sessionID\s*=\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE,
        )
        val totalCommentCountRegex = Regex(
            """"total_count"\s*:\s*(\d+)""",
            RegexOption.IGNORE_CASE,
        )
        val totalCommentCountLabelRegex = Regex(
            """id="commentthread_[^"]*_totalcount">([^<]+)<""",
            RegexOption.IGNORE_CASE,
        )
        val commentBlockOpeningRegex = Regex(
            """<div\b[^>]*class="[^"]*\bcommentthread_comment\b[^"]*"[^>]*id="comment_([^"]+)"[^>]*>""",
            RegexOption.IGNORE_CASE,
        )
        val commentAuthorRegex = Regex(
            """<a\b[^>]*class="[^"]*\bcommentthread_author_link\b[^"]*"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val commentTimestampDataRegex = Regex(
            "<span\\b[^>]*class=\"[^\"]*\\bcommentthread_comment_timestamp\\b[^\"]*\"[^>]*\\bdata-timestamp=\"(\\d+)\"",
            RegexOption.IGNORE_CASE,
        )
        val commentTimestampTextRegex = Regex(
            """<span\b[^>]*class="[^"]*\bcommentthread_comment_timestamp\b[^"]*"[^>]*>(.*?)</span>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val commentTextRegex = Regex(
            """<div\b[^>]*class="[^"]*\bcommentthread_comment_text\b[^"]*"[^>]*>(.*?)</div>""",
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

private fun extractDivBlock(
    payload: String,
    openingTagStart: Int,
    openingTagLength: Int,
): String? {
    var cursor = openingTagStart + openingTagLength
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
            val closingTagEnd = payload.indexOf('>', nextIndex).takeIf { it >= 0 } ?: return null
            return payload.substring(openingTagStart, closingTagEnd + 1)
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

private fun JsonObject.intValue(key: String): Int? =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

private fun JsonObject.uintValue(key: String): UInt? =
    this[key]?.jsonPrimitive?.contentOrNull?.toUIntOrNull()

private fun kotlinx.serialization.json.JsonElement?.tagNames(): List<String> =
    (this as? JsonArray)
        ?.mapNotNull { tag ->
            (tag as? JsonObject)?.get("tag")?.jsonPrimitive?.contentOrNull
        }
        .orEmpty()
