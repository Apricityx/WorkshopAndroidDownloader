package top.apricityx.workshop

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModLibraryRepository(
    application: Application,
    private val store: ModLibraryStore = ModLibraryStore(File(application.filesDir, "mod-library/index.json")),
    private val localDataSource: ModLibraryLocalDataSource = AndroidModLibraryLocalDataSource(application),
    private val previewImageCache: WorkshopPreviewImageCache = WorkshopPreviewImageCache(application),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun syncWithLocalStorage(): List<DownloadedModEntry> = withContext(Dispatchers.IO) {
        store.withFileLock {
            val indexed = store.loadEntries()
            val merged = mergeIndexedAndLocalMods(indexed, localDataSource.listLocalMods(indexed), nowMillis)
            store.saveEntries(merged)
            merged
        }
    }

    suspend fun upsertDownloadedMod(
        appId: UInt,
        publishedFileId: ULong,
        gameTitle: String,
        itemTitle: String,
        previewImagePath: String? = null,
        versionId: String = LEGACY_MOD_VERSION_ID,
        versionUpdatedAtMillis: Long? = null,
        files: List<ExportedDownloadFile>,
    ): List<DownloadedModEntry> = withContext(Dispatchers.IO) {
        store.withFileLock {
            val currentEntries = store.loadEntries()
            val normalizedVersionId = normalizeModVersionId(versionId)
            val existingEntry = currentEntries.firstOrNull {
                it.matches(appId, publishedFileId, normalizedVersionId)
            }
            val updatedEntry = DownloadedModEntry(
                appId = appId,
                publishedFileId = publishedFileId,
                gameTitle = gameTitle,
                itemTitle = itemTitle,
                previewImagePath = previewImagePath ?: existingEntry?.previewImagePath?.takeIf(::isExistingFile),
                versionId = normalizedVersionId,
                versionUpdatedAtMillis = versionUpdatedAtMillis,
                storedAtMillis = nowMillis(),
                files = files.sortedBy(ExportedDownloadFile::relativePath),
            )
            val updated = (currentEntries.filterNot { it.matches(appId, publishedFileId, normalizedVersionId) } + updatedEntry)
                .sortedForDisplay()
            store.saveEntries(updated)
            updated
        }
    }

    suspend fun deleteMod(entry: DownloadedModEntry): List<DownloadedModEntry> = withContext(Dispatchers.IO) {
        store.withFileLock {
            localDataSource.deleteModFiles(entry)
            previewImageCache.deleteCachedPreview(entry.previewImagePath)
            val remainingIndexedEntries = store.loadEntries().filterNot {
                it.matches(entry.appId, entry.publishedFileId, entry.versionId)
            }
            val synced = mergeIndexedAndLocalMods(
                indexedEntries = remainingIndexedEntries,
                localMods = localDataSource.listLocalMods(remainingIndexedEntries),
                nowMillis = nowMillis,
            )
            store.saveEntries(synced)
            synced
        }
    }

    interface ModLibraryLocalDataSource {
        suspend fun listLocalMods(indexedEntries: List<DownloadedModEntry>): List<LocalModSnapshot>

        suspend fun deleteModFiles(entry: DownloadedModEntry)
    }

    data class LocalModSnapshot(
        val appId: UInt,
        val publishedFileId: ULong,
        val gameTitle: String? = null,
        val itemTitle: String? = null,
        val versionId: String = LEGACY_MOD_VERSION_ID,
        val versionUpdatedAtMillis: Long? = null,
        val files: List<ExportedDownloadFile>,
    )
}

