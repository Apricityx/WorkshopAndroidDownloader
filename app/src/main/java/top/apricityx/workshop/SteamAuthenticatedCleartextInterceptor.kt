package top.apricityx.workshop

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

class SteamAuthenticatedCleartextInterceptor(
    private val hasAuthenticatedSteamSession: () -> Boolean,
    private val allowAuthenticatedCleartextHttpProvider: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (
            !request.url.isHttps &&
            hasAuthenticatedSteamSession() &&
            request.url.host.isSteamAuthenticatedTrafficDomain() &&
            !allowAuthenticatedCleartextHttpProvider()
        ) {
            throw SteamAuthenticatedCleartextBlockedException(request.url.host)
        }
        return chain.proceed(request)
    }
}

class SteamAuthenticatedCleartextBlockedException(
    host: String,
) : IOException(
    "当前设置禁止带 Steam 登录态的明文 HTTP 请求：$host。可在设置里开启后重试。",
)

internal fun String.isSteamAuthenticatedTrafficDomain(): Boolean {
    val host = lowercase()
    return host.matchesDomainSuffix("steamcommunity.com") ||
        host.matchesDomainSuffix("steampowered.com") ||
        host.matchesDomainSuffix("steamcontent.com") ||
        host.matchesDomainSuffix("steam.clngaa.com") ||
        host.matchesDomainSuffix("dl.eccdnx.com") ||
        host.matchesDomainSuffix("pphimalayanrt.com")
}

private fun String.matchesDomainSuffix(suffix: String): Boolean =
    this == suffix || endsWith(".$suffix")
