package top.apricityx.workshop

import android.app.Application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class WorkshopPreviewImageCache(
    application: Application,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val previewRoot = File(application.filesDir, "mod-library/previews")

    suspend fun cachePreviewImage(
        appId: UInt,
        publishedFileId: ULong,
        imageUrl: String?,
    ): String? = withContext(Dispatchers.IO) {
        val normalizedUrl = imageUrl?.trim().orEmpty()
        if (normalizedUrl.isBlank()) {
            return@withContext null
        }

        val request = Request.Builder()
            .url(normalizedUrl)
            .build()

        val bodyBytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Preview image request failed: ${response.code}")
            }
            response.body?.bytes() ?: error("Preview image response body was empty")
        }

        val extension = normalizedUrl
            .substringBefore('?')
            .substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .takeIf(String::isNotBlank)
            ?: "img"

        val previewDir = File(previewRoot, "$appId/$publishedFileId").apply { mkdirs() }
        previewDir.listFiles()?.forEach(File::delete)
        val previewFile = File(previewDir, "cover.$extension")
        previewFile.writeBytes(bodyBytes)
        previewFile.absolutePath
    }

    fun deleteCachedPreview(path: String?) {
        if (path.isNullOrBlank()) {
            return
        }

        val previewFile = File(path)
        if (!previewFile.exists()) {
            return
        }

        previewFile.delete()
        var current = previewFile.parentFile
        while (current != null && current.exists() && current.isDirectory && current.list().isNullOrEmpty()) {
            val shouldStop = current == previewRoot
            current.delete()
            if (shouldStop) {
                break
            }
            current = current.parentFile
        }
    }
}
