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
    val additionalLogicalHosts: Set<String> = emptySet(),
    val bootstrapFakeServerName: String = "",
    val bootstrapIgnoreSslCertVerification: Boolean = true,
)

internal val SteamCommunityWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-community",
    // v4 invalidates snapshots written before the Steam community/store route
    // format and fake-SNI mapping changed upstream.
    cacheFileName = "watt-route-cache-v4.json",
    supportedHosts = setOf(STEAM_COMMUNITY_HOST, "www.steamcommunity.com"),
    bootstrapForwardTargets = listOf("https://www.valvesoftware.com"),
    bootstrapFakeServerName = "www.valvesoftware.com",
    bootstrapIgnoreSslCertVerification = false,
)

internal val SteamImageWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-image",
    cacheFileName = "watt-image-route-cache-v1.json",
    supportedHosts = setOf(
        "steamcdn-a.akamaihd.net",
        "steamuserimages-a.akamaihd.net",
        "cdn.akamai.steamstatic.com",
        "community.akamai.steamstatic.com",
        "avatars.akamai.steamstatic.com",
        "store.akamai.steamstatic.com",
        "avatars.fastly.steamstatic.com",
        "images.steamusercontent.com",
    ),
    bootstrapForwardTargets = listOf("https://steamimage.rmbgame.net"),
    additionalLogicalHosts = setOf("images.steamusercontent.com"),
)

internal val SteamStoreWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-store",
    cacheFileName = "watt-store-route-cache-v4.json",
    supportedHosts = setOf(
        "api.steampowered.com",
        "store.steampowered.com",
        "help.steampowered.com",
        "login.steampowered.com",
        "checkout.steampowered.com",
    ),
    // Keep the offline route aligned with Watt's current Steam Store profile.
    // The previous Akamai/officecdn pair now presents a certificate for the
    // wrong hostname and fails before an HTTP request is sent.
    bootstrapForwardTargets = listOf("steamstore.rmbgame.net"),
    bootstrapFakeServerName = "steamstore-a.akamaihd.net",
    bootstrapIgnoreSslCertVerification = false,
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
    SteamImageWattToolkitRouteProfile,
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
    val forwardDns: WattToolkitForwardDns = WattToolkitForwardDns(),
)

internal fun createExperimentalWorkshopDirectAccessRuntime(
    filesDir: File,
    routeProfiles: List<WattToolkitRouteProfile> = defaultExperimentalWorkshopDirectAccessProfiles,
): ExperimentalWorkshopDirectAccessRuntime {
    val forwardDns = WattToolkitForwardDns()
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
        .dns(forwardDns)
        .trustWattToolkitForwardCertificates()
        .followRedirects(false)
        .followSslRedirects(false)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    return ExperimentalWorkshopDirectAccessRuntime(
        resolvers = resolvers,
        hostnameVerifier = hostnameVerifier,
        directHttpClient = directHttpClient,
        forwardDns = forwardDns,
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
                    forwardDns = runtime.forwardDns,
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
                ignoreSslCertVerification = routeProfile.bootstrapIgnoreSslCertVerification,
                fakeServerName = routeProfile.bootstrapFakeServerName,
            )
        }

internal data class ExperimentalGithubDirectAccessRuntime(
    val resolvers: List<WattToolkitWorkshopRouteResolver>,
    val hostnameVerifier: HostnameVerifier,
    val directHttpClient: OkHttpClient,
    val forwardDns: WattToolkitForwardDns = WattToolkitForwardDns(),
)

internal fun createExperimentalGithubDirectAccessRuntime(
    filesDir: File,
    routeProfiles: List<WattToolkitRouteProfile> = defaultExperimentalGithubDirectAccessProfiles,
): ExperimentalGithubDirectAccessRuntime {
    val forwardDns = WattToolkitForwardDns()
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
        .dns(forwardDns)
        .trustWattToolkitForwardCertificates()
        .followRedirects(false)
        .followSslRedirects(false)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    return ExperimentalGithubDirectAccessRuntime(
        resolvers = resolvers,
        hostnameVerifier = hostnameVerifier,
        directHttpClient = directHttpClient,
        forwardDns = forwardDns,
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
                forwardDns = runtime.forwardDns,
            ),
        )
    }
