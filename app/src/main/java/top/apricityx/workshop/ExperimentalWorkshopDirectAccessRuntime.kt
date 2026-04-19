package top.apricityx.workshop

import java.io.File
import javax.net.ssl.HostnameVerifier
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import top.apricityx.workshop.steam.protocol.applyDefaultHttpTimeouts

internal data class WattToolkitRouteProfile(
    val name: String,
    val cacheFileName: String,
    val supportedHosts: Set<String>,
    val bootstrapForwardTargets: List<String>,
)

internal val SteamCommunityWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-community",
    cacheFileName = "watt-route-cache.json",
    supportedHosts = setOf(STEAM_COMMUNITY_HOST, "www.steamcommunity.com"),
    bootstrapForwardTargets = listOf("steamcommunity.rmbgame.net"),
)

internal val SteamStoreWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-store",
    cacheFileName = "watt-store-route-cache.json",
    supportedHosts = setOf(
        "api.steampowered.com",
        "store.steampowered.com",
        "help.steampowered.com",
        "login.steampowered.com",
        "checkout.steampowered.com",
    ),
    bootstrapForwardTargets = listOf("steamstore.rmbgame.net"),
)

internal val GithubApiWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "github-api",
    cacheFileName = "watt-github-api-route-cache.json",
    supportedHosts = setOf("api.github.com"),
    bootstrapForwardTargets = listOf("githubapi.rmbgame.net"),
)

internal val GithubWebWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "github-web",
    cacheFileName = "watt-github-web-route-cache.json",
    supportedHosts = setOf("github.com"),
    bootstrapForwardTargets = emptyList(),
)

internal val GithubUserContentWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "githubusercontent",
    cacheFileName = "watt-githubusercontent-route-cache.json",
    supportedHosts = setOf(
        "raw.github.com",
        "raw.githubusercontent.com",
        "githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    ),
    bootstrapForwardTargets = emptyList(),
)

internal val DEFAULT_WATT_TOOLKIT_ROUTE_HOSTS: Set<String> = SteamCommunityWattToolkitRouteProfile.supportedHosts
internal val DEFAULT_WATT_TOOLKIT_STEAM_STORE_ROUTE_HOSTS: Set<String> = SteamStoreWattToolkitRouteProfile.supportedHosts
internal val DEFAULT_WATT_TOOLKIT_GITHUB_API_ROUTE_HOSTS: Set<String> = GithubApiWattToolkitRouteProfile.supportedHosts
internal val DEFAULT_WATT_TOOLKIT_GITHUB_WEB_ROUTE_HOSTS: Set<String> = GithubWebWattToolkitRouteProfile.supportedHosts
internal val DEFAULT_WATT_TOOLKIT_GITHUB_USERCONTENT_ROUTE_HOSTS: Set<String> = GithubUserContentWattToolkitRouteProfile.supportedHosts

private val defaultExperimentalWorkshopDirectAccessProfiles = listOf(
    SteamCommunityWattToolkitRouteProfile,
    SteamStoreWattToolkitRouteProfile,
)

private val defaultExperimentalGithubDirectAccessProfiles = listOf(
    GithubApiWattToolkitRouteProfile,
    GithubWebWattToolkitRouteProfile,
    GithubUserContentWattToolkitRouteProfile,
)

internal data class ExperimentalWorkshopDirectAccessRuntime(
    val resolvers: List<WattToolkitWorkshopRouteResolver>,
    val hostnameVerifier: HostnameVerifier,
    val directHttpClient: OkHttpClient,
)

internal fun createExperimentalWorkshopDirectAccessRuntime(
    filesDir: File,
    routeProfiles: List<WattToolkitRouteProfile> = defaultExperimentalWorkshopDirectAccessProfiles,
): ExperimentalWorkshopDirectAccessRuntime {
    val resolvers = routeProfiles.map { routeProfile ->
        WattToolkitWorkshopRouteResolver(
            routeProfile = routeProfile,
            routeStore = createFileBackedWattToolkitWorkshopRouteStore(
                filesDir = filesDir,
                routeProfile = routeProfile,
            ),
        )
    }
    val hostnameVerifier = WorkshopDirectHostnameVerifier { host ->
        resolvers.any { resolver -> resolver.allowsUnsafeHostnameBypass(host) }
    }
    val directHttpClient = OkHttpClient.Builder()
        .applyDefaultHttpTimeouts()
        .applyAppNetworkLogging("workshop-web")
        .hostnameVerifier(hostnameVerifier)
        .followRedirects(false)
        .followSslRedirects(false)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    return ExperimentalWorkshopDirectAccessRuntime(
        resolvers = resolvers,
        hostnameVerifier = hostnameVerifier,
        directHttpClient = directHttpClient,
    )
}