@VisibleForTesting
internal fun mergeIndexedAndLocalMods(
    indexedEntries: List<DownloadedModEntry>,
    localMods: List<ModLibraryRepository.LocalModSnapshot>,
    nowMillis: () -> Long,
): List<DownloadedModEntry> {
    val indexedByKey = indexedEntries.associateBy(::entryIdentityKey)
    return localMods
        .map { local ->
            val existing = indexedByKey[
                entryIdentityKey(
                    appId = local.appId,
                    publishedFileId = local.publishedFileId,
                    versionId = local.versionId,
                )
            ]
            DownloadedModEntry(
                appId = local.appId,
                publishedFileId = local.publishedFileId,
                gameTitle = existing?.gameTitle ?: local.gameTitle.orEmpty().ifBlank { "App ${local.appId}" },
                itemTitle = existing?.itemTitle ?: local.itemTitle.orEmpty().ifBlank { "模组 ${local.publishedFileId}" },
                previewImagePath = existing?.previewImagePath?.takeIf(::isExistingFile),
                versionId = normalizeModVersionId(existing?.versionId ?: local.versionId),
                versionUpdatedAtMillis = existing?.versionUpdatedAtMillis ?: local.versionUpdatedAtMillis,
                storedAtMillis = existing?.storedAtMillis
                    ?: local.files.maxOfOrNull(ExportedDownloadFile::modifiedEpochMillis)
                    ?: nowMillis(),
                files = local.files.sortedBy(ExportedDownloadFile::relativePath),
            )
        }
        .sortedForDisplay()
}

private fun List<DownloadedModEntry>.sortedForDisplay(): List<DownloadedModEntry> =
    distinctBy(::entryIdentityKey)
        .sortedWith(
            compareByDescending<DownloadedModEntry> { it.storedAtMillis }
                .thenByDescending { it.versionUpdatedAtMillis ?: Long.MIN_VALUE }
                .thenBy { it.gameTitle.lowercase() }
                .thenBy { it.itemTitle.lowercase() },
        )

private fun entryIdentityKey(it: DownloadedModEntry): Triple<UInt, ULong, String> =
    entryIdentityKey(
        appId = it.appId,
        publishedFileId = it.publishedFileId,
        versionId = it.versionId,
    )

private fun entryIdentityKey(
    appId: UInt,
    publishedFileId: ULong,
    versionId: String,
): Triple<UInt, ULong, String> =
    Triple(appId, publishedFileId, normalizeModVersionId(versionId))

private fun isExistingFile(path: String): Boolean =
    File(path).isFile

internal fun deleteFileAndEmptyParents(file: File) {
    if (file.exists()) {
        file.delete()
    }
    var current = file.parentFile
    while (current != null && current.exists() && current.isDirectory && current.list().isNullOrEmpty()) {
        val currentPath = current.absolutePath
        current.delete()
        if (currentPath.endsWith("${File.separator}workshop")) {
            break
        }
        current = current.parentFile
    }
}

