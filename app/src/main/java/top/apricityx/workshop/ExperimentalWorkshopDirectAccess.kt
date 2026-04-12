package top.apricityx.workshop
import java.io.File
import java.io.IOException
import java.net.ProtocolException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import top.apricityx.workshop.steam.protocol.applyDefaultHttpTimeouts

internal class ExperimentalWorkshopDirectAccessInterceptor(
    private val enabledProvider: () -> Boolean,
    private val routeResolver: WattToolkitWorkshopRouteResolver,
    private val steamCookieJar: CookieJar,
    private val directCallFactory: Call.Factory,
    private val maxRedirects: Int = MAX_FOLLOW_UPS,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url
        if (!enabledProvider() || !routeResolver.supports(originalUrl.host)) {
            return chain.proceed(request)
        }

        val route = routeResolver.resolveRouteForHost(originalUrl.host)
        if (route == null) {
            workshopLogWarn(
                "Experimental workshop direct access has no Watt route; falling back to original host=${originalUrl.host}.",
            )
            return chain.proceed(request)
        }
        return executeDirectAccessRequest(request, route)
    }

    private fun executeDirectAccessRequest(
        initialLogicalRequest: Request,
        route: WattToolkitWorkshopRoute,
    ): Response {
        var logicalRequest = route.normalizeLogicalRequest(initialLogicalRequest)
        var followUpCount = 0
        while (true) {
            val networkRequest = buildNetworkRequest(logicalRequest, route)
            val response = directCallFactory.newCall(networkRequest).execute()
            steamCookieJar.saveForwardedResponse(logicalRequest.url, response.headers)
            val redirectTarget = response.redirectTarget(logicalRequest.url, route)
            if (redirectTarget == null) {
                return response.newBuilder()
                    .request(logicalRequest)
                    .build()
            }
            if (followUpCount >= maxRedirects) {
                response.close()
                throw ProtocolException("Too many experimental workshop direct-access redirects: $maxRedirects")
            }
            workshopLogInfo(
                "Experimental workshop direct access following redirect ${logicalRequest.url.host}${logicalRequest.url.encodedPath} -> ${redirectTarget.host}${redirectTarget.encodedPath}",
            )
            val nextLogicalRequest = buildRedirectRequest(
                previousLogicalRequest = logicalRequest,
                redirectUrl = redirectTarget,
                responseCode = response.code,
            )
            response.close()
            logicalRequest = nextLogicalRequest
            followUpCount++
        }
    }

    private fun buildNetworkRequest(
        logicalRequest: Request,
        route: WattToolkitWorkshopRoute,
    ): Request {
        val logicalUrl = route.normalizeLogicalUrl(
            url = logicalRequest.url,
            fallbackLogicalHost = logicalRequest.url.host,
        )
        val shouldForward = route.matchesLogicalHost(logicalUrl.host)
        val networkUrl = if (shouldForward) route.buildForwardedUrl(logicalUrl) else logicalUrl
        val cookieHeader = steamCookieJar.loadForRequest(logicalUrl).toCookieHeader().orEmpty()
        if (shouldForward) {
            workshopLogInfo(
                "Experimental workshop direct access rewriting ${logicalUrl.host}${logicalUrl.encodedPath} -> ${networkUrl.host}${networkUrl.encodedPath} ignoreSsl=${route.ignoreSslCertVerification}",
            )
        }
        return logicalRequest.newBuilder()
            .url(networkUrl)
            .removeHeader("Cookie")
            .apply {
                if (cookieHeader.isNotBlank()) {
                    header("Cookie", cookieHeader)
                }
                if (shouldForward) {
                    header("Host", logicalUrl.host)
                } else {
                    removeHeader("Host")
                }
            }
            .build()
    }

    private fun buildRedirectRequest(
        previousLogicalRequest: Request,
        redirectUrl: HttpUrl,
        responseCode: Int,
    ): Request {
        val preserveBody = responseCode == HTTP_TEMP_REDIRECT || responseCode == HTTP_PERM_REDIRECT
        val originalMethod = previousLogicalRequest.method
        val redirectMethod = when {
            preserveBody -> originalMethod
            originalMethod == HTTP_METHOD_GET || originalMethod == HTTP_METHOD_HEAD -> originalMethod
            else -> HTTP_METHOD_GET
        }
        val redirectBody: RequestBody? = if (redirectMethod == originalMethod) previousLogicalRequest.body else null
        return previousLogicalRequest.newBuilder()
            .url(redirectUrl)
            .method(redirectMethod, redirectBody)
            .apply {
                if (redirectBody == null) {
                    removeHeader("Transfer-Encoding")
                    removeHeader("Content-Length")
                    removeHeader("Content-Type")
                }
            }
            .build()
    }
}

