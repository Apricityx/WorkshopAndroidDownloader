package top.apricityx.workshop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import top.apricityx.workshop.SteamLanguagePreference
import top.apricityx.workshop.WorkshopBrowseSortOption
import top.apricityx.workshop.WorkshopBrowseTimeWindow
import top.apricityx.workshop.steam.protocol.SteamPublishedFileQueryResult

class WorkshopBrowseRepository(
    private val client: OkHttpClient,
    private val detailClient: OkHttpClient = createWorkshopBrowseDetailClient(client),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = "https://steamcommunity.com/".toHttpUrl(),
    private val detailBaseUrl: HttpUrl = "https://api.steampowered.com/".toHttpUrl(),
    private val languagePreferenceProvider: () -> SteamLanguagePreference = { SteamLanguagePreference.SimplifiedChinese },
) {
    suspend fun browseGameWorkshop(
        appId: UInt,
        searchQuery: String,
        sortOption: WorkshopBrowseSortOption = WorkshopBrowseSortOption.MostPopular,
        timeWindow: WorkshopBrowseTimeWindow = WorkshopBrowseTimeWindow.OneWeek,
        page: Int = 1,
    ): WorkshopBrowsePage = withContext(Dispatchers.IO) {
        val urlBuilder = baseUrl.newBuilder()
            .addPathSegments("workshop/browse/")
            .addQueryParameter("appid", appId.toString())
            .addQueryParameter("searchtext", searchQuery)
            .addQueryParameter("childpublishedfileid", "0")
            .addQueryParameter("l", languagePreferenceProvider().requestValue)
            .addQueryParameter("browsesort", sortOption.browseSortValue)
            .addQueryParameter("section", "readytouseitems")
            .addQueryParameter("actualsort", sortOption.actualSortValue)
            .addQueryParameter("p", page.toString())
            .addQueryParameter("numperpage", "30")
        if (sortOption.supportsTimeWindow) {
            urlBuilder.addQueryParameter("days", timeWindow.daysValue.toString())
        }
        val url = urlBuilder.build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Workshop browse request failed: ${response.code}")
            }
            val pageResult = WorkshopBrowseParser.parse(
                payload = response.body?.string().orEmpty(),
                page = page,
                json = json,
            )
            val fileSizes = runCatching {
                loadFileSizes(pageResult.items)
            }.getOrDefault(emptyMap())
            if (fileSizes.isEmpty()) {
                pageResult
            } else {
                pageResult.copy(
                    items = pageResult.items.map { item ->
                        item.copy(fileSizeBytes = fileSizes[item.publishedFileId] ?: item.fileSizeBytes)
                    },
                )
            }
        }
    }

    private fun loadFileSizes(items: List<WorkshopBrowseItem>): Map<ULong, Long> {
        if (items.isEmpty() || items.all { it.fileSizeBytes != null }) {
            return emptyMap()
        }

        val request = Request.Builder()
            .url(detailBaseUrl.newBuilder().addPathSegments("ISteamRemoteStorage/GetPublishedFileDetails/v1/").build())
            .post(
                FormBody.Builder().apply {
                    add("itemcount", items.size.toString())
                    add("appid", items.first().appId.toString())
                    items.forEachIndexed { index, item ->
                        add("publishedfileids[$index]", item.publishedFileId.toString())
                    }
                }.build(),
            )
            .build()

        detailClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Workshop detail request failed: ${response.code}")
            }

            return json.parseToJsonElement(response.body?.string().orEmpty())
                .jsonObject["response"]
                ?.jsonObject
                ?.get("publishedfiledetails")
                ?.jsonArray
                ?.mapNotNull { detail ->
                    val detailObject = detail.jsonObject
                    val publishedFileId = detailObject["publishedfileid"]?.jsonPrimitive?.contentOrNull?.toULongOrNull()
                    val fileSizeBytes = detailObject["file_size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    if (publishedFileId != null && fileSizeBytes != null) {
                        publishedFileId to fileSizeBytes
                    } else {
                        null
                    }
                }
                ?.toMap()
                .orEmpty()
        }
    }

    private companion object {
        const val USER_AGENT = "WorkshopOnAndroid/1.0"
    }
}

