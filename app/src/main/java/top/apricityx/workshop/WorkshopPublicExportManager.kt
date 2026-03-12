package top.apricityx.workshop

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLConnection
import java.util.zip.ZipInputStream

class WorkshopPublicExportManager(
    private val application: Application,
) {
    suspend fun exportDownloadedFiles(
        appId: UInt,
        publishedFileId: ULong,
        stagingDir: File,
        files: List<top.apricityx.workshop.workshop.DownloadedFileInfo>,
        log: suspend (String) -> Unit,
    ): List<ExportedDownloadFile> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) {
            return@withContext emptyList()
        }

        val resolver = application.contentResolver
        val metadata = loadWorkshopMetadata(stagingDir)
        val exportPlan = files.map { file ->
            val source = File(stagingDir, file.relativePath.replace('/', File.separatorChar))
            require(source.isFile) { "Downloaded file is missing: ${file.relativePath}" }
            val displayName = buildExportDisplayName(
                source = source,
                file = file,
                metadata = metadata,
                singleFile = files.size == 1,
            )

            ExportTarget(
                source = source,
                file = file,
                displayName = displayName,
                displayedRelativePath = buildDisplayedRelativePath(
                    relativeFilePath = file.relativePath,
                    displayName = displayName,
                ),
                relativePath = buildDownloadRelativePath(
                    appId = appId,
                    publishedFileId = publishedFileId,
                    relativeFilePath = file.relativePath,
                ),
                userVisiblePath = buildUserVisiblePath(
                    appId = appId,
                    publishedFileId = publishedFileId,
                    relativeFilePath = file.relativePath,
                    displayName = displayName,
                ),
            )
        }

        exportPlan.map { it.relativePath }.distinct().forEach { relativePath ->
            deleteExistingFolderEntries(resolver, relativePath)
        }

        val exportedFiles = mutableListOf<ExportedDownloadFile>()
        exportPlan.forEach { target ->
            val mimeType = URLConnection.guessContentTypeFromName(target.displayName) ?: "application/octet-stream"

            val itemUri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                },
            ) ?: error("Failed to create public download entry for ${target.file.relativePath}")

            try {
                resolver.openOutputStream(itemUri, "w")?.use { output ->
                    target.source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Failed to open public download entry for ${target.file.relativePath}")

                resolver.update(
                    itemUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )

                log(
                    "Exported ${target.file.relativePath} to ${target.userVisiblePath}",
                )
                exportedFiles += ExportedDownloadFile(
                    relativePath = target.displayedRelativePath,
                    sizeBytes = target.file.sizeBytes,
                    modifiedEpochMillis = target.file.modifiedEpochMillis,
                    contentUri = itemUri.toString(),
                    userVisiblePath = target.userVisiblePath,
                )
            } catch (error: Throwable) {
                resolver.delete(itemUri, null, null)
                throw error
            }
        }

        return@withContext exportedFiles
    }

    private fun deleteExistingFolderEntries(
        resolver: android.content.ContentResolver,
        relativePath: String,
    ) {
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(relativePath),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                resolver.delete(
                    ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
                    null,
                    null,
                )
            }
        }
    }

    private fun loadWorkshopMetadata(stagingDir: File): WorkshopMetadata? {
        val metadataFile = File(stagingDir, "metadata.json")
        if (!metadataFile.isFile) {
            return null
        }

        return runCatching {
            val details = JSONObject(metadataFile.readText())
                .getJSONObject("response")
                .getJSONArray("publishedfiledetails")
                .getJSONObject(0)
            WorkshopMetadata(
                title = details.optString("title").trim(),
                filename = details.optString("filename").trim(),
            )
        }.getOrNull()
    }

    private fun buildExportDisplayName(
        source: File,
        file: top.apricityx.workshop.workshop.DownloadedFileInfo,
        metadata: WorkshopMetadata?,
        singleFile: Boolean,
    ): String {
        val originalName = file.relativePath.substringAfterLast('/')
        val metadataFileName = metadata?.filename
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeFileName)
        if (metadataFileName != null) {
            return appendExtensionIfMissing(metadataFileName, inferExtension(source))
        }

        val metadataTitle = metadata?.title
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeFileName)
            ?.ifBlank { null }
        if (singleFile && metadataTitle != null) {
            return appendExtensionIfMissing(metadataTitle, inferExtension(source))
        }

        if (!singleFile || !looksOpaqueSteamFileName(originalName)) {
            return originalName
        }

        val fallbackBaseName = "workshop-${
                file.relativePath.substringAfterLast('/', originalName).hashCode().toUInt().toString(16)
            }"

        return appendExtensionIfMissing(fallbackBaseName, inferExtension(source))
    }

    private fun inferExtension(source: File): String {
        val header = source.inputStream().use { input ->
            ByteArray(4).also { buffer ->
                input.read(buffer)
            }
        }
        val looksZip = header.size >= 4 &&
            header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            header[2] == 3.toByte() &&
            header[3] == 4.toByte()

        if (!looksZip) {
            return source.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        }

        val zipEntries = ZipInputStream(source.inputStream().buffered()).use { zip ->
            buildList {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    add(entry.name)
                    zip.closeEntry()
                }
            }
        }

        return if (zipEntries.any { it.equals("META-INF/MANIFEST.MF", ignoreCase = true) } ||
            zipEntries.any { it.endsWith(".class", ignoreCase = true) }) {
            ".jar"
        } else {
            ".zip"
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "workshop" }

    private fun appendExtensionIfMissing(baseName: String, extension: String): String {
        if (extension.isBlank() || baseName.contains('.')) {
            return baseName
        }
        return baseName + extension
    }

    private fun looksOpaqueSteamFileName(fileName: String): Boolean {
        val baseName = fileName.substringAfterLast('/')
        if ('.' in baseName || baseName.length < 24) {
            return false
        }
        return baseName.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '_' || it == '=' }
    }

    companion object {
        private const val FALLBACK_DOWNLOADS_DIRECTORY = "Download"

        fun buildDownloadRelativePath(
            appId: UInt,
            publishedFileId: ULong,
            relativeFilePath: String,
        ): String {
            val parent = relativeFilePath.substringBeforeLast('/', "")
            return buildString {
                append(Environment.DIRECTORY_DOWNLOADS ?: FALLBACK_DOWNLOADS_DIRECTORY)
                append("/workshop/")
                append(appId)
                append('/')
                append(publishedFileId)
                append('/')
                if (parent.isNotEmpty()) {
                    append(parent)
                    append('/')
                }
            }
        }

        fun buildUserVisiblePath(
            appId: UInt,
            publishedFileId: ULong,
            relativeFilePath: String,
            displayName: String = relativeFilePath.substringAfterLast('/'),
        ): String {
            val parent = relativeFilePath.substringBeforeLast('/', "")
            return buildString {
                append("Download/workshop/")
                append(appId)
                append('/')
                append(publishedFileId)
                append('/')
                if (parent.isNotEmpty()) {
                    append(parent)
                    append('/')
                }
                append(displayName)
            }
        }

        fun buildDisplayedRelativePath(
            relativeFilePath: String,
            displayName: String,
        ): String {
            val parent = relativeFilePath.substringBeforeLast('/', "")
            return if (parent.isBlank()) {
                displayName
            } else {
                "$parent/$displayName"
            }
        }
    }

    private data class WorkshopMetadata(
        val title: String,
        val filename: String,
    )

    private data class ExportTarget(
        val source: File,
        val file: top.apricityx.workshop.workshop.DownloadedFileInfo,
        val displayName: String,
        val displayedRelativePath: String,
        val relativePath: String,
        val userVisiblePath: String,
    )
}