internal class WorkshopDirectHostnameVerifier(
    private val defaultVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
    private val unsafeHostBypassProvider: (String) -> Boolean,
) : HostnameVerifier {
    override fun verify(
        hostname: String,
        session: SSLSession,
    ): Boolean {
        if (defaultVerifier.verify(hostname, session)) {
            return true
        }
        if (unsafeHostBypassProvider(hostname)) {
            workshopLogWarn("Experimental workshop direct access bypassed hostname verification for host=$hostname.")
            return true
        }
        return false
    }
}

internal class WattToolkitWorkshopRouteResolver(
    private val routeProfile: WattToolkitRouteProfile = SteamCommunityWattToolkitRouteProfile,
    private val client: OkHttpClient = defaultWattToolkitRouteClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val projectGroupsUrl: HttpUrl = WATT_ACCELERATOR_PROJECTGROUPS_URL.toHttpUrl(),
    private val routeStore: WattToolkitWorkshopRouteStore = NoOpWattToolkitWorkshopRouteStore,
    private val bootstrapRouteProvider: (WattToolkitRouteProfile) -> WattToolkitWorkshopRoute? = ::defaultBootstrapRouteForProfile,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val sleepProvider: (Long) -> Unit = { delayMs -> Thread.sleep(delayMs) },
) {
    private val lock = Any()
    private val normalizedSupportedHosts = routeProfile.supportedHosts.map(String::lowercase).toSet()
    @Volatile
    private var cachedRoute: WattToolkitWorkshopRoute? = null
    @Volatile
    private var cachedAtMs: Long = 0L
    @Volatile
    private var persistedRouteLoaded: Boolean = false

    fun supports(host: String): Boolean =
        host.lowercase() in normalizedSupportedHosts

    fun currentForwardHosts(): Set<String> =
        cachedRoute?.forwardHosts.orEmpty()

    fun allowsUnsafeHostnameBypass(host: String): Boolean =
        cachedRoute?.shouldBypassHostnameVerification(host) == true

    fun resolveSteamCommunityRoute(): WattToolkitWorkshopRoute? =
        resolveRouteForHost(STEAM_COMMUNITY_HOST)

    fun resolveRouteForHost(host: String): WattToolkitWorkshopRoute? {
        val normalizedHost = host.lowercase()
        if (normalizedHost !in normalizedSupportedHosts) {
            return null
        }
        val now = nowProvider()
        synchronized(lock) {
            restorePersistedRouteLocked(now)
            val cached = cachedRoute
            if (cached != null && cached.matchesLogicalHost(normalizedHost) && now - cachedAtMs < ROUTE_CACHE_TTL_MS) {
                return cached
            }
        }

        val fetched = runCatching(::fetchSupportedRouteWithRetries)
            .onFailure { error ->
                workshopLogWarn(
                    "Experimental workshop direct access failed to fetch Watt route: ${error.message}",
                    error,
                )
            }
            .getOrNull()
        synchronized(lock) {
            if (fetched != null) {
                cachedRoute = fetched
                cachedAtMs = now
                routeStore.save(
                    PersistedWattToolkitWorkshopRoute(
                        route = fetched,
                        cachedAtMs = now,
                    ),
                )
                workshopLogInfo(
                    "Experimental workshop direct access resolved Watt route forward=${fetched.forwardTargets.joinToString(";")} ignoreSsl=${fetched.ignoreSslCertVerification}",
                )
                return fetched
            }
            if (cachedRoute != null) {
                workshopLogInfo(
                    "Experimental workshop direct access using cached Watt route ageMs=${(now - cachedAtMs).coerceAtLeast(0L)} after fetch failure.",
                )
            }
            val cachedMatch = cachedRoute?.takeIf { it.matchesLogicalHost(normalizedHost) }
            if (cachedMatch != null) {
                return cachedMatch
            }
            val bootstrapRoute = bootstrapRouteProvider(routeProfile)
                ?.takeIf { it.matchesLogicalHost(normalizedHost) }
            if (bootstrapRoute != null) {
                cachedRoute = bootstrapRoute
                cachedAtMs = now
                routeStore.save(
                    PersistedWattToolkitWorkshopRoute(
                        route = bootstrapRoute,
                        cachedAtMs = now,
                    ),
                )
                workshopLogWarn(
                    "Experimental workshop direct access using built-in bootstrap Watt route profile=${routeProfile.name} hosts=${bootstrapRoute.logicalHosts.joinToString(";")} forward=${bootstrapRoute.forwardTargets.joinToString(";")}",
                )
                return bootstrapRoute
            }
            return null
        }
    }

    private fun restorePersistedRouteLocked(now: Long) {
        if (persistedRouteLoaded) {
            return
        }
        persistedRouteLoaded = true
        val persisted = routeStore.load() ?: return
        cachedRoute = persisted.route
        cachedAtMs = persisted.cachedAtMs
        workshopLogInfo(
            "Experimental workshop direct access restored persisted Watt route ageMs=${(now - persisted.cachedAtMs).coerceAtLeast(0L)} forward=${persisted.route.forwardTargets.joinToString(";")}",
        )
    }

    private fun fetchSupportedRouteWithRetries(): WattToolkitWorkshopRoute {
        var lastError: Throwable? = null
        repeat(ROUTE_FETCH_ATTEMPTS) { attempt ->
            try {
                return fetchSupportedRoute()
            } catch (error: Throwable) {
                lastError = error
                val isLastAttempt = attempt == ROUTE_FETCH_ATTEMPTS - 1
                if (!error.isRetryableWattRouteFetchFailure() || isLastAttempt) {
                    throw error
                }
                val retryDelayMs = ROUTE_FETCH_RETRY_DELAYS_MS.getOrElse(attempt) { 0L }
                workshopLogWarn(
                    "Experimental workshop direct access Watt route fetch attempt ${attempt + 1}/$ROUTE_FETCH_ATTEMPTS failed; retrying in ${retryDelayMs}ms: ${error::class.java.simpleName}:${error.message}",
                )
                sleepForRetry(retryDelayMs)
            }
        }
        throw lastError ?: IllegalStateException("Watt Toolkit route fetch failed without exception")
    }

    private fun sleepForRetry(delayMs: Long) {
        if (delayMs <= 0L) {
            return
        }
        try {
            sleepProvider(delayMs)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting to retry Watt route fetch", error)
        }
    }

    private fun fetchSupportedRoute(): WattToolkitWorkshopRoute {
        val request = Request.Builder()
            .url(projectGroupsUrl)
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Watt Toolkit route request failed: ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            val decoded = json.decodeFromString<WattAccelerateResponse>(payload)
            val matchedProject = decoded.groups.asSequence()
                .flatMap { flattenProjects(it.items).asSequence() }
                .mapNotNull { project ->
                    val logicalHosts = project.parseLogicalHosts().intersect(normalizedSupportedHosts)
                    logicalHosts.takeIf(Set<String>::isNotEmpty)?.let { project to it }
                }
                .firstOrNull()
                ?: error("Watt Toolkit route was not found for hosts=${normalizedSupportedHosts.joinToString(";")}")
            val (project, logicalHosts) = matchedProject
            if (project.proxyType != 0) {
                error("Unsupported Watt Toolkit route type for hosts=${logicalHosts.joinToString(";")}: ${project.proxyType}")
            }
            return WattToolkitWorkshopRoute(
                logicalHosts = logicalHosts,
                forwardTargets = project.forwardDomainNames.parseForwardTargets(),
                ignoreSslCertVerification = project.ignoreSslCertVerification,
            )
        }
    }

    private fun flattenProjects(items: List<WattAccelerateProject>): List<WattAccelerateProject> =
        buildList {
            items.forEach { item ->
                add(item)
                addAll(flattenProjects(item.items))
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val ROUTE_CACHE_TTL_MS = 30L * 60L * 1_000L
        const val ROUTE_FETCH_ATTEMPTS = 5
        val ROUTE_FETCH_RETRY_DELAYS_MS = longArrayOf(250L, 500L, 1_000L, 1_500L)
    }
}

