package top.apricityx.workshop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import top.apricityx.workshop.SteamLanguagePreference

class SteamGameRepository(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = "https://store.steampowered.com/".toHttpUrl(),
    private val languagePreferenceProvider: () -> SteamLanguagePreference = { SteamLanguagePreference.SimplifiedChinese },
) {
    suspend fun loadFeaturedWorkshopGames(): List<SteamGame> = lookupGamesByIds(featuredWorkshopGameIds)

    suspend fun searchWorkshopGames(query: String): List<SteamGame> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext emptyList<SteamGame>()
        }

        val directAppId = trimmed.toUIntOrNull()
        val directMatch = if (directAppId != null) lookupGame(directAppId) else null
        val suggestedIds = loadSearchSuggestionIds(trimmed)
        val loadedGames = lookupGamesByIds(
            buildList<UInt> {
                if (directAppId != null) {
                    add(directAppId)
                }
                addAll(suggestedIds)
            },
        )

        val combined = buildList<SteamGame> {
            if (directMatch != null) {
                add(directMatch)
            }
            addAll(loadedGames)
        }

        return@withContext combined
            .filter(SteamGame::supportsWorkshop)
            .distinctBy(SteamGame::appId)
    }

    suspend fun lookupGame(appId: UInt): SteamGame? =
        lookupGamesByIds(listOf(appId)).firstOrNull()

    suspend fun lookupGamesByIds(appIds: List<UInt>): List<SteamGame> = withContext(Dispatchers.IO) {
        appIds.distinct()
            .flatMap { appId ->
                val payload = executeStringRequest(buildAppDetailsUrl(appId))
                SteamGameParsers.parseAppDetails(payload, json)
            }
            .sortedBy { appIds.indexOf(it.appId) }
    }

    private fun loadSearchSuggestionIds(query: String): List<UInt> {
        val languagePreference = languagePreferenceProvider()
        val url = baseUrl.newBuilder()
            .addPathSegments("search/suggest")
            .addQueryParameter("term", query)
            .addQueryParameter("f", "games")
            .addQueryParameter("cc", "US")
            .addQueryParameter("realm", "1")
            .addQueryParameter("l", languagePreference.requestValue)
            .build()

        return SteamGameParsers.parseSearchSuggestionIds(executeStringRequest(url))
    }

    private fun executeStringRequest(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Steam request failed: ${response.code} url=$url")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun buildAppDetailsUrl(appId: UInt): HttpUrl =
        baseUrl.newBuilder()
            .addPathSegments("api/appdetails")
            .addQueryParameter("appids", appId.toString())
            .addQueryParameter("l", languagePreferenceProvider().requestValue)
            .addQueryParameter("cc", "US")
            .build()

    companion object {
        val featuredWorkshopGameIds = listOf(
            646570u,
            294100u,
            4000u,
            255710u,
            322330u,
            431960u,
            602960u,
            108600u,
        )

        private const val USER_AGENT = "WorkshopOnAndroid/1.0"
    }
}

internal object SteamGameParsers {
    private val searchSuggestionRegex = Regex(
        """data-ds-appid="(\d+)".*?<div class="match_name">(.*?)</div>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    fun parseSearchSuggestionIds(payload: String): List<UInt> =
        searchSuggestionRegex.findAll(payload)
            .mapNotNull { match -> match.groupValues[1].toUIntOrNull() }
            .distinct()
            .toList()

    fun parseAppDetails(payload: String, json: Json): List<SteamGame> {
        val root = json.parseToJsonElement(payload).jsonObject
        return root.values.mapNotNull { entry ->
            val wrapper = entry.jsonObject
            val success = wrapper["success"]?.jsonPrimitive?.booleanOrNull == true
            if (!success) {
                return@mapNotNull null
            }

            val data = wrapper["data"]?.jsonObject ?: return@mapNotNull null
            val appId = data["steam_appid"]?.jsonPrimitive?.intOrNull?.toUInt() ?: return@mapNotNull null
            val categories = data["categories"]
                ?.jsonArray
                ?.mapNotNull { category -> category.asWorkshopCategoryId() }
                .orEmpty()

            SteamGame(
                appId = appId,
                name = data.stringValue("name"),
                shortDescription = SteamHtmlDecoder.stripTagsAndDecode(data.stringValue("short_description")),
                headerImageUrl = data.stringValue("header_image"),
                capsuleImageUrl = data.stringValue("capsule_imagev5").ifBlank { data.stringValue("capsule_image") },
                supportsWorkshop = 30 in categories,
            )
        }
    }

    private fun JsonElement.asWorkshopCategoryId(): Int? =
        jsonObject["id"]?.jsonPrimitive?.intOrNull

    private fun JsonObject.stringValue(key: String): String =
        get(key)?.jsonPrimitive?.content.orEmpty()
}
