package top.apricityx.workshop
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Proxy
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
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
    private val fallbackNoticeSink: SteamDirectAccessFallbackNoticeSink = NoOpSteamDirectAccessFallbackNoticeSink,
    private val maxRedirects: Int = MAX_FOLLOW_UPS,
    private val forwardDns: WattToolkitForwardDns? = null,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url
        if (!enabledProvider() || !routeResolver.supports(originalUrl.host)) {
            return chain.proceed(request)
        }

        val route = routeResolver.resolveRouteForHost(originalUrl.host)
            ?: run {
                workshopLogWarn(
                    "Experimental workshop direct access has no Watt route for host=${originalUrl.host}; falling back to the original Steam route.",
                )
                notifyFallbackIfWorkshopHost(originalUrl.host)
                return chain.proceed(request)
            }
        return try {
            executeDirectAccessRequestWithRouteRefresh(
                initialLogicalRequest = request,
                originalLogicalHost = originalUrl.host,
                route = route,
            )
        } catch (error: IOException) {
            workshopLogWarn(
                "Experimental workshop direct access exhausted Watt route refresh for host=${originalUrl.host}; accelerated request failed: ${error::class.java.simpleName}:${error.message}",
                error,
            )
            workshopLogWarn(
                "Experimental workshop direct access falling back to the original Steam route for host=${originalUrl.host}.",
            )
            notifyFallbackIfWorkshopHost(originalUrl.host)
            return chain.proceed(request)
        }
    }

    private fun notifyFallbackIfWorkshopHost(host: String) {
        if (host.equals(STEAM_COMMUNITY_HOST, ignoreCase = true) ||
            host.equals("www.steamcommunity.com", ignoreCase = true)
        ) {
            fallbackNoticeSink.onFallbackToOriginalSteamRoute()
        }
    }

    private fun executeDirectAccessRequestWithRouteRefresh(
        initialLogicalRequest: Request,
        originalLogicalHost: String,
        route: WattToolkitWorkshopRoute,
    ): Response {
        return try {
            executeDirectAccessRequest(initialLogicalRequest, route)
        } catch (error: IOException) {
            if (!error.isRetryableDirectAccessRouteRefreshFailure()) {
                throw error
            }
            workshopLogWarn(
                "Experimental workshop direct access failed for host=$originalLogicalHost; clearing cached Watt route and fetching a fresh route before retrying: ${error::class.java.simpleName}:${error.message}",
                error,
            )
            val refreshedRoute = routeResolver.refreshRouteForHost(originalLogicalHost)
                ?: throw error
            workshopLogInfo(
                "Experimental workshop direct access retrying host=$originalLogicalHost with refreshed Watt route forward=${refreshedRoute.forwardTargets.joinToString(";")}",
            )
            try {
                executeDirectAccessRequest(initialLogicalRequest, refreshedRoute)
            } catch (refreshedError: IOException) {
                refreshedError.addSuppressed(error)
                throw refreshedError
            }
        }
    }

    private fun executeDirectAccessRequest(
        initialLogicalRequest: Request,
        route: WattToolkitWorkshopRoute,
    ): Response {
        var logicalRequest = route.normalizeLogicalRequest(initialLogicalRequest)
        var followUpCount = 0
        while (true) {
            val response = executeWithForwardTargetFallback(logicalRequest, route)
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

    private fun executeWithForwardTargetFallback(
        logicalRequest: Request,
        route: WattToolkitWorkshopRoute,
    ): Response {
        var lastError: IOException? = null
        route.forwardTargetCandidates().forEach { candidateRoute ->
            try {
                val response = directCallFactory
                    .newCall(buildNetworkRequest(logicalRequest, candidateRoute))
                    .execute()
                if (response.isRetryableForwardedFailure(logicalRequest, candidateRoute)) {
                    val failure = ForwardedHttpFailure(
                        responseCode = response.code,
                        requestUrl = response.request.url,
                    )
                    response.close()
                    lastError = failure
                    return@forEach
                }
                return response
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("No Steam acceleration route candidate was available")
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
        if (shouldForward) {
            forwardDns?.register(route)
        }
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

internal class ExperimentalGithubDirectAccessInterceptor(
    private val enabledProvider: () -> Boolean,
    private val routeResolvers: List<WattToolkitWorkshopRouteResolver>,
    private val directCallFactory: Call.Factory,
    private val maxRedirects: Int = MAX_FOLLOW_UPS,
    private val forwardDns: WattToolkitForwardDns? = null,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!enabledProvider() || routeResolvers.none { resolver -> resolver.supports(request.url.host) }) {
            return chain.proceed(request)
        }
        return executeDirectAccessRequest(request)
    }

    private fun executeDirectAccessRequest(initialLogicalRequest: Request): Response {
        var logicalRequest = initialLogicalRequest
        var followUpCount = 0
        while (true) {
            val resolver = routeResolvers.firstOrNull { candidate -> candidate.supports(logicalRequest.url.host) }
            val route = resolver?.resolveRouteForHost(logicalRequest.url.host)
            if (resolver != null && route == null) {
                workshopLogWarn(
                    "Experimental GitHub direct access has no Watt route; falling back to original host=${logicalRequest.url.host}.",
                )
            }
            var effectiveRoute = route
            val response = try {
                executeWithForwardTargetFallback(logicalRequest, route)
            } catch (error: IOException) {
                val refreshedRoute = resolver?.refreshRouteForHost(logicalRequest.url.host) ?: throw error
                effectiveRoute = refreshedRoute
                try {
                    executeWithForwardTargetFallback(logicalRequest, refreshedRoute)
                } catch (refreshedError: IOException) {
                    refreshedError.addSuppressed(error)
                    throw refreshedError
                }
            }
            val redirectTarget = response.redirectTarget(logicalRequest.url, effectiveRoute)
            if (redirectTarget == null) {
                return response.newBuilder()
                    .request(logicalRequest)
                    .build()
            }
            if (followUpCount >= maxRedirects) {
                response.close()
                throw ProtocolException("Too many experimental GitHub direct-access redirects: $maxRedirects")
            }
            workshopLogInfo(
                "Experimental GitHub direct access following redirect ${logicalRequest.url.host}${logicalRequest.url.encodedPath} -> ${redirectTarget.host}${redirectTarget.encodedPath}",
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

    private fun executeWithForwardTargetFallback(
        logicalRequest: Request,
        route: WattToolkitWorkshopRoute?,
    ): Response {
        val candidateRoutes = route?.forwardTargetCandidates() ?: listOf(null)
        var lastError: IOException? = null
        candidateRoutes.forEach { candidateRoute ->
            try {
                return directCallFactory.newCall(buildNetworkRequest(logicalRequest, candidateRoute)).execute()
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("No GitHub acceleration route candidate was available")
    }

    private fun buildNetworkRequest(
        logicalRequest: Request,
        route: WattToolkitWorkshopRoute?,
    ): Request {
        if (route == null) {
            return logicalRequest
        }
        val logicalUrl = route.normalizeLogicalUrl(
            url = logicalRequest.url,
            fallbackLogicalHost = logicalRequest.url.host,
        )
        val shouldForward = route.matchesLogicalHost(logicalUrl.host)
        val networkUrl = if (shouldForward) route.buildForwardedUrl(logicalUrl) else logicalUrl
        if (shouldForward) {
            forwardDns?.register(route)
        }
        if (shouldForward) {
            workshopLogInfo(
                "Experimental GitHub direct access rewriting ${logicalUrl.host}${logicalUrl.encodedPath} -> ${networkUrl.host}${networkUrl.encodedPath} ignoreSsl=${route.ignoreSslCertVerification}",
            )
        }
        return logicalRequest.newBuilder()
            .url(networkUrl)
            .apply {
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
    private val forwardTargetProbe: (String) -> WattToolkitForwardTargetProbe = ::probeWattToolkitForwardTarget,
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
            if (cached != null &&
                cached.matchesLogicalHost(normalizedHost) &&
                !cached.isKnownBrokenLegacyRoute() &&
                now - cachedAtMs < ROUTE_CACHE_TTL_MS
            ) {
                return cached
            }
        }

        // The route endpoint is commonly intercepted by campus gateways. Once
        // that is observed, avoid repeating the same doomed request for every
        // Steam host/profile and keep using the local accelerated bootstrap.
        if (WattToolkitRouteFetchState.isCaptivePortalBlocked()) {
            synchronized(lock) {
                return routeWithoutRemoteFetchLocked(normalizedHost, now)
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
                persistResolvedRouteLocked(fetched, now)
                workshopLogInfo(
                    "Experimental workshop direct access resolved Watt route forward=${fetched.forwardTargets.joinToString(";")} ignoreSsl=${fetched.ignoreSslCertVerification}",
                )
                return fetched
            }
            if (cachedRoute != null && !cachedRoute!!.isKnownBrokenLegacyRoute()) {
                workshopLogInfo(
                    "Experimental workshop direct access using cached Watt route ageMs=${(now - cachedAtMs).coerceAtLeast(0L)} after fetch failure.",
                )
            }
            val cachedMatch = cachedRoute
                ?.takeIf { it.matchesLogicalHost(normalizedHost) }
                ?.takeUnless(WattToolkitWorkshopRoute::isKnownBrokenLegacyRoute)
                ?.takeIf { now - cachedAtMs < ROUTE_CACHE_TTL_MS }
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

    fun refreshRouteForHost(host: String): WattToolkitWorkshopRoute? {
        val normalizedHost = host.lowercase()
        if (normalizedHost !in normalizedSupportedHosts) {
            return null
        }
        if (WattToolkitRouteFetchState.isCaptivePortalBlocked()) {
            synchronized(lock) {
                return routeWithoutRemoteFetchLocked(
                    normalizedHost = normalizedHost,
                    now = nowProvider(),
                    preferBootstrap = true,
                )
            }
        }
        synchronized(lock) {
            clearCachedRouteLocked()
        }
        val now = nowProvider()
        val fetched = runCatching(::fetchSupportedRouteWithRetries)
            .onFailure { error ->
                workshopLogWarn(
                    "Experimental workshop direct access failed to refresh Watt route for host=$normalizedHost: ${error.message}",
                    error,
                )
            }
            .getOrNull()
            ?.takeIf { route -> route.matchesLogicalHost(normalizedHost) }
        synchronized(lock) {
            if (fetched != null) {
                persistResolvedRouteLocked(fetched, now)
                workshopLogInfo(
                    "Experimental workshop direct access refreshed Watt route forward=${fetched.forwardTargets.joinToString(";")} ignoreSsl=${fetched.ignoreSslCertVerification}",
                )
                return fetched
            }
            if (WattToolkitRouteFetchState.isCaptivePortalBlocked()) {
                return routeWithoutRemoteFetchLocked(
                    normalizedHost = normalizedHost,
                    now = now,
                    preferBootstrap = true,
                )
            }
            return null
        }
    }

    private fun routeWithoutRemoteFetchLocked(
        normalizedHost: String,
        now: Long,
        preferBootstrap: Boolean = false,
    ): WattToolkitWorkshopRoute? {
        val cachedMatch = cachedRoute
            ?.takeIf { it.matchesLogicalHost(normalizedHost) }
            ?.takeUnless(WattToolkitWorkshopRoute::isKnownBrokenLegacyRoute)
        val bootstrapRoute = bootstrapRouteProvider(routeProfile)
            ?.takeIf { it.matchesLogicalHost(normalizedHost) }

        val selected = if (preferBootstrap) bootstrapRoute ?: cachedMatch else cachedMatch ?: bootstrapRoute
        if (selected == null) {
            workshopLogWarn(
                "Experimental workshop direct access has no accelerated route while Watt configuration is blocked for host=$normalizedHost.",
            )
            return null
        }
        if (selected !== cachedRoute || cachedAtMs != now) {
            cachedRoute = selected
            cachedAtMs = now
            routeStore.save(
                PersistedWattToolkitWorkshopRoute(
                    route = selected,
                    cachedAtMs = now,
                ),
            )
        }
        workshopLogWarn(
            "Experimental workshop direct access using accelerated route without Watt fetch profile=${routeProfile.name} forward=${selected.forwardTargets.joinToString(";")}",
        )
        return selected
    }

    private fun restorePersistedRouteLocked(now: Long) {
        if (persistedRouteLoaded) {
            return
        }
        persistedRouteLoaded = true
        val persisted = routeStore.load() ?: return
        if (persisted.route.isKnownBrokenLegacyRoute()) {
            workshopLogWarn(
                "Experimental workshop direct access discarded known broken persisted Watt route forward=${persisted.route.forwardTargets.joinToString(";")}",
            )
            routeStore.clear()
            return
        }
        cachedRoute = persisted.route
        cachedAtMs = persisted.cachedAtMs
        workshopLogInfo(
            "Experimental workshop direct access restored persisted Watt route ageMs=${(now - persisted.cachedAtMs).coerceAtLeast(0L)} forward=${persisted.route.forwardTargets.joinToString(";")}",
        )
    }

    private fun persistResolvedRouteLocked(
        route: WattToolkitWorkshopRoute,
        cachedAtMs: Long,
    ) {
        cachedRoute = route
        this.cachedAtMs = cachedAtMs
        routeStore.save(
            PersistedWattToolkitWorkshopRoute(
                route = route,
                cachedAtMs = cachedAtMs,
            ),
        )
    }

    private fun clearCachedRouteLocked() {
        cachedRoute = null
        cachedAtMs = 0L
        persistedRouteLoaded = true
        routeStore.clear()
    }

    private fun fetchSupportedRouteWithRetries(): WattToolkitWorkshopRoute {
        var lastError: Throwable? = null
        repeat(ROUTE_FETCH_ATTEMPTS) { attempt ->
            try {
                return fetchSupportedRoute(projectGroupsUrl)
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

    private fun fetchSupportedRoute(endpoint: HttpUrl): WattToolkitWorkshopRoute {
        val request = Request.Builder()
            .url(endpoint)
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("User-Agent", "WorkshopOnAndroid/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val responseBody = response.body?.string().orEmpty()
                val normalizedBody = responseBody.replace(Regex("\\s+"), " " )
                val captivePortal = normalizedBody.contains("校园网") ||
                    normalizedBody.contains("认证页面") ||
                    normalizedBody.contains("认证登录") ||
                    normalizedBody.contains("captive portal", ignoreCase = true)
                if (captivePortal) {
                    WattToolkitRouteFetchState.markCaptivePortalBlocked()
                }
                val bodyPreview = normalizedBody.take(160)
                val detail = buildString {
                    append("Watt Toolkit route request failed: ${response.code}")
                    response.header("Server")?.let { append(" server=$it") }
                    if (captivePortal) {
                        append(" captive-portal")
                    }
                    if (bodyPreview.isNotBlank()) {
                        append(" body=").append(bodyPreview)
                    }
                }
                error(detail)
            }
            val payload = response.body?.string().orEmpty()
            val decoded = json.decodeFromString<WattAccelerateResponse>(payload)
            val matchedProject = decoded.groups.asSequence()
                .flatMap { flattenProjects(it.items).asSequence() }
                .filter(WattAccelerateProject::checked)
                .mapNotNull { project ->
                    val logicalHosts = (
                        normalizedSupportedHosts.filterTo(LinkedHashSet()) { supportedHost ->
                            project.parseLogicalHosts().any { configuredHost ->
                                wattHostPatternMatches(configuredHost, supportedHost)
                            }
                        } +
                            routeProfile.additionalLogicalHosts.map(String::lowercase)
                        ).intersect(normalizedSupportedHosts)
                    logicalHosts.takeIf(Set<String>::isNotEmpty)?.let { project to it }
                }
                .firstOrNull()
                ?: error("Watt Toolkit route was not found for hosts=${normalizedSupportedHosts.joinToString(";")}")
            val (project, logicalHosts) = matchedProject
            if (project.proxyType != 0) {
                error("Unsupported Watt Toolkit route type for hosts=${logicalHosts.joinToString(";")}: ${project.proxyType}")
            }
            WattToolkitRouteFetchState.markConfigurationAvailable()
            return WattToolkitWorkshopRoute(
                logicalHosts = logicalHosts,
                forwardTargets = rankForwardTargets(project.forwardDomainNames.parseForwardTargets()),
                ignoreSslCertVerification = project.ignoreSslCertVerification,
                fakeServerName = project.fakeServerName.trim(),
            )
        }
    }

    private fun rankForwardTargets(targets: List<String>): List<String> {
        val distinctTargets = targets.distinct()
        if (distinctTargets.size < 2) {
            return distinctTargets
        }
        return distinctTargets
            .mapIndexed { index, target ->
                RankedWattForwardTarget(
                    target = target,
                    originalIndex = index,
                    probe = runCatching { forwardTargetProbe(target) }
                        .getOrDefault(WattToolkitForwardTargetProbe.failed()),
                )
            }
            .sortedWith(
                compareByDescending<RankedWattForwardTarget> { it.probe.successRate }
                    .thenBy { it.probe.latencyMs ?: Long.MAX_VALUE }
                    .thenBy { it.originalIndex },
            )
            .map(RankedWattForwardTarget::target)
    }

    private data class RankedWattForwardTarget(
        val target: String,
        val originalIndex: Int,
        val probe: WattToolkitForwardTargetProbe,
    )

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

/** Process-wide guard for a Watt endpoint intercepted by a captive portal. */
internal object WattToolkitRouteFetchState {
    private const val CAPTIVE_PORTAL_COOLDOWN_MS = 5L * 60L * 1_000L

    @Volatile
    private var captivePortalBlockedUntilMs: Long = 0L

    fun isCaptivePortalBlocked(now: Long = System.currentTimeMillis()): Boolean =
        captivePortalBlockedUntilMs > now

    fun markCaptivePortalBlocked(now: Long = System.currentTimeMillis()) {
        captivePortalBlockedUntilMs = now + CAPTIVE_PORTAL_COOLDOWN_MS
        workshopLogWarn(
            "Watt Toolkit route configuration is blocked by a captive portal; suppressing further config fetches for ${CAPTIVE_PORTAL_COOLDOWN_MS / 1_000L}s.",
        )
    }

    fun markConfigurationAvailable() {
        captivePortalBlockedUntilMs = 0L
    }

    internal fun resetForTesting() {
        captivePortalBlockedUntilMs = 0L
    }
}

internal data class WattToolkitWorkshopRoute(
    val logicalHosts: Set<String>,
    val forwardTargets: List<String>,
    val ignoreSslCertVerification: Boolean = false,
    val fakeServerName: String = "",
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
    val networkHosts: Set<String> = buildSet {
        addAll(forwardHosts)
        normalizedFakeServerName()?.let(::add)
        if (usesOriginFakeServerName()) {
            addAll(logicalHosts)
        }
    }

    fun buildForwardedUrl(originalUrl: HttpUrl): HttpUrl {
        val firstTarget = forwardTargets.firstOrNull()?.trim().orEmpty()
        if (firstTarget.isBlank()) {
            return originalUrl
        }
        return if (firstTarget.contains("://")) {
            val forwardedBase = firstTarget.toHttpUrl()
            val networkHost = networkHostFor(originalUrl.host) ?: forwardedBase.host
            forwardedBase.newBuilder()
                .encodedPath(originalUrl.encodedPath)
                .host(networkHost)
                .encodedQuery(originalUrl.encodedQuery)
                .build()
        } else {
            originalUrl.newBuilder()
                .host(networkHostFor(originalUrl.host) ?: firstTarget)
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
        if (url.host.lowercase() !in networkHosts) {
            return url
        }
        return url.newBuilder()
            .host(fallbackLogicalHost)
            .build()
    }

    fun matchesLogicalHost(host: String): Boolean =
        host.lowercase() in logicalHosts

    fun shouldBypassHostnameVerification(host: String): Boolean =
        ignoreSslCertVerification && host.lowercase() in networkHosts

    fun isKnownBrokenLegacyRoute(): Boolean =
        forwardHosts.any { host ->
            // The community mirror was retired and now answers Steam paths with
            // a generic 404. The store mirror remains valid when paired with
            // the current fake SNI supplied by Watt (steamstore-a.*).
            host == "steamcommunity.rmbgame.net" ||
                (host == "steamstore.rmbgame.net" && fakeServerName.isBlank())
        }

    fun networkHostFor(logicalHost: String): String? {
        val fakeHost = normalizedFakeServerName()
        return when {
            fakeHost != null -> fakeHost
            usesOriginFakeServerName() -> logicalHost.lowercase()
            else -> null
        }
    }

    fun usesOriginFakeServerName(): Boolean = fakeServerName.trim() in setOf("{origin}", "@domain")

    fun forwardTargetCandidates(): List<WattToolkitWorkshopRoute> {
        if (forwardTargets.size < 2) {
            return listOf(this)
        }
        return forwardTargets.indices.map { index ->
            copy(forwardTargets = forwardTargets.drop(index))
        }
    }

    private fun normalizedFakeServerName(): String? = fakeServerName
        .trim()
        .takeIf { it.isNotEmpty() && it != "{origin}" && it != "@domain" }
        ?.lowercase()
}

internal class WattToolkitForwardDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    private val forwardHostsByNetworkHost = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun register(route: WattToolkitWorkshopRoute) {
        val targetHost = route.forwardHosts.firstOrNull() ?: return
        val fakeHost = route.fakeServerName
            .trim()
            .takeIf { it.isNotEmpty() && it != "{origin}" && it != "@domain" }
            ?.lowercase()
        if (fakeHost != null) {
            forwardHostsByNetworkHost[fakeHost] = targetHost
        } else if (route.usesOriginFakeServerName()) {
            route.logicalHosts.forEach { logicalHost ->
                forwardHostsByNetworkHost[logicalHost.lowercase()] = targetHost
            }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val targetHost = forwardHostsByNetworkHost[hostname.lowercase()] ?: hostname
        return delegate.lookup(targetHost)
    }
}

/** A forwarded endpoint answered with a gateway/mirror failure rather than Steam content. */
private class ForwardedHttpFailure(
    val responseCode: Int,
    val requestUrl: HttpUrl,
) : IOException("Forwarded Steam request failed with HTTP $responseCode at $requestUrl")

internal interface WattToolkitWorkshopRouteStore {
    fun load(): PersistedWattToolkitWorkshopRoute?

    fun save(route: PersistedWattToolkitWorkshopRoute)

    fun clear()
}

internal object NoOpWattToolkitWorkshopRouteStore : WattToolkitWorkshopRouteStore {
    override fun load(): PersistedWattToolkitWorkshopRoute? = null

    override fun save(route: PersistedWattToolkitWorkshopRoute) = Unit

    override fun clear() = Unit
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
                    fakeServerName = snapshot.fakeServerName.trim(),
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
                fakeServerName = route.route.fakeServerName,
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

    override fun clear() {
        runCatching {
            if (file.exists() && !file.delete()) {
                error("Failed to delete ${file.absolutePath}")
            }
        }.onFailure { error ->
            workshopLogWarn("Experimental workshop direct access failed to clear persisted Watt route: ${error.message}", error)
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
        // api.steampp.net currently serves an expired intermediate certificate.
        // This client only talks to the fixed Watt route endpoint; forwarding
        // clients keep the same trust policy. Some networks also replace the
        // endpoint certificate with their own gateway certificate, so the
        // hostname exception is scoped to this fixed configuration host only.
        .trustWattToolkitForwardCertificates()
        .hostnameVerifier(WattToolkitRouteHostnameVerifier)
        .build()

internal fun OkHttpClient.Builder.trustWattToolkitForwardCertificates(): OkHttpClient.Builder = apply {
    val trustManager = WattToolkitForwardTrustManager
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), null)
    }
    sslSocketFactory(sslContext.socketFactory, trustManager)
}

private fun String.parseForwardTargets(): List<String> =
    split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)

internal data class WattToolkitForwardTargetProbe(
    val successes: Int,
    val attempts: Int,
    val latencyMs: Long?,
) {
    val successRate: Double
        get() = if (attempts <= 0) 0.0 else successes.toDouble() / attempts.toDouble()

    companion object {
        fun failed(attempts: Int = FORWARD_TARGET_PROBE_ATTEMPTS): WattToolkitForwardTargetProbe =
            WattToolkitForwardTargetProbe(successes = 0, attempts = attempts, latencyMs = null)
    }
}

private fun probeWattToolkitForwardTarget(target: String): WattToolkitForwardTargetProbe {
    val url = runCatching { target.toHttpUrl() }.getOrNull()
        ?: runCatching { "https://$target".toHttpUrl() }.getOrNull()
        ?: return WattToolkitForwardTargetProbe.failed()
    var successes = 0
    val latencies = ArrayList<Long>(FORWARD_TARGET_PROBE_ATTEMPTS)
    repeat(FORWARD_TARGET_PROBE_ATTEMPTS) {
        val startedAt = System.nanoTime()
        try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(url.host, url.port),
                    FORWARD_TARGET_PROBE_TIMEOUT_MS.toInt(),
                )
            }
            successes++
            latencies += ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: IOException) {
            // Probe every sample so transient loss lowers the success-rate score.
        }
    }
    return WattToolkitForwardTargetProbe(
        successes = successes,
        attempts = FORWARD_TARGET_PROBE_ATTEMPTS,
        latencyMs = latencies.takeIf(List<Long>::isNotEmpty)?.average()?.toLong(),
    )
}

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

private fun Throwable.isRetryableDirectAccessRouteRefreshFailure(): Boolean =
    this is IOException || cause?.isRetryableDirectAccessRouteRefreshFailure() == true

private fun Response.isRetryableForwardedFailure(
    logicalRequest: Request,
    route: WattToolkitWorkshopRoute,
): Boolean {
    if (!route.matchesLogicalHost(logicalRequest.url.host)) {
        return false
    }
    // A 403 from a forwarded endpoint means that the relay rejected the
    // logical Steam host. It is not an access decision for the requested
    // workshop item, so refresh the route and then fall back if needed.
    return code == 403 || code == 404 || code == 421 || code == 502 || code == 503 || code == 504 ||
        code in 521..525
}

private fun Response.redirectTarget(
    logicalUrl: HttpUrl,
    route: WattToolkitWorkshopRoute?,
): HttpUrl? {
    if (code !in REDIRECT_RESPONSE_CODES) {
        return null
    }
    val location = header("Location")?.trim().orEmpty()
    if (location.isBlank()) {
        return null
    }
    return logicalUrl.resolve(location)?.let { resolvedUrl ->
        route?.normalizeLogicalUrl(
            url = resolvedUrl,
            fallbackLogicalHost = logicalUrl.host,
        ) ?: resolvedUrl
    }
}

internal const val STEAM_COMMUNITY_HOST = "steamcommunity.com"
private const val WATT_ACCELERATOR_HOST = "api.steampp.net"
private const val WATT_ACCELERATOR_PROJECTGROUPS_URL = "https://$WATT_ACCELERATOR_HOST/accelerator/projectgroups"
private const val MAX_FOLLOW_UPS = 10
private const val FORWARD_TARGET_PROBE_ATTEMPTS = 3
private const val FORWARD_TARGET_PROBE_TIMEOUT_MS = 1_200L
private const val HTTP_METHOD_GET = "GET"
private const val HTTP_METHOD_HEAD = "HEAD"
private const val HTTP_TEMP_REDIRECT = 307
private const val HTTP_PERM_REDIRECT = 308
private val REDIRECT_RESPONSE_CODES = setOf(300, 301, 302, 303, HTTP_TEMP_REDIRECT, HTTP_PERM_REDIRECT)

private object WattToolkitForwardTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private object WattToolkitRouteHostnameVerifier : HostnameVerifier {
    private val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    override fun verify(
        hostname: String,
        session: SSLSession,
    ): Boolean =
        hostname.equals(WATT_ACCELERATOR_HOST, ignoreCase = true) ||
            defaultVerifier.verify(hostname, session)
}

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
    val fakeServerName: String = "",
)

@Serializable
private data class WattAccelerateProject(
    @SerialName("MatchDomainNames")
    val matchDomainNames: String = "",
    @SerialName("ListenDomainNames")
    val listenDomainNames: String = "",
    @SerialName("ForwardDomainNames")
    val forwardDomainNames: String = "",
    @SerialName("ProxyType")
    val proxyType: Int = -1,
    @SerialName("IgnoreSSLCertVerification")
    val ignoreSslCertVerification: Boolean = false,
    @SerialName("FakeServerName")
    val fakeServerName: String = "",
    @SerialName("Checked")
    val checked: Boolean = true,
    @SerialName("Items")
    val items: List<WattAccelerateProject> = emptyList(),
)

private fun WattAccelerateProject.parseLogicalHosts(): Set<String> =
    parseHosts(matchDomainNames, listenDomainNames)

private fun parseHosts(
    vararg hostGroups: String,
): Set<String> =
    hostGroups.asSequence()
        .flatMap { hosts -> hosts.split(';').asSequence() }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { match ->
            when {
                "://" in match -> runCatching { match.toHttpUrl().host.lowercase() }.getOrNull()
                match == "*" || match.count { it == '*' } > 1 -> null
                '*' in match && !match.startsWith("*.") -> null
                else -> match.lowercase()
            }
        }
        .toSet()

private fun wattHostPatternMatches(
    pattern: String,
    host: String,
): Boolean {
    val normalizedPattern = pattern.lowercase()
    val normalizedHost = host.lowercase()
    if (normalizedPattern == normalizedHost) {
        return true
    }
    val wildcardSuffix = normalizedPattern.removePrefix("*.")
    return normalizedPattern.startsWith("*.") &&
        (normalizedHost == wildcardSuffix || normalizedHost.endsWith(".$wildcardSuffix"))
}