internal data class WattToolkitWorkshopRoute(
    val logicalHosts: Set<String>,
    val forwardTargets: List<String>,
    val ignoreSslCertVerification: Boolean = false,
) {
    val forwardHosts: Set<String> =
        forwardTargets.mapNotNull { target ->
            runCatching {
                if (target.contains("://")) {
                    target.toHttpUrl().host.lowercase()
                } else {
                    target.lowercase()
                }
            }.getOrNull()
        }.toSet()

    fun buildForwardedUrl(originalUrl: HttpUrl): HttpUrl {
        val firstTarget = forwardTargets.firstOrNull()?.trim().orEmpty()
        if (firstTarget.isBlank()) {
            return originalUrl
        }
        return if (firstTarget.contains("://")) {
            val forwardedBase = firstTarget.toHttpUrl()
            forwardedBase.newBuilder()
                .encodedPath(originalUrl.encodedPath)
                .encodedQuery(originalUrl.encodedQuery)
                .build()
        } else {
            originalUrl.newBuilder()
                .host(firstTarget)
                .build()
        }
    }

    fun normalizeLogicalRequest(request: Request): Request {
        val normalizedUrl = normalizeLogicalUrl(
            url = request.url,
            fallbackLogicalHost = request.url.host,
        )
        if (normalizedUrl == request.url) {
            return request
        }
        return request.newBuilder()
            .url(normalizedUrl)
            .build()
    }

    fun normalizeLogicalUrl(
        url: HttpUrl,
        fallbackLogicalHost: String,
    ): HttpUrl {
        if (url.host.lowercase() !in forwardHosts) {
            return url
        }
        return url.newBuilder()
            .host(fallbackLogicalHost)
            .build()
    }

    fun matchesLogicalHost(host: String): Boolean =
        host.lowercase() in logicalHosts

    fun shouldBypassHostnameVerification(host: String): Boolean =
        ignoreSslCertVerification && host.lowercase() in forwardHosts
}

