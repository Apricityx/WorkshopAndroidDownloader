package top.apricityx.workshop.update

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import top.apricityx.workshop.BuildConfig

class WorkshopUpdateService(
    baseClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client = baseClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun checkForUpdates(
        currentVersion: String,
        preferredUserSource: UpdateSource,
    ): UpdateCheckExecutionResult = withContext(Dispatchers.IO) {
        var lastErrorSummary = "无法连接任何更新源。"
        var successfulMetadataSource: UpdateSource? = null
        var releaseInfo: UpdateReleaseInfo? = null

        for (source in UpdateSource.metadataCandidates(preferredUserSource)) {
            val requestUrl = source.buildUrl(latestReleaseApiUrl)
            try {
                val responseText = requestText(requestUrl)
                val parsed = parseLatestRelease(responseText)
                    ?: throw IOException("更新元数据格式无效。")
                successfulMetadataSource = source
                releaseInfo = parsed
                break
            } catch (error: Throwable) {
                lastErrorSummary = "${source.displayName}: ${summarizeError(error)}"
            }
        }

        val metadataSource = successfulMetadataSource
        val release = releaseInfo
        if (metadataSource == null || release == null) {
            return@withContext UpdateCheckExecutionResult.Failure(
                errorSummary = lastErrorSummary,
            )
        }

        val hasUpdate = WorkshopUpdateVersioning.isRemoteNewer(
            currentVersion = currentVersion,
            remoteVersionTag = release.normalizedVersion,
        )
        if (!hasUpdate) {
            return@withContext UpdateCheckExecutionResult.Success(
                currentVersion = currentVersion,
                release = release,
                metadataSource = metadataSource,
                downloadResolution = null,
                hasUpdate = false,
            )
        }

        val downloadResolution = resolveDownloadResolution(
            release = release,
            preferredUserSource = preferredUserSource,
            metadataSource = metadataSource,
        )
        if (downloadResolution == null) {
            return@withContext UpdateCheckExecutionResult.Failure(
                errorSummary = "无法解析可访问的 APK 下载地址。",
                release = release,
                metadataSource = metadataSource,
            )
        }

        UpdateCheckExecutionResult.Success(
            currentVersion = currentVersion,
            release = release,
            metadataSource = metadataSource,
            downloadResolution = downloadResolution,
            hasUpdate = true,
        )
    }

    internal fun parseLatestRelease(responseText: String): UpdateReleaseInfo? {
        val payload = runCatching {
            json.decodeFromString<GithubReleasePayload>(responseText)
        }.getOrNull() ?: return null
        val rawTagName = payload.tagName.trim()
        if (rawTagName.isEmpty()) {
            return null
        }
        val asset = payload.assets.firstOrNull { asset ->
            asset.name.trim().endsWith(".apk", ignoreCase = true)
        } ?: return null
        val assetName = asset.name.trim()
        val assetDownloadUrl = asset.browserDownloadUrl.trim()
        if (assetName.isEmpty() || assetDownloadUrl.isEmpty()) {
            return null
        }
        return UpdateReleaseInfo(
            rawTagName = rawTagName,
            normalizedVersion = WorkshopUpdateVersioning.normalizeVersionTag(rawTagName),
            publishedAtRaw = payload.publishedAt?.trim()?.ifEmpty { null },
            publishedAtDisplayText = WorkshopUpdateVersioning.formatPublishedAt(payload.publishedAt),
            notesText = WorkshopUpdateVersioning.normalizeReleaseNotesText(payload.body),
            assetName = assetName,
            assetDownloadUrl = assetDownloadUrl,
        )
    }

    internal fun resolveDownloadResolution(
        release: UpdateReleaseInfo,
        preferredUserSource: UpdateSource,
        metadataSource: UpdateSource,
    ): UpdateDownloadResolution? {
        for (source in UpdateSource.downloadCandidates(preferredUserSource, metadataSource)) {
            val candidateUrl = source.buildUrl(release.assetDownloadUrl)
            if (isDownloadCandidateReachable(candidateUrl)) {
                return UpdateDownloadResolution(
                    source = source,
                    resolvedUrl = candidateUrl,
                )
            }
        }
        return null
    }

    private fun requestText(requestUrl: String): String {
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun isDownloadCandidateReachable(requestUrl: String): Boolean {
        return requestProbe(requestUrl, head = true) || requestRangeProbe(requestUrl)
    }

    private fun requestProbe(requestUrl: String, head: Boolean): Boolean {
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", USER_AGENT)
        val request = if (head) {
            requestBuilder.head().build()
        } else {
            requestBuilder.get().build()
        }
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    private fun requestRangeProbe(requestUrl: String): Boolean {
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-0")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 206
            }
        }.getOrDefault(false)
    }

    private fun summarizeError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        return if (message.isNotEmpty()) {
            message
        } else {
            error.javaClass.simpleName
        }
    }

    @Serializable
    private data class GithubReleasePayload(
        @SerialName("tag_name")
        val tagName: String = "",
        @SerialName("published_at")
        val publishedAt: String? = null,
        val body: String? = null,
        val assets: List<GithubReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class GithubReleaseAsset(
        val name: String = "",
        @SerialName("browser_download_url")
        val browserDownloadUrl: String = "",
    )

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 8L
        private const val READ_TIMEOUT_SECONDS = 12L
        private const val USER_AGENT = "WorkshopAndroidDownloader-Update"
        private val latestReleaseApiUrl: String
            get() = "https://api.github.com/repos/${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}/releases/latest"
    }
}
