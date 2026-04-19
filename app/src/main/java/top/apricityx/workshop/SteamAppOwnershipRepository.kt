package top.apricityx.workshop

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import top.apricityx.workshop.steam.protocol.applyDefaultHttpTimeouts
import top.apricityx.workshop.steam.protocol.applySteamHttpCompatibility

enum class SteamAppOwnershipStatus {
    Owned,
    NotOwned,
    Unknown,
}

class SteamAppOwnershipRepository(
    context: Context,
    private val steamAuthRepository: SteamAuthRepository,
    private val settingsRepository: DownloadSettingsRepository = DownloadSettingsRepository(context.applicationContext),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val appContext = context.applicationContext
    private val experimentalWorkshopDirectAccessRuntime =
        createExperimentalWorkshopDirectAccessRuntime(appContext.filesDir)
    private val ownedAppsCache = ConcurrentHashMap<String, OwnedAppsCacheEntry>()

    suspend fun ownershipStatus(
        accountId: String?,
        appId: UInt,
    ): SteamAppOwnershipStatus = withContext(Dispatchers.IO) {
        val resolvedAccountId = accountId ?: return@withContext SteamAppOwnershipStatus.Unknown
        if (steamAuthRepository.accountSessionFor(resolvedAccountId) == null) {
            return@withContext SteamAppOwnershipStatus.Unknown
        }

        val ownedApps = loadOwnedAppIds(resolvedAccountId) ?: return@withContext SteamAppOwnershipStatus.Unknown
        if (appId in ownedApps) {
            SteamAppOwnershipStatus.Owned
        } else {
            SteamAppOwnershipStatus.NotOwned
        }
    }

    private fun loadOwnedAppIds(accountId: String): Set<UInt>? {
        val now = System.currentTimeMillis()
        ownedAppsCache[accountId]
            ?.takeIf { entry -> entry.expiresAtMillis > now }
            ?.let(OwnedAppsCacheEntry::appIds)
            ?.let { cached -> return cached }

        val loaded = runCatching { requestOwnedAppIds(accountId) }.getOrNull() ?: return null
        ownedAppsCache[accountId] = OwnedAppsCacheEntry(
            appIds = loaded,
            expiresAtMillis = now + OWNED_APPS_CACHE_TTL_MILLIS,
        )
        return loaded
    }

    private fun requestOwnedAppIds(accountId: String): Set<UInt> {
        val steamWebCookieJar = SteamWebSessionCookieJar(
            projectedCookiesProvider = { url ->
                steamAuthRepository.blockingProjectedCookiesFor(
                    url = url,
                    accountId = accountId,
                )
            },
        )
        val client = OkHttpClient.Builder()
            .applyDefaultHttpTimeouts()
            .applySteamHttpCompatibility()
            .applyAppNetworkLogging("steam-owned-apps")
            .cookieJar(steamWebCookieJar)
            .hostnameVerifier(experimentalWorkshopDirectAccessRuntime.hostnameVerifier)
            .addInterceptor(
                SteamAuthenticatedCleartextInterceptor(
                    hasAuthenticatedSteamSession = {
                        steamAuthRepository.accountSessionFor(accountId) != null
                    },
                    allowAuthenticatedCleartextHttpProvider = settingsRepository::isSteamAuthenticatedCleartextHttpAllowed,
                ),
            )
            .addInterceptor(SteamLanguageInterceptor(settingsRepository::getSteamLanguagePreference))
            .addExperimentalWorkshopDirectAccess(
                runtime = experimentalWorkshopDirectAccessRuntime,
                enabledProvider = {
                    ExperimentalWorkshopDirectAccessFallbackNotifier.isDirectAccessAllowed(
                        settingsRepository.isExperimentalWorkshopDirectAccessEnabled(),
                    )
                },
                steamCookieJar = steamWebCookieJar,
            )
            .build()

        primeStoreSession(client)

        val request = Request.Builder()
            .url(STEAM_OWNED_APPS_URL)
            .header("Accept", "application/json")
            .header("User-Agent", STEAM_WEB_SESSION_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Steam owned-apps request failed: ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            return parseOwnedAppIds(payload)
                ?: error("Steam owned-apps response missing rgOwnedApps")
        }
    }

    private fun primeStoreSession(client: OkHttpClient) {
        client.newCall(
            Request.Builder()
                .url(STEAM_STORE_PRIME_URL)
                .header("User-Agent", STEAM_WEB_SESSION_USER_AGENT)
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) {
                error("Steam store session prime failed: ${response.code}")
            }
        }
    }

    private fun parseOwnedAppIds(payload: String): Set<UInt>? =
        runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            root["rgOwnedApps"]
                ?.jsonArray
                ?.mapNotNull { element ->
                    element.jsonPrimitive.intOrNull
                        ?.takeIf { value -> value >= 0 }
                        ?.toUInt()
                        ?: element.jsonPrimitive.contentOrNull?.toUIntOrNull()
                }
                ?.toSet()
        }.getOrNull()

    private data class OwnedAppsCacheEntry(
        val appIds: Set<UInt>,
        val expiresAtMillis: Long,
    )

    private companion object {
        private const val OWNED_APPS_CACHE_TTL_MILLIS = 15 * 60 * 1000L
        private const val STEAM_WEB_SESSION_USER_AGENT = "WorkshopOnAndroid/1.0"
        private const val STEAM_STORE_PRIME_URL = "https://store.steampowered.com/account/preferences/"
        private const val STEAM_OWNED_APPS_URL = "https://store.steampowered.com/dynamicstore/userdata/"
    }
}
