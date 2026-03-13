package top.apricityx.workshop

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLConnection
import java.util.zip.ZipInputStream

class WorkshopPublicExportManager(
    private val application: Application,
) {
    suspend fun exportDownloadedFiles(
        gameTitle: String,
        itemTitle: String,
        stagingDir: File,
        files: List<top.apricityx.workshop.workshop.DownloadedFileInfo>,
        log: suspend (String) -> Unit,
    ): List<ExportedDownloadFile> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) {
            return@withContext emptyList()
        }

        val metadata = readWorkshopDownloadMetadata(stagingDir)
        val resolvedItemTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: itemTitle
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
                modSubdirectoryPath = buildModSubdirectoryPath(
                    gameTitle = gameTitle,
                    itemTitle = resolvedItemTitle,
                ),
                downloadSubdirectoryPath = buildDownloadSubdirectoryPath(
                    gameTitle = gameTitle,
                    itemTitle = resolvedItemTitle,
                    relativeFilePath = file.relativePath,
                ),
                mediaStoreRelativePath = buildDownloadRelativePath(
                    gameTitle = gameTitle,
                    itemTitle = resolvedItemTitle,
                    relativeFilePath = file.relativePath,
                ),
                publicUserVisiblePath = buildUserVisiblePath(
                    gameTitle = gameTitle,
                    itemTitle = resolvedItemTitle,
                    relativeFilePath = file.relativePath,
                    displayName = displayName,
                ),
            )
        }

        return@withContext when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> exportToMediaStore(exportPlan, log)
            hasLegacyExternalStoragePermission() && isLegacyExternalStorageWritable() ->
                exportToLegacyPublicDownloads(exportPlan, log)

            else -> exportToAppSpecificDownloads(exportPlan, log)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun exportToMediaStore(
        exportPlan: List<ExportTarget>,
        log: suspend (String) -> Unit,
    ): List<ExportedDownloadFile> {
        val resolver = application.contentResolver
        exportPlan.map(ExportTarget::mediaStoreModRelativePath).distinct().forEach { relativePath ->
            deleteExistingFolderEntriesRecursively(resolver, relativePath)
        }

        val exportedFiles = mutableListOf<ExportedDownloadFile>()
        exportPlan.forEach { target ->
            val mimeType = URLConnection.guessContentTypeFromName(target.displayName) ?: "application/octet-stream"

            val itemUri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, target.mediaStoreRelativePath)
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
                    "Exported ${target.file.relativePath} to ${target.publicUserVisiblePath}",
                )
                exportedFiles += ExportedDownloadFile(
                    relativePath = target.displayedRelativePath,
                    sizeBytes = target.file.sizeBytes,
                    modifiedEpochMillis = target.file.modifiedEpochMillis,
                    contentUri = itemUri.toString(),
                    userVisiblePath = target.publicUserVisiblePath,
                )
            } catch (error: Throwable) {
                resolver.delete(itemUri, null, null)
                throw error
            }
        }

        return exportedFiles
    }

    private suspend fun exportToLegacyPublicDownloads(
        exportPlan: List<ExportTarget>,
        log: suspend (String) -> Unit,
    ): List<ExportedDownloadFile> {
        val downloadsRoot = legacyPublicDownloadsRoot()
            ?: return exportToAppSpecificDownloads(exportPlan, log)

        return exportToFileSystem(
            exportPlan = exportPlan,
            rootDir = downloadsRoot,
            userVisiblePathFor = { target, _ -> target.publicUserVisiblePath },
            onFileExported = { destinationFile, mimeType ->
                MediaScannerConnection.scanFile(
                    application,
                    arrayOf(destinationFile.absolutePath),
                    arrayOf(mimeType),
                    null,
                )
            },
            log = log,
        )
    }

    private suspend fun exportToAppSpecificDownloads(
        exportPlan: List<ExportTarget>,
        log: suspend (String) -> Unit,
    ): List<ExportedDownloadFile> {
        val downloadsRoot = appSpecificDownloadsRoot()
        log("Legacy storage permission unavailable; exported files to app-specific storage.")
        return exportToFileSystem(
            exportPlan = exportPlan,
            rootDir = downloadsRoot,
            userVisiblePathFor = { _, destinationFile -> destinationFile.absolutePath },
            onFileExported = null,
            log = log,
        )
    }

    private suspend fun exportToFileSystem(
        exportPlan: List<ExportTarget>,
        rootDir: File,
        userVisiblePathFor: (ExportTarget, File) -> String,
        onFileExported: ((File, String) -> Unit)?,
        log: suspend (String) -> Unit,
    ): List<ExportedDownloadFile> {
        deleteExistingDirectories(rootDir, exportPlan.map(ExportTarget::modSubdirectoryPath).distinct())

        val exportedFiles = mutableListOf<ExportedDownloadFile>()
        exportPlan.forEach { target ->
            val destinationDir = File(rootDir, target.downloadSubdirectoryPath)
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                error("Failed to create export directory: ${destinationDir.absolutePath}")
            }

            val destinationFile = File(destinationDir, target.displayName)
            target.source.inputStream().buffered().use { input ->
                destinationFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.setLastModified(target.file.modifiedEpochMillis)

            val mimeType = URLConnection.guessContentTypeFromName(target.displayName) ?: "application/octet-stream"
            onFileExported?.invoke(destinationFile, mimeType)

            val visiblePath = userVisiblePathFor(target, destinationFile)
            log("Exported ${target.file.relativePath} to $visiblePath")
            exportedFiles += ExportedDownloadFile(
                relativePath = target.displayedRelativePath,
                sizeBytes = target.file.sizeBytes,
                modifiedEpochMillis = target.file.modifiedEpochMillis,
                contentUri = fileProviderUri(destinationFile).toString(),
                userVisiblePath = visiblePath,
            )
        }

        return exportedFiles
    }

    private fun deleteExistingDirectories(
        rootDir: File,
        relativeDirectories: List<String>,
    ) {
        relativeDirectories.forEach { relativeDirectory ->
            File(rootDir, relativeDirectory).deleteRecursively()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteExistingFolderEntriesRecursively(
        resolver: android.content.ContentResolver,
        relativePath: String,
    ) {
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("$relativePath%"),
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

    @Suppress("DEPRECATION")
    private fun legacyPublicDownloadsRoot(): File? {
        if (!isLegacyExternalStorageWritable()) {
            return null
        }
        return Environment.getExternalStoragePublicDirectory(downloadsDirectoryName())
    }

    private fun appSpecificDownloadsRoot(): File =
        application.getExternalFilesDir(downloadsDirectoryName())
            ?: File(application.filesDir, "exports/downloads")

    private fun hasLegacyExternalStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    private fun isLegacyExternalStorageWritable(): Boolean =
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    private fun fileProviderUri(file: File) =
        FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            file,
        )

    private fun buildExportDisplayName(
        source: File,
        file: top.apricityx.workshop.workshop.DownloadedFileInfo,
        metadata: WorkshopDownloadMetadata?,
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

        fun buildModSubdirectoryPath(
            gameTitle: String,
            itemTitle: String,
        ): String = buildString {
            append("workshop/")
            append(sanitizeDirectorySegment(gameTitle, fallback = "game"))
            append('/')
            append(sanitizeDirectorySegment(itemTitle, fallback = "mod"))
            append('/')
        }

        fun buildDownloadSubdirectoryPath(
            gameTitle: String,
            itemTitle: String,
            relativeFilePath: String,
        ): String {
            val parent = relativeFilePath.substringBeforeLast('/', "")
            return buildString {
                append(buildModSubdirectoryPath(gameTitle = gameTitle, itemTitle = itemTitle))
                if (parent.isNotEmpty()) {
                    append(parent)
                    append('/')
                }
            }
        }

        fun downloadRootRelativePath(): String = downloadsDirectoryName() + "/workshop/"

        fun buildDownloadRelativePath(
            gameTitle: String,
            itemTitle: String,
            relativeFilePath: String,
        ): String = downloadsDirectoryName() + "/" +
            buildDownloadSubdirectoryPath(
                gameTitle = gameTitle,
                itemTitle = itemTitle,
                relativeFilePath = relativeFilePath,
            )

        fun buildUserVisiblePath(
            gameTitle: String,
            itemTitle: String,
            relativeFilePath: String,
            displayName: String = relativeFilePath.substringAfterLast('/'),
        ): String = buildDownloadRelativePath(
            gameTitle = gameTitle,
            itemTitle = itemTitle,
            relativeFilePath = relativeFilePath,
        ) + displayName

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

        private fun downloadsDirectoryName(): String =
            Environment.DIRECTORY_DOWNLOADS?.takeIf { it.isNotBlank() } ?: FALLBACK_DOWNLOADS_DIRECTORY

        private fun sanitizeDirectorySegment(
            value: String,
            fallback: String,
        ): String =
            value
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.', ' ')
                .ifBlank { fallback }
    }

    private data class ExportTarget(
        val source: File,
        val file: top.apricityx.workshop.workshop.DownloadedFileInfo,
        val displayName: String,
        val displayedRelativePath: String,
        val modSubdirectoryPath: String,
        val downloadSubdirectoryPath: String,
        val mediaStoreRelativePath: String,
        val publicUserVisiblePath: String,
    ) {
        val mediaStoreModRelativePath: String =
            downloadsDirectoryName() + "/" + modSubdirectoryPath
    }
}

