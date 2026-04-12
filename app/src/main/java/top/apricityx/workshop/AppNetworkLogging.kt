package top.apricityx.workshop
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal fun OkHttpClient.Builder.applyAppNetworkLogging(clientName: String): OkHttpClient.Builder =
    eventListenerFactory(AppNetworkLoggingEventListener.Factory(clientName))

internal class AppNetworkLoggingEventListener private constructor(
    private val clientName: String,
    private val callId: Long,
) : EventListener() {
    private val startedAtNanos = System.nanoTime()

    override fun callStart(call: Call) {
        val request = call.request()
        workshopLogInfo(
            "NET callStart client=$clientName id=$callId ${request.method} ${request.url.redactedForNetworkLog()} request=${request.headers.requestHeadersForLog()}",
        )
    }

    override fun dnsStart(
        call: Call,
        domainName: String,
    ) {
        workshopLogInfo("NET dnsStart client=$clientName id=$callId host=$domainName elapsedMs=${elapsedMs()}")
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<InetAddress>,
    ) {
        workshopLogInfo(
            "NET dnsEnd client=$clientName id=$callId host=$domainName resolved=${inetAddressList.joinToString(",") { it.hostAddress.orEmpty() }} elapsedMs=${elapsedMs()}",
        )
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: java.net.InetSocketAddress,
        proxy: Proxy,
    ) {
        workshopLogInfo(
            "NET connectStart client=$clientName id=$callId target=${inetSocketAddress.hostString}:${inetSocketAddress.port} proxy=${proxy.type()} elapsedMs=${elapsedMs()}",
        )
    }

    override fun secureConnectStart(call: Call) {
        workshopLogInfo("NET secureConnectStart client=$clientName id=$callId elapsedMs=${elapsedMs()}")
    }

    override fun secureConnectEnd(
        call: Call,
        handshake: Handshake?,
    ) {
        workshopLogInfo(
            "NET secureConnectEnd client=$clientName id=$callId tls=${handshake?.tlsVersion ?: "-"} cipher=${handshake?.cipherSuite ?: "-"} elapsedMs=${elapsedMs()}",
        )
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: java.net.InetSocketAddress,
        proxy: Proxy,
        protocol: okhttp3.Protocol?,
        ioe: IOException,
    ) {
        workshopLogWarn(
            "NET connectFailed client=$clientName id=$callId target=${inetSocketAddress.hostString}:${inetSocketAddress.port} proxy=${proxy.type()} protocol=${protocol ?: "-"} elapsedMs=${elapsedMs()} error=${ioe::class.java.simpleName}:${ioe.message}",
            ioe,
        )
    }

    override fun connectionAcquired(
        call: Call,
        connection: Connection,
    ) {
        val route = connection.route()
        workshopLogInfo(
            "NET connectionAcquired client=$clientName id=$callId protocol=${connection.protocol()} route=${route.socketAddress} proxy=${route.proxy.type()} elapsedMs=${elapsedMs()}",
        )
    }

    override fun requestHeadersStart(call: Call) {
        workshopLogInfo("NET requestHeadersStart client=$clientName id=$callId elapsedMs=${elapsedMs()}")
    }

    override fun requestBodyStart(call: Call) {
        workshopLogInfo("NET requestBodyStart client=$clientName id=$callId elapsedMs=${elapsedMs()}")
    }

    override fun requestBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        workshopLogInfo("NET requestBodyEnd client=$clientName id=$callId bytes=$byteCount elapsedMs=${elapsedMs()}")
    }

    override fun responseHeadersStart(call: Call) {
        workshopLogInfo("NET responseHeadersStart client=$clientName id=$callId elapsedMs=${elapsedMs()}")
    }

    override fun responseHeadersEnd(
        call: Call,
        response: Response,
    ) {
        workshopLogInfo(
            "NET responseHeadersEnd client=$clientName id=$callId code=${response.code} message=${response.message.ifBlank { "-" }} response=${response.headers.responseHeadersForLog()} elapsedMs=${elapsedMs()}",
        )
    }

    override fun responseBodyStart(call: Call) {
        workshopLogInfo("NET responseBodyStart client=$clientName id=$callId elapsedMs=${elapsedMs()}")
    }

    override fun responseBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        workshopLogInfo("NET responseBodyEnd client=$clientName id=$callId bytes=$byteCount elapsedMs=${elapsedMs()}")
    }

    override fun callEnd(call: Call) {
        workshopLogInfo("NET callEnd client=$clientName id=$callId elapsedMs=${elapsedMs()}")
    }

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) {
        workshopLogWarn(
            "NET callFailed client=$clientName id=$callId elapsedMs=${elapsedMs()} error=${ioe::class.java.simpleName}:${ioe.message}",
            ioe,
        )
    }

    private fun elapsedMs(): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    internal class Factory(
        private val clientName: String,
    ) : EventListener.Factory {
        override fun create(call: Call): EventListener =
            AppNetworkLoggingEventListener(
                clientName = clientName,
                callId = nextCallId.incrementAndGet(),
            )
    }

    private companion object {
        val nextCallId = AtomicLong(0L)
    }
}

internal fun HttpUrl.redactedForNetworkLog(): String {
    if (querySize == 0) {
        return toString()
    }
    val builder = newBuilder().query(null)
    queryParameterNames.forEach { name ->
        val values = queryParameterValues(name)
        values.forEach { value ->
            builder.addQueryParameter(
                name,
                if (name.isSensitiveNetworkLogField()) REDACTED_VALUE else value,
            )
        }
    }
    return builder.build().toString()
}

private fun Headers.requestHeadersForLog(): String =
    buildHeaderSummary(
        names = REQUEST_HEADER_ALLOWLIST,
        label = "headers",
    )

private fun Headers.responseHeadersForLog(): String =
    buildHeaderSummary(
        names = RESPONSE_HEADER_ALLOWLIST,
        label = "headers",
    )

private fun Headers.buildHeaderSummary(
    names: Set<String>,
    label: String,
): String {
    val values = names.mapNotNull { name ->
        this[name]?.takeIf(String::isNotBlank)?.let { value -> "${name.lowercase(Locale.ROOT)}=${value.take(160)}" }
    }
    return if (values.isEmpty()) {
        "$label=-"
    } else {
        "$label=${values.joinToString(",")}"
    }
}

private fun String.isSensitiveNetworkLogField(): Boolean {
    val normalized = lowercase(Locale.ROOT)
    return normalized in SENSITIVE_QUERY_PARAMETER_NAMES ||
        normalized.contains("token") ||
        normalized.contains("secret") ||
        normalized.contains("password") ||
        normalized.contains("session")
}

private val REQUEST_HEADER_ALLOWLIST = setOf(
    "Content-Type",
    "Accept",
    "Accept-Language",
    "Range",
    "Referer",
    "Origin",
    "Host",
    "User-Agent",
    "X-Requested-With",
)

private val RESPONSE_HEADER_ALLOWLIST = setOf(
    "Content-Type",
    "Content-Length",
    "Location",
    "Server",
    "Via",
    "Cache-Control",
    "Content-Range",
)

private val SENSITIVE_QUERY_PARAMETER_NAMES = setOf(
    "access_token",
    "token",
    "auth",
    "apikey",
    "api_key",
    "key",
    "code",
    "sig",
    "signature",
    "password",
    "sessionid",
)

private const val REDACTED_VALUE = "***"
