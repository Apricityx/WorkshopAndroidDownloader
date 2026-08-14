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
import java.util.concurrent.ConcurrentHashMap

class SteamGameRepository(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: HttpUrl = "https://store.steampowered.com/".toHttpUrl(),
    private val workshopBaseUrl: HttpUrl = "https://steamcommunity.com/".toHttpUrl(),
    private val languagePreferenceProvider: () -> SteamLanguagePreference = { SteamLanguagePreference.SimplifiedChinese },
) {
    private val workshopSupportCache = ConcurrentHashMap<UInt, Boolean>()

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
            .map(::resolveWorkshopSupport)
            .sortedBy { appIds.indexOf(it.appId) }
    }

    private fun resolveWorkshopSupport(game: SteamGame): SteamGame {
        if (game.supportsWorkshop) {
            workshopSupportCache[game.appId] = true
            return game
        }
        if (!game.storeType.isWorkshopEligibleStoreType()) {
            return game
        }

        val hasWorkshopPage = workshopSupportCache.getOrPut(game.appId) {
            runCatching { hasWorkshopBrowsePage(game.appId) }.getOrDefault(false)
        }
        return if (hasWorkshopPage) {
            game.copy(supportsWorkshop = true)
        } else {
            game
        }
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
            .header("User-Agent", STEAM_WEB_BROWSER_USER_AGENT)
            .header("Accept", STEAM_WEB_BROWSER_ACCEPT)
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

    private fun hasWorkshopBrowsePage(appId: UInt): Boolean {
        val requestUrl = workshopBaseUrl.newBuilder()
            .addPathSegments("workshop/browse/")
            .addQueryParameter("appid", appId.toString())
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", STEAM_WEB_BROWSER_USER_AGENT)
            .header("Accept", STEAM_WEB_BROWSER_ACCEPT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return false
            }

            val finalUrl = response.request.url
            return finalUrl.host == requestUrl.host &&
                finalUrl.encodedPath == "/workshop/browse/" &&
                finalUrl.queryParameter("appid") == appId.toString()
        }
    }

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
                supportsWorkshop = 30 in categories && data.stringValue("type").ifBlank { "game" }.isWorkshopEligibleStoreType(),
                storeType = data.stringValue("type").ifBlank { "game" },
            )
        }
    }

    private fun JsonElement.asWorkshopCategoryId(): Int? =
        jsonObject["id"]?.jsonPrimitive?.intOrNull

    private fun JsonObject.stringValue(key: String): String =
        get(key)?.jsonPrimitive?.content.orEmpty()

}

internal fun String.isWorkshopEligibleStoreType(): Boolean =
    equals("game", ignoreCase = true) || equals("software", ignoreCase = true)