private class AndroidModLibraryLocalDataSource(
    private val application: Application,
) : ModLibraryRepository.ModLibraryLocalDataSource {
    override suspend fun listLocalMods(
        indexedEntries: List<DownloadedModEntry>,
    ): List<ModLibraryRepository.LocalModSnapshot> = withContext(Dispatchers.IO) {
        val discovered = linkedMapOf<Triple<UInt, ULong, String>, MutableList<DiscoveredFile>>()
        collectIndexedFiles(indexedEntries).forEach { addDiscoveredFile(discovered, it) }
        scanLegacyMediaStore().forEach { addDiscoveredFile(discovered, it) }
        scanLegacyFileSystem().forEach { addDiscoveredFile(discovered, it) }

        discovered.entries.map { (key, files) ->
            val first = files.first()
            ModLibraryRepository.LocalModSnapshot(
                appId = key.first,
                publishedFileId = key.second,
                gameTitle = first.gameTitle,
                itemTitle = first.itemTitle,
                versionId = key.third,
                versionUpdatedAtMillis = first.versionUpdatedAtMillis,
                files = files
                    .map(DiscoveredFile::file)
                    .distinctBy { it.contentUri to it.userVisiblePath }
                    .sortedBy(ExportedDownloadFile::relativePath),
            )
        }
    }

    override suspend fun deleteModFiles(entry: DownloadedModEntry) = withContext(Dispatchers.IO) {
        entry.files.forEach { file ->
            deleteMediaStoreFileIfNeeded(file)
            resolvePhysicalFile(file)?.let(::deleteFileAndEmptyParents)
        }
    }

    private fun collectIndexedFiles(indexedEntries: List<DownloadedModEntry>): List<DiscoveredFile> =
        indexedEntries.flatMap { entry ->
            entry.files.mapNotNull { file ->
                if (!exportedFileExists(file)) {
                    return@mapNotNull null
                }
                DiscoveredFile(
                    appId = entry.appId,
                    publishedFileId = entry.publishedFileId,
                    gameTitle = entry.gameTitle,
                    itemTitle = entry.itemTitle,
                    versionId = entry.versionId,
                    versionUpdatedAtMillis = entry.versionUpdatedAtMillis,
                    file = file,
                )
            }
        }

    private fun scanLegacyMediaStore(): List<DiscoveredFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return emptyList()
        }

        val resolver = application.contentResolver
        val prefix = WorkshopPublicExportManager.downloadRootRelativePath()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        return buildList {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$prefix%"),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(displayNameIndex).orEmpty()
                    val relativePath = cursor.getString(relativePathIndex).orEmpty()
                    val parsed = parseLegacyRelativePath(relativePath + displayName) ?: continue
                    add(
                        DiscoveredFile(
                            appId = parsed.appId,
                            publishedFileId = parsed.publishedFileId,
                            gameTitle = parsed.gameTitle,
                            itemTitle = parsed.itemTitle,
                            versionId = parsed.versionId,
                            versionUpdatedAtMillis = parsed.versionUpdatedAtMillis,
                            file = ExportedDownloadFile(
                                relativePath = parsed.fileRelativePath,
                                sizeBytes = cursor.getLong(sizeIndex),
                                modifiedEpochMillis = cursor.getLong(modifiedIndex) * 1000L,
                                contentUri = ContentUris.withAppendedId(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    cursor.getLong(idIndex),
                                ).toString(),
                                userVisiblePath = relativePath + displayName,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun scanLegacyFileSystem(): List<DiscoveredFile> {
        val roots = listOfNotNull(
            application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { downloadRoot ->
                ScanRoot(
                    root = File(downloadRoot, "workshop"),
                    kind = RootKind.AppExternal,
                )
            },
            ScanRoot(
                root = File(application.filesDir, "exports/downloads/workshop"),
                kind = RootKind.AppInternal,
            ),
            legacyPublicRoot(),
        )

        return roots.flatMap { root ->
            if (!root.root.isDirectory) {
                emptyList()
            } else {
                root.root.walkTopDown()
                    .filter(File::isFile)
                    .mapNotNull { file ->
                        val parsed = parseLegacyFileSystemPath(root.root, file) ?: return@mapNotNull null
                        val contentUri = fileContentUri(file) ?: return@mapNotNull null
                        val relativeToRoot = file.relativeTo(root.root).invariantSeparatorsPath
                        DiscoveredFile(
                            appId = parsed.appId,
                            publishedFileId = parsed.publishedFileId,
                            gameTitle = parsed.gameTitle,
                            itemTitle = parsed.itemTitle,
                            versionId = parsed.versionId,
                            versionUpdatedAtMillis = parsed.versionUpdatedAtMillis,
                            file = ExportedDownloadFile(
                                relativePath = parsed.fileRelativePath,
                                sizeBytes = file.length(),
                                modifiedEpochMillis = file.lastModified(),
                                contentUri = contentUri,
                                userVisiblePath = root.kind.userVisiblePath(
                                    file = file,
                                    rootRelativePath = relativeToRoot,
                                ),
                            ),
                        )
                    }
                    .toList()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyPublicRoot(): ScanRoot? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return null
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return ScanRoot(
            root = File(downloadsDir, "workshop"),
            kind = RootKind.LegacyPublic,
        )
    }

    private fun addDiscoveredFile(
        target: MutableMap<Triple<UInt, ULong, String>, MutableList<DiscoveredFile>>,
        file: DiscoveredFile,
    ) {
        val key = entryIdentityKey(
            appId = file.appId,
            publishedFileId = file.publishedFileId,
            versionId = file.versionId,
        )
        target.getOrPut(key) { mutableListOf() }.add(file)
    }

    private fun fileContentUri(file: File): String? =
        runCatching {
            FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file,
            ).toString()
        }.getOrNull()

    private fun exportedFileExists(file: ExportedDownloadFile): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = runCatching { Uri.parse(file.contentUri) }.getOrNull()
            if (uri?.authority == MediaStore.AUTHORITY) {
                return application.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    null,
                    null,
                    null,
                )?.use { cursor -> cursor.moveToFirst() } == true
            }
        }

        return resolvePhysicalFile(file)?.exists() == true
    }

    private fun deleteMediaStoreFileIfNeeded(file: ExportedDownloadFile): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val uri = runCatching { Uri.parse(file.contentUri) }.getOrNull() ?: return false
        if (uri.authority != MediaStore.AUTHORITY) {
            return false
        }

        return runCatching {
            application.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    private fun resolvePhysicalFile(file: ExportedDownloadFile): File? {
        if (file.userVisiblePath.isBlank()) {
            return null
        }

        val visibleFile = File(file.userVisiblePath)
        if (visibleFile.isAbsolute) {
            return visibleFile
        }

        val relativePath = file.userVisiblePath.removePrefix("${Environment.DIRECTORY_DOWNLOADS}/")
        if (relativePath == file.userVisiblePath) {
            return null
        }

        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, relativePath)
    }

    private data class ScanRoot(
        val root: File,
        val kind: RootKind,
    )

    private enum class RootKind {
        AppExternal,
        AppInternal,
        LegacyPublic,
        ;

        fun userVisiblePath(
            file: File,
            rootRelativePath: String,
        ): String =
            when (this) {
                AppExternal,
                AppInternal,
                -> file.absolutePath

                LegacyPublic -> WorkshopPublicExportManager.downloadRootRelativePath() + rootRelativePath
            }
    }

    private data class ParsedModPath(
        val appId: UInt,
        val publishedFileId: ULong,
        val gameTitle: String? = null,
        val itemTitle: String? = null,
        val versionId: String = LEGACY_MOD_VERSION_ID,
        val versionUpdatedAtMillis: Long? = null,
        val fileRelativePath: String,
    )

    private data class DiscoveredFile(
        val appId: UInt,
        val publishedFileId: ULong,
        val gameTitle: String? = null,
        val itemTitle: String? = null,
        val versionId: String = LEGACY_MOD_VERSION_ID,
        val versionUpdatedAtMillis: Long? = null,
        val file: ExportedDownloadFile,
    )

    private fun parseLegacyRelativePath(path: String): ParsedModPath? {
        val normalized = path.replace('\\', '/').trimStart('/')
        val segments = normalized.split('/').filter(String::isNotBlank)
        if (segments.size < 5 || segments[1] != "workshop") {
            return null
        }

        val appId = segments[2].toUIntOrNull() ?: return null
        val publishedFileId = segments[3].toULongOrNull() ?: return null
        val parsedVersion = if (segments.size >= 8) {
            ParsedModPath(
                appId = appId,
                publishedFileId = publishedFileId,
                versionId = normalizeModVersionId(segments[4]),
                versionUpdatedAtMillis = parseModVersionUpdatedAtMillis(normalizeModVersionId(segments[4])),
                gameTitle = segments[5],
                itemTitle = segments[6],
                fileRelativePath = segments.drop(7).joinToString("/").ifBlank { return null },
            )
        } else {
            ParsedModPath(
                appId = appId,
                publishedFileId = publishedFileId,
                fileRelativePath = segments.drop(4).joinToString("/").ifBlank { return null },
            )
        }
        return parsedVersion
    }

    private fun parseLegacyFileSystemPath(
        root: File,
        file: File,
    ): ParsedModPath? {
        val relativePath = file.relativeTo(root).invariantSeparatorsPath
        val segments = relativePath.split('/').filter(String::isNotBlank)
        if (segments.size < 3) {
            return null
        }

        val appId = segments[0].toUIntOrNull() ?: return null
        val publishedFileId = segments[1].toULongOrNull() ?: return null
        return if (segments.size >= 6) {
            val versionId = normalizeModVersionId(segments[2])
            ParsedModPath(
                appId = appId,
                publishedFileId = publishedFileId,
                versionId = versionId,
                versionUpdatedAtMillis = parseModVersionUpdatedAtMillis(versionId),
                gameTitle = segments[3],
                itemTitle = segments[4],
                fileRelativePath = segments.drop(5).joinToString("/").ifBlank { return null },
            )
        } else {
            ParsedModPath(
                appId = appId,
                publishedFileId = publishedFileId,
                fileRelativePath = segments.drop(2).joinToString("/").ifBlank { return null },
            )
        }
    }
}