internal fun createWorkshopBrowseDetailClient(baseClient: OkHttpClient): OkHttpClient =
    baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .build()

internal object WorkshopBrowseParser {
    private val itemBlockRegex = Regex(
        """<div\b[^>]*class="workshopItem"[^>]*>(.*?<div class="workshopItemAuthorName ellipsis">.*?</div>.*?)</div>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val itemHeaderRegex = Regex(
        """<a\b[^>]*href="[^"]*?id=(\d+)[^"]*"[^>]*class="ugc"[^>]*data-appid="(\d+)"[^>]*data-publishedfileid="\d+"[^>]*>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val itemPreviewRegex = Regex(
        """class="workshopItemPreviewImage[^"]*"\s+src="([^"]+)"""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val itemTitleRegex = Regex(
        """class="workshopItemTitle ellipsis">(.*?)</div>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val itemAuthorRegex = Regex(
        """class="workshopItemAuthorName ellipsis">.*?<a\b[^>]*>(.*?)</a>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val hoverRegex = Regex(
        """SharedFileBindMouseHover\(\s*"sharedfile_(\d+)"\s*,\s*false\s*,\s*(\{.*?\})\s*\);""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val ssrRenderContextRegex = Regex(
        """window\.SSR\.renderContext=JSON\.parse\("(.+?)"\);""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun parse(
        payload: String,
        page: Int,
        json: Json,
    ): WorkshopBrowsePage {
        val descriptions = hoverRegex.findAll(payload)
            .associate { match ->
                val fileId = match.groupValues[1].toULong()
                val description = runCatching {
                    json.parseToJsonElement(match.groupValues[2])
                        .jsonObject["description"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        .orEmpty()
                }.getOrDefault("")
                fileId to SteamHtmlDecoder.stripTagsAndDecode(description)
            }

        val items = itemBlockRegex.findAll(payload)
            .mapNotNull { blockMatch ->
                val block = blockMatch.groupValues[1]
                val headerMatch = itemHeaderRegex.find(block) ?: return@mapNotNull null
                val publishedFileId = headerMatch.groupValues[1].toULongOrNull() ?: return@mapNotNull null
                val appId = headerMatch.groupValues[2].toUIntOrNull() ?: return@mapNotNull null
                val previewImageUrl = itemPreviewRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
                val title = itemTitleRegex.find(block)?.groupValues?.getOrNull(1)?.let(SteamHtmlDecoder::stripTagsAndDecode).orEmpty()
                val authorName = itemAuthorRegex.find(block)?.groupValues?.getOrNull(1)?.let(SteamHtmlDecoder::stripTagsAndDecode).orEmpty()
                WorkshopBrowseItem(
                    appId = appId,
                    publishedFileId = publishedFileId,
                    previewImageUrl = previewImageUrl,
                    title = title,
                    authorName = authorName,
                    descriptionSnippet = descriptions[publishedFileId].orEmpty(),
                )
            }
            .toList()

        val hasNextPage = payload.contains("""&p=${page + 1}""") &&
            payload.contains("""class='pagebtn'""")

        val legacyPage = WorkshopBrowsePage(
            items = items,
            page = page,
            hasNextPage = hasNextPage,
        )
        if (legacyPage.items.isNotEmpty() || legacyPage.hasNextPage) {
            return legacyPage
        }
        return parseSsrRenderContext(payload = payload, fallbackPage = legacyPage, json = json)
    }

    private fun parseSsrRenderContext(
        payload: String,
        fallbackPage: WorkshopBrowsePage,
        json: Json,
    ): WorkshopBrowsePage {
        val encodedRenderContext = ssrRenderContextRegex.find(payload)?.groupValues?.getOrNull(1) ?: return fallbackPage
        val renderContext = decodeJsonStringLiteral(encodedRenderContext, json) ?: return fallbackPage
        val renderContextObject = runCatching {
            json.parseToJsonElement(renderContext) as? JsonObject
        }.getOrNull() ?: return fallbackPage
        val queryData = runCatching {
            renderContextObject.stringValueOrNull("queryData")
        }.getOrNull() ?: return fallbackPage
        val queryEntries = runCatching {
            json.parseToJsonElement(queryData)
                .asJsonObject()
                ?.arrayValueOrNull("queries")
                .orEmpty()
        }.getOrNull() ?: return fallbackPage

        val creatorNames = buildMap {
            queryEntries.forEach { entry ->
                val queryObject = entry.asJsonObject() ?: return@forEach
                val queryKey = queryObject.arrayValueOrNull("queryKey").orEmpty()
                val keyName = queryKey.firstOrNull()?.stringContentOrNull()
                if (keyName != "PlayerLinkDetails") {
                    return@forEach
                }
                val steamId = queryKey.getOrNull(1)?.stringContentOrNull() ?: return@forEach
                val personaName = queryObject.objectValue("state")
                    ?.objectValue("data")
                    ?.objectValue("public_data")
                    ?.stringValueOrNull("persona_name")
                    .orEmpty()
                if (personaName.isNotBlank()) {
                    put(steamId, personaName)
                }
            }
        }

        val browseData = queryEntries.firstNotNullOfOrNull { entry ->
            entry.asJsonObject()
                ?.objectValue("state")
                ?.objectValue("data")
                ?.takeIf { data ->
                    data.intValueOrNull("current_page") != null &&
                        data.intValueOrNull("total_pages") != null &&
                        data.arrayValueOrNull("results") != null
                }
        } ?: return fallbackPage

        val currentPage = browseData.intValueOrNull("current_page") ?: fallbackPage.page
        val totalPages = browseData.intValueOrNull("total_pages") ?: currentPage
        val items = browseData.arrayValueOrNull("results")
            .orEmpty()
            .mapNotNull { result ->
                val item = result.asJsonObject() ?: return@mapNotNull null
                val publishedFileId = item.ulongValueOrNull("publishedfileid")
                    ?: return@mapNotNull null
                val appId = item.uintValueOrNull("consumer_appid")
                    ?: return@mapNotNull null
                val creatorSteamId = item.stringValueOrNull("creator")
                WorkshopBrowseItem(
                    appId = appId,
                    publishedFileId = publishedFileId,
                    previewImageUrl = item.stringValueOrNull("preview_url").orEmpty(),
                    title = item.stringValueOrNull("title").orEmpty(),
                    authorName = creatorSteamId?.let(creatorNames::get).orEmpty(),
                    descriptionSnippet = item.stringValueOrNull("short_description").orEmpty(),
                    fileSizeBytes = item.longValueOrNull("file_size"),
                )
            }

        return WorkshopBrowsePage(
            items = items,
            page = currentPage,
            hasNextPage = currentPage < totalPages,
        )
    }

    private fun decodeJsonStringLiteral(
        encoded: String,
        json: Json,
    ): String? =
        runCatching {
            json.parseToJsonElement(""""$encoded"""")
                .jsonPrimitive
                .content
        }.getOrNull()
}

private fun JsonElement?.asJsonObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.stringContentOrNull(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

private fun JsonObject.objectValue(key: String): JsonObject? =
    this[key] as? JsonObject

private fun JsonObject.arrayValueOrNull(key: String): JsonArray? =
    this[key] as? JsonArray

private fun JsonObject.stringValueOrNull(key: String): String? =
    this[key].stringContentOrNull()

private fun JsonObject.intValueOrNull(key: String): Int? =
    stringValueOrNull(key)?.toIntOrNull()

private fun JsonObject.longValueOrNull(key: String): Long? =
    stringValueOrNull(key)?.toLongOrNull()

private fun JsonObject.uintValueOrNull(key: String): UInt? =
    stringValueOrNull(key)?.toUIntOrNull()

private fun JsonObject.ulongValueOrNull(key: String): ULong? =
    stringValueOrNull(key)?.toULongOrNull()

internal fun SteamPublishedFileQueryResult.toWorkshopBrowsePage(page: Int, pageSize: Int): WorkshopBrowsePage =
    WorkshopBrowsePage(
        items = items.map { item ->
            WorkshopBrowseItem(
                appId = item.appId,
                publishedFileId = item.publishedFileId,
                previewImageUrl = item.previewUrl,
                title = item.title,
                authorName = "",
                descriptionSnippet = item.description,
                fileSizeBytes = item.fileSizeBytes,
            )
        },
        page = page,
        hasNextPage = total > page * pageSize || !nextCursor.isNullOrBlank(),
    )
