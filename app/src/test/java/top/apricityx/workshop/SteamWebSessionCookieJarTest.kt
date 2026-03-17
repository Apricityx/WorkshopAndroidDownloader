package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class SteamWebSessionCookieJarTest {
    @Test
    fun `loadForRequest returns persisted steam cookies`() {
        val jar = SteamWebSessionCookieJar()
        val url = "https://steamcommunity.com/workshop/".toHttpUrl()
        val sessionCookie = steamCookie(
            url = url,
            name = "sessionid",
            value = "abc123",
        )

        jar.saveFromResponse(url, listOf(sessionCookie))

        assertThat(jar.loadForRequest(url))
            .containsExactly(sessionCookie)
    }

    @Test
    fun `loadForRequest merges projected cookies over persisted cookies with same name`() {
        val url = "https://steamcommunity.com/workshop/".toHttpUrl()
        val persistedCookie = steamCookie(
            url = url,
            name = "steamLoginSecure",
            value = "stale",
        )
        val projectedCookie = steamCookie(
            url = url,
            name = "steamLoginSecure",
            value = "fresh",
        )
        val sessionCookie = steamCookie(
            url = url,
            name = "sessionid",
            value = "abc123",
        )
        val jar = SteamWebSessionCookieJar(
            projectedCookiesProvider = { listOf(projectedCookie) },
        )

        jar.saveFromResponse(url, listOf(persistedCookie, sessionCookie))

        assertThat(jar.loadForRequest(url))
            .containsExactly(sessionCookie, projectedCookie)
    }

    @Test
    fun `loadForRequest preserves same name cookies with different paths`() {
        val url = "https://steamcommunity.com/workshop/".toHttpUrl()
        val persistedRootCookie = steamCookie(
            url = url,
            name = "steamLoginSecure",
            value = "stale-root",
        )
        val persistedWorkshopCookie = steamCookie(
            url = url,
            name = "steamLoginSecure",
            value = "scoped-workshop",
            path = "/workshop/",
        )
        val projectedRootCookie = steamCookie(
            url = url,
            name = "steamLoginSecure",
            value = "fresh-root",
        )
        val jar = SteamWebSessionCookieJar(
            projectedCookiesProvider = { listOf(projectedRootCookie) },
        )

        jar.saveFromResponse(url, listOf(persistedRootCookie, persistedWorkshopCookie))

        assertThat(jar.loadForRequest(url))
            .containsExactly(projectedRootCookie, persistedWorkshopCookie)
    }

    @Test
    fun `changing session scope clears persisted cookies`() {
        var scope = "account-a"
        val jar = SteamWebSessionCookieJar(
            sessionScopeProvider = { scope },
        )
        val url = "https://steamcommunity.com/workshop/".toHttpUrl()
        val sessionCookie = steamCookie(
            url = url,
            name = "sessionid",
            value = "abc123",
        )

        jar.saveFromResponse(url, listOf(sessionCookie))
        assertThat(jar.loadForRequest(url)).containsExactly(sessionCookie)

        scope = "account-b"

        assertThat(jar.loadForRequest(url)).isEmpty()
    }

    @Test
    fun `non steam domains are ignored`() {
        val jar = SteamWebSessionCookieJar()
        val url = "https://example.com/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("sessionid")
            .value("abc123")
            .domain(url.host)
            .path("/")
            .build()

        jar.saveFromResponse(url, listOf(cookie))

        assertThat(jar.loadForRequest(url)).isEmpty()
    }

    @Test
    fun `login steampowered domain is treated as steam web session domain`() {
        val jar = SteamWebSessionCookieJar()
        val url = "https://login.steampowered.com/jwt/finalizelogin".toHttpUrl()
        val cookie = steamCookie(
            url = url,
            name = "sessionid",
            value = "abc123",
        )

        jar.saveFromResponse(url, listOf(cookie))

        assertThat(jar.loadForRequest(url)).containsExactly(cookie)
    }

    private fun steamCookie(
        url: okhttp3.HttpUrl,
        name: String,
        value: String,
        path: String = "/",
    ): Cookie =
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain(url.host)
            .path(path)
            .build()
}
