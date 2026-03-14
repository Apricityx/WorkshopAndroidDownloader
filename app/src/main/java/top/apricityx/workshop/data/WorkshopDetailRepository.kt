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
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("ISteamRemoteStorage/GetPublishedFileDetails/v1/").build())
            .post(
                FormBody.Builder()
                    .add("itemcount", "1")
                    .add("publishedfileids[0]", item.publishedFileId.toString())
                    .add("appid", item.appId.toString())
                    .build(),
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Workshop detail request failed: ${response.code}")
            }

            val payload = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val detail = payload["response"]
                ?.jsonObject
                ?.get("publishedfiledetails")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?: error("Workshop detail payload was empty")
            val localizedDetail = runCatching {
                loadLocalizedDetailPage(item)
            }.getOrNull()
            val apiTitle = detail.stringValue("title")
            val apiDescription = decodeApiDescription(detail.stringValue("description")).ifBlank {
                item.descriptionSnippet.ifBlank { "暂无描述。" }
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
                workshopUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=${item.publishedFileId}&l=${languagePreferenceProvider().requestValue}",
            )
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
                )?.let(::decodeHtmlDescription).orEmpty(),
            )
        }
    }

    private fun decodeApiDescription(raw: String): String {
        if (raw.isBlank()) {
            return ""
        }

        return SteamHtmlDecoder.decode(
            raw.replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("""<[^>]+>"""), " "),
        ).replace(Regex("""\n{3,}"""), "\n\n")
    }

    private fun decodeHtmlDescription(raw: String): String {
        if (raw.isBlank()) {
            return ""
        }

        return SteamHtmlDecoder.decode(
            raw.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "• ")
                .replace(Regex("""</li\s*>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("""</div\s*>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""<[^>]+>"""), " ")
        ).replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private data class LocalizedWorkshopDetail(
        val title: String,
        val description: String,
    )

    private companion object {
        val workshopTitleRegex = Regex(
            """<div class="workshopItemTitle">(.*?)</div>""",
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

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.longValue(key: String): Long? =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

private fun kotlinx.serialization.json.JsonElement?.tagNames(): List<String> =
    (this as? JsonArray)
        ?.mapNotNull { tag ->
            (tag as? JsonObject)?.get("tag")?.jsonPrimitive?.contentOrNull
        }
        .orEmpty()