internal interface WattToolkitWorkshopRouteStore {
    fun load(): PersistedWattToolkitWorkshopRoute?

    fun save(route: PersistedWattToolkitWorkshopRoute)
}

internal object NoOpWattToolkitWorkshopRouteStore : WattToolkitWorkshopRouteStore {
    override fun load(): PersistedWattToolkitWorkshopRoute? = null

    override fun save(route: PersistedWattToolkitWorkshopRoute) = Unit
}

internal class FileBackedWattToolkitWorkshopRouteStore(
    private val file: File,
    private val fallbackLogicalHosts: Set<String> = emptySet(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : WattToolkitWorkshopRouteStore {
    override fun load(): PersistedWattToolkitWorkshopRoute? {
        return runCatching {
            val snapshot = json.decodeFromString<PersistedWattToolkitWorkshopRouteSnapshot>(file.readText())
            val logicalHosts = snapshot.logicalHosts
                .ifEmpty { fallbackLogicalHosts.toList() }
                .map(String::lowercase)
                .toSet()
            if (snapshot.forwardTargets.isEmpty() || logicalHosts.isEmpty()) {
                return null
            }
            PersistedWattToolkitWorkshopRoute(
                route = WattToolkitWorkshopRoute(
                    logicalHosts = logicalHosts,
                    forwardTargets = snapshot.forwardTargets,
                    ignoreSslCertVerification = snapshot.ignoreSslCertVerification,
                ),
                cachedAtMs = snapshot.cachedAtMs,
            )
        }.onFailure { error ->
            if (file.isFile) {
                workshopLogWarn("Experimental workshop direct access failed to load persisted Watt route: ${error.message}", error)
            }
        }.getOrNull()
    }

    override fun save(route: PersistedWattToolkitWorkshopRoute) {
        runCatching {
            file.parentFile?.mkdirs()
            val snapshot = PersistedWattToolkitWorkshopRouteSnapshot(
                cachedAtMs = route.cachedAtMs,
                logicalHosts = route.route.logicalHosts.sorted(),
                forwardTargets = route.route.forwardTargets,
                ignoreSslCertVerification = route.route.ignoreSslCertVerification,
            )
            val tempFile = File.createTempFile(file.name, ".tmp", file.parentFile ?: file.absoluteFile.parentFile)
            tempFile.writeText(json.encodeToString(snapshot))
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }.onFailure { error ->
            workshopLogWarn("Experimental workshop direct access failed to persist Watt route: ${error.message}", error)
        }
    }
}

internal data class PersistedWattToolkitWorkshopRoute(
    val route: WattToolkitWorkshopRoute,
    val cachedAtMs: Long,
)

internal fun defaultWattToolkitRouteClient(): OkHttpClient =
    OkHttpClient.Builder()
        .applyDefaultHttpTimeouts()
        .applyAppNetworkLogging("watt-route")
        .proxy(Proxy.NO_PROXY)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .build()

private fun String.parseForwardTargets(): List<String> =
    split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)

private fun List<Cookie>.toCookieHeader(): String? =
    takeIf(List<Cookie>::isNotEmpty)
        ?.joinToString(separator = "; ") { cookie -> "${cookie.name}=${cookie.value}" }

private fun CookieJar.saveForwardedResponse(
    originalUrl: HttpUrl,
    headers: Headers,
) {
    val cookies = Cookie.parseAll(originalUrl, headers)
    if (cookies.isNotEmpty()) {
        saveFromResponse(originalUrl, cookies)
    }
}

private fun Throwable.isRetryableWattRouteFetchFailure(): Boolean =
    this is IOException || cause?.isRetryableWattRouteFetchFailure() == true

private fun Response.redirectTarget(
    logicalUrl: HttpUrl,
    route: WattToolkitWorkshopRoute,
): HttpUrl? {
    if (code !in REDIRECT_RESPONSE_CODES) {
        return null
    }
    val location = header("Location")?.trim().orEmpty()
    if (location.isBlank()) {
        return null
    }
    return logicalUrl.resolve(location)?.let { resolvedUrl ->
        route.normalizeLogicalUrl(
            url = resolvedUrl,
            fallbackLogicalHost = logicalUrl.host,
        )
    }
}

internal const val STEAM_COMMUNITY_HOST = "steamcommunity.com"
private const val WATT_ACCELERATOR_PROJECTGROUPS_URL = "https://api.steampp.net/accelerator/projectgroups"
private const val MAX_FOLLOW_UPS = 10
private const val HTTP_METHOD_GET = "GET"
private const val HTTP_METHOD_HEAD = "HEAD"
private const val HTTP_TEMP_REDIRECT = 307
private const val HTTP_PERM_REDIRECT = 308
private val REDIRECT_RESPONSE_CODES = setOf(300, 301, 302, 303, HTTP_TEMP_REDIRECT, HTTP_PERM_REDIRECT)

@Serializable
private data class WattAccelerateResponse(
    @SerialName("🦓")
    val groups: List<WattAccelerateGroup> = emptyList(),
)

@Serializable
private data class WattAccelerateGroup(
    @SerialName("Items")
    val items: List<WattAccelerateProject> = emptyList(),
)

@Serializable
private data class PersistedWattToolkitWorkshopRouteSnapshot(
    val cachedAtMs: Long = 0L,
    val logicalHosts: List<String> = emptyList(),
    val forwardTargets: List<String> = emptyList(),
    val ignoreSslCertVerification: Boolean = false,
)

@Serializable
private data class WattAccelerateProject(
    @SerialName("MatchDomainNames")
    val matchDomainNames: String = "",
    @SerialName("ForwardDomainNames")
    val forwardDomainNames: String = "",
    @SerialName("ProxyType")
    val proxyType: Int = -1,
    @SerialName("IgnoreSSLCertVerification")
    val ignoreSslCertVerification: Boolean = false,
    @SerialName("Items")
    val items: List<WattAccelerateProject> = emptyList(),
)

private fun WattAccelerateProject.parseLogicalHosts(): Set<String> =
    matchDomainNames
        .split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { match ->
            when {
                "://" in match -> runCatching { match.toHttpUrl().host.lowercase() }.getOrNull()
                '*' in match -> null
                else -> match.lowercase()
            }
        }
        .toSet()
