package top.apricityx.workshop

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SteamWebSessionCookieJar(
    private val projectedCookiesProvider: (HttpUrl) -> List<Cookie> = { emptyList() },
    private val sessionScopeProvider: (() -> String?)? = null,
) : CookieJar {
    private val lock = Any()
    private val cookies = linkedMapOf<StoredCookieKey, Cookie>()
    private var isScopeInitialized = false
    private var currentScope: String? = null

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        if (!url.host.isSteamDomain()) {
            return
        }
        syncScope()
        val now = System.currentTimeMillis()
        synchronized(lock) {
            cookies.forEach { cookie ->
                val key = cookie.storageKey()
                if (cookie.expiresAt <= now) {
                    this.cookies.remove(key)
                } else {
                    this.cookies[key] = cookie
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.isSteamDomain()) {
            return emptyList()
        }
        syncScope()
        val persistedCookies = synchronized(lock) {
            val now = System.currentTimeMillis()
            val expiredKeys = mutableListOf<StoredCookieKey>()
            val matchingCookies = mutableListOf<Cookie>()
            cookies.forEach { (key, cookie) ->
                when {
                    cookie.expiresAt <= now -> expiredKeys += key
                    cookie.matches(url) -> matchingCookies += cookie
                }
            }
            expiredKeys.forEach(cookies::remove)
            matchingCookies
        }
        return mergeCookies(
            persistedCookies = persistedCookies,
            projectedCookies = projectedCookiesProvider(url),
        )
    }

    private fun syncScope() {
        val provider = sessionScopeProvider ?: return
        val nextScope = provider()
        synchronized(lock) {
            if (!isScopeInitialized || currentScope != nextScope) {
                cookies.clear()
                currentScope = nextScope
                isScopeInitialized = true
            }
        }
    }

    private fun mergeCookies(
        persistedCookies: List<Cookie>,
        projectedCookies: List<Cookie>,
    ): List<Cookie> {
        if (persistedCookies.isEmpty()) {
            return projectedCookies
        }
        if (projectedCookies.isEmpty()) {
            return persistedCookies
        }
        val merged = linkedMapOf<StoredCookieKey, Cookie>()
        persistedCookies.forEach { cookie ->
            merged[cookie.storageKey()] = cookie
        }
        projectedCookies.forEach { cookie ->
            merged[cookie.storageKey()] = cookie
        }
        return merged.values.toList()
    }
}

private data class StoredCookieKey(
    val name: String,
    val domain: String,
    val path: String,
)

private fun Cookie.storageKey(): StoredCookieKey =
    StoredCookieKey(
        name = name.lowercase(),
        domain = domain,
        path = path,
    )