internal fun OkHttpClient.Builder.addExperimentalWorkshopDirectAccess(
    runtime: ExperimentalWorkshopDirectAccessRuntime,
    enabledProvider: () -> Boolean,
    steamCookieJar: CookieJar,
    fallbackNoticeSink: SteamDirectAccessFallbackNoticeSink = NoOpSteamDirectAccessFallbackNoticeSink,
): OkHttpClient.Builder =
    apply {
        runtime.resolvers.forEach { resolver ->
            addInterceptor(
                ExperimentalWorkshopDirectAccessInterceptor(
                    enabledProvider = enabledProvider,
                    routeResolver = resolver,
                    steamCookieJar = steamCookieJar,
                    directCallFactory = runtime.directHttpClient,
                    fallbackNoticeSink = fallbackNoticeSink,
                ),
            )
        }
    }

internal fun createFileBackedWattToolkitWorkshopRouteStore(
    filesDir: File,
    routeProfile: WattToolkitRouteProfile,
): FileBackedWattToolkitWorkshopRouteStore =
    FileBackedWattToolkitWorkshopRouteStore(
        file = File(filesDir, "workshop/network/${routeProfile.cacheFileName}"),
        fallbackLogicalHosts = routeProfile.supportedHosts,
    )

internal fun defaultBootstrapRouteForProfile(routeProfile: WattToolkitRouteProfile): WattToolkitWorkshopRoute? =
    routeProfile.bootstrapForwardTargets
        .takeIf(List<String>::isNotEmpty)
        ?.let { forwardTargets ->
            WattToolkitWorkshopRoute(
                logicalHosts = routeProfile.supportedHosts.map(String::lowercase).toSet(),
                forwardTargets = forwardTargets,
                ignoreSslCertVerification = true,
            )
        }

internal data class ExperimentalGithubDirectAccessRuntime(
    val resolvers: List<WattToolkitWorkshopRouteResolver>,
    val hostnameVerifier: HostnameVerifier,
    val directHttpClient: OkHttpClient,
)

internal fun createExperimentalGithubDirectAccessRuntime(
    filesDir: File,
    routeProfiles: List<WattToolkitRouteProfile> = defaultExperimentalGithubDirectAccessProfiles,
): ExperimentalGithubDirectAccessRuntime {
    val resolvers = routeProfiles.map { routeProfile ->
        WattToolkitWorkshopRouteResolver(
            routeProfile = routeProfile,
            routeStore = createFileBackedWattToolkitWorkshopRouteStore(
                filesDir = filesDir,
                routeProfile = routeProfile,
            ),
        )
    }
    val hostnameVerifier = WorkshopDirectHostnameVerifier { host ->
        resolvers.any { resolver -> resolver.allowsUnsafeHostnameBypass(host) }
    }
    val directHttpClient = OkHttpClient.Builder()
        .applyDefaultHttpTimeouts()
        .applyAppNetworkLogging("github-update")
        .hostnameVerifier(hostnameVerifier)
        .followRedirects(false)
        .followSslRedirects(false)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    return ExperimentalGithubDirectAccessRuntime(
        resolvers = resolvers,
        hostnameVerifier = hostnameVerifier,
        directHttpClient = directHttpClient,
    )
}

internal fun OkHttpClient.Builder.addExperimentalGithubDirectAccess(
    runtime: ExperimentalGithubDirectAccessRuntime,
): OkHttpClient.Builder =
    apply {
        addInterceptor(
            ExperimentalGithubDirectAccessInterceptor(
                enabledProvider = { true },
                routeResolvers = runtime.resolvers,
                directCallFactory = runtime.directHttpClient,
            ),
        )
    }
