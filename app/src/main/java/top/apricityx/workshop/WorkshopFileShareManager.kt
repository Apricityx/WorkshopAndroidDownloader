package top.apricityx.workshop

import android.content.Intent
import android.net.Uri
import java.net.URLConnection

object WorkshopFileShareManager {
    fun createShareFileIntent(file: ExportedDownloadFile): Intent? {
        val uri = runCatching { Uri.parse(file.contentUri) }
            .getOrNull()
            ?.takeIf { it.toString().isNotBlank() }
            ?: return null

        val mimeType = URLConnection.guessContentTypeFromName(file.relativePath.substringAfterLast('/')) ?: "*/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, "分享文件").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
