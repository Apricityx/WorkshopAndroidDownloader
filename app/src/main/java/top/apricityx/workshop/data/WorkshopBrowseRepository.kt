package top.apricityx.workshop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import top.apricityx.workshop.SteamLanguagePreference
import top.apricityx.workshop.WorkshopBrowseSortOption
import top.apricityx.workshop.WorkshopBrowseTimeWindow

class WorkshopBrowseRepository(
    private val client: OkHttpClient,
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
        if (items.isEmpty()) {
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

        client.newCall(request).execute().use { response ->
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

        return WorkshopBrowsePage(
            items = items,
            page = page,
            hasNextPage = hasNextPage,
        )
    }
}
