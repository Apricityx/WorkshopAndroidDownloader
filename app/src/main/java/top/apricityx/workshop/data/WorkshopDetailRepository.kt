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

class WorkshopDetailRepository(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = "https://api.steampowered.com/".toHttpUrl(),
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

            WorkshopItemDetail(
                appId = item.appId,
                publishedFileId = item.publishedFileId,
                title = detail.stringValue("title").ifBlank { item.title },
                authorName = item.authorName,
                previewImageUrl = detail.stringValue("preview_url").ifBlank { item.previewImageUrl },
                description = decodeDescription(detail.stringValue("description")).ifBlank {
                    item.descriptionSnippet.ifBlank { "暂无描述。" }
                },
                fileSizeBytes = detail.longValue("file_size"),
                timeUpdatedEpochSeconds = detail.longValue("time_updated"),
                subscriptions = detail.longValue("subscriptions"),
                favorited = detail.longValue("favorited"),
                views = detail.longValue("views"),
                tags = detail["tags"].tagNames(),
                workshopUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=${item.publishedFileId}",
            )
        }
    }

    private fun decodeDescription(raw: String): String {
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
