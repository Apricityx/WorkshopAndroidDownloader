package top.apricityx.workshop.steam.protocol

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

const val DEFAULT_HTTP_TIMEOUT_SECONDS = 30L

fun OkHttpClient.Builder.applyDefaultHttpTimeouts(
    timeoutSeconds: Long = DEFAULT_HTTP_TIMEOUT_SECONDS,
): OkHttpClient.Builder =
    connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)

fun newDefaultOkHttpClient(
    timeoutSeconds: Long = DEFAULT_HTTP_TIMEOUT_SECONDS,
): OkHttpClient =
    OkHttpClient.Builder()
        .applyDefaultHttpTimeouts(timeoutSeconds)
        .build()
