package top.apricityx.workshop.workshop

import top.apricityx.workshop.steam.protocol.CdnServer
import top.apricityx.workshop.steam.protocol.OkHttpSteamCmSession
import top.apricityx.workshop.steam.protocol.SteamCmSession
import top.apricityx.workshop.steam.protocol.SteamContentClient
import top.apricityx.workshop.steam.protocol.SteamDirectoryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

class UgcWorkshopDownloader(
    private val client: OkHttpClient,
    private val directoryClient: SteamDirectoryClient,
    private val maxConcurrentChunks: Int = DEFAULT_MAX_CONCURRENT_CHUNKS,
    private val bypassSteamCmWebSocket: Boolean = false,
    private val sessionFactory: () -> SteamCmSession = { OkHttpSteamCmSession(client) },
    private val sessionConnector: suspend (SteamCmSession, List<top.apricityx.workshop.steam.protocol.CmServer>) -> top.apricityx.workshop.steam.protocol.SessionContext =
        { session, servers -> session.connectAnonymous(servers) },
    private val allowPublicCdnFallbackOnSessionFailure: Boolean = true,
) {
    suspend fun download(
        request: WorkshopDownloadRequest,
        item: ResolvedWorkshopItem.UgcManifestItem,
        emit: suspend (DownloadEvent) -> Unit,
        log: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        log("Loading Steam CM websocket candidates")
        val cmServers = directoryClient.loadServers()
        log("Loaded ${cmServers.size} CM websocket candidates")

        sessionFactory().use { session ->
            val contentClient = SteamContentClient(session, directoryClient)

            if (bypassSteamCmWebSocket) {
                log("Compatibility mode enabled; skipping Steam CM websocket and using public CDN flow")
            } else {
                val connectResult = runCatching { sessionConnector(session, cmServers) }
                connectResult
                    .onSuccess { log("Connected to Steam CM cell=${it.cellId} steamId=${it.steamId}") }
                    .onFailure {
                        if (allowPublicCdnFallbackOnSessionFailure) {
                            log("Steam CM connection failed, continuing with public CDN flow: ${it.message}")
                        } else {
                            throw it
                        }
                    }
            }

            val manifestRequestCode = runCatching {
                contentClient.getManifestRequestCode(
                    appId = request.appId,
                    depotId = item.depotId,
                    manifestId = item.manifestId,
                )
            }.getOrElse {
                log("Manifest request code unavailable, retrying without request code: ${it.message}")
                0uL
            }

            val contentServers = runCatching {
                contentClient.getServersForSteamPipe()
            }.getOrElse {
                log("Falling back to public content server directory API")
                directoryClient.loadContentServers()
            }.sortedBy { it.weightedLoad }

            require(contentServers.isNotEmpty()) { "No CDN servers available for SteamPipe" }
            log("Loaded ${contentServers.size} SteamPipe content servers")

            val depotKey = runCatching {
                session.requestDepotDecryptionKey(
                    appId = request.appId,
                    depotId = item.depotId,
                )
            }.onSuccess {
                log("Loaded depot key for depot=${item.depotId}")
            }.onFailure {
                log("Depot key request failed for depot=${item.depotId}: ${it.message}")
            }.getOrNull()

            val manifest = downloadManifest(
                appId = request.appId,
                item = item,
                contentServers = contentServers,
                manifestRequestCode = manifestRequestCode,
                contentClient = contentClient,
                log = log,
            )

            emit(DownloadEvent.StateChanged(DownloadState.Downloading))
            val chunks = manifest.uniqueChunks()
            val totalBytes = chunks.sumOf { it.uncompressedLength.toLong() }
            val totalFiles = manifest.files.count { it.linkTarget.isNullOrBlank() }
            log("Manifest ${manifest.manifestId} contains ${manifest.files.size} files and ${chunks.size} unique chunks")
            emit(
                DownloadEvent.Progress(
                    writtenBytes = 0L,
                    totalBytes = totalBytes,
                    completedChunks = 0,
                    totalChunks = chunks.size,
                    completedFiles = 0,
                    totalFiles = totalFiles,
                ),
            )

            val stageDir = File(request.outputDir, ".chunks").apply { mkdirs() }
            cacheChunks(
                appId = request.appId,
                depotId = item.depotId,
                contentServers = contentServers,
                contentClient = contentClient,
                chunks = chunks,
                stageDir = stageDir,
                depotKey = depotKey,
                totalFiles = totalFiles,
                emit = emit,
                log = log,
            )

            assembleFiles(
                manifest = manifest,
                outputDir = request.outputDir,
                stageDir = stageDir,
                totalBytes = totalBytes,
                totalChunks = chunks.size,
                totalFiles = totalFiles,
                emit = emit,
                log = log,
            )
        }
    }

    private suspend fun downloadManifest(
        appId: UInt,
        item: ResolvedWorkshopItem.UgcManifestItem,
        contentServers: List<CdnServer>,
        manifestRequestCode: ULong,
        contentClient: SteamContentClient,
        log: suspend (String) -> Unit,
    ): DepotManifest {
        var lastError: Throwable? = null
        for (server in contentServers) {
            try {
                log("Trying manifest download from ${server.host}")
                val path = buildString {
                    append("depot/${item.depotId}/manifest/${item.manifestId}/5")
                    if (manifestRequestCode > 0uL) {
                        append("/$manifestRequestCode")
                    }
                }
                val bytes = requestZipBytes(server, path, appId, item.depotId, contentClient)
                return DepotManifestParser.parse(unzipSingleEntry(bytes))
            } catch (error: Throwable) {
                lastError = error
                log("Manifest download failed from ${server.host}: ${error.message}")
            }
        }
        throw WorkshopDownloadException("Unable to download UGC manifest", lastError)
    }

    private suspend fun cacheChunks(
        appId: UInt,
        depotId: UInt,
        contentServers: List<CdnServer>,
        contentClient: SteamContentClient,
        chunks: List<ManifestChunk>,
        stageDir: File,
        depotKey: ByteArray?,
        totalFiles: Int,
        emit: suspend (DownloadEvent) -> Unit,
        log: suspend (String) -> Unit,
    ) = coroutineScope {
        val semaphore = Semaphore(maxConcurrentChunks.coerceAtLeast(1))
        val totalBytes = chunks.sumOf { it.uncompressedLength.toLong() }
        val downloaded = java.util.concurrent.atomic.AtomicLong(0L)
        val completedChunks = AtomicInteger(0)
        val totalChunks = chunks.size

        chunks.map { chunk ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val stageFile = File(stageDir, "${chunk.idHex}.chunk")
                    if (tryReuseCachedChunk(stageFile, chunk, downloaded, completedChunks, totalBytes, totalChunks, totalFiles, emit)) {
                        return@withPermit
                    }

                    val processed = downloadChunkWithRetries(
                        appId = appId,
                        depotId = depotId,
                        contentServers = contentServers,
                        contentClient = contentClient,
                        chunk = chunk,
                        depotKey = depotKey,
                        log = log,
                    )
                    writeAtomically(stageFile, processed)
                    emit(
                        DownloadEvent.Progress(
                            writtenBytes = downloaded.addAndGet(processed.size.toLong()),
                            totalBytes = totalBytes,
                            completedChunks = completedChunks.incrementAndGet(),
                            totalChunks = totalChunks,
                            completedFiles = 0,
                            totalFiles = totalFiles,
                        ),
                    )
                }
            }
        }.awaitAll()
    }

    private suspend fun tryReuseCachedChunk(
        stageFile: File,
        chunk: ManifestChunk,
        downloaded: java.util.concurrent.atomic.AtomicLong,
        completedChunks: AtomicInteger,
        totalBytes: Long,
        totalChunks: Int,
        totalFiles: Int,
        emit: suspend (DownloadEvent) -> Unit,
    ): Boolean {
        if (!stageFile.exists()) {
            return false
        }

        if (!validateChunkFile(stageFile, chunk)) {
            stageFile.delete()
            return false
        }

        emit(
            DownloadEvent.Progress(
                writtenBytes = downloaded.addAndGet(stageFile.length()),
                totalBytes = totalBytes,
                completedChunks = completedChunks.incrementAndGet(),
                totalChunks = totalChunks,
                completedFiles = 0,
                totalFiles = totalFiles,
            ),
        )
        return true
    }

    private suspend fun downloadChunkWithRetries(
        appId: UInt,
        depotId: UInt,
        contentServers: List<CdnServer>,
        contentClient: SteamContentClient,
        chunk: ManifestChunk,
        depotKey: ByteArray?,
        log: suspend (String) -> Unit,
    ): ByteArray {
        var lastError: Throwable? = null

        for (attempt in 1..MAX_CHUNK_DOWNLOAD_ATTEMPTS) {
            for (server in rotateServers(contentServers, attempt - 1)) {
                try {
                    val path = "depot/$depotId/chunk/${chunk.idHex}"
                    val raw = requestBytes(
                        server = server,
                        path = path,
                        query = null,
                        appId = appId,
                        depotId = depotId,
                        contentClient = contentClient,
                    )
                    return ChunkProcessor.process(raw, chunk, depotKey)
                } catch (error: Throwable) {
                    lastError = error
                    log("Chunk ${chunk.idHex} failed from ${server.host}: ${error.message}")
                }
            }

            if (attempt < MAX_CHUNK_DOWNLOAD_ATTEMPTS) {
                log("Retrying chunk ${chunk.idHex} (${attempt + 1}/$MAX_CHUNK_DOWNLOAD_ATTEMPTS)")
                delay(CHUNK_RETRY_DELAY_MILLIS * attempt)
            }
        }

        throw WorkshopDownloadException("Failed to download chunk ${chunk.idHex}", lastError)
    }

    private suspend fun assembleFiles(
        manifest: DepotManifest,
        outputDir: File,
        stageDir: File,
        totalBytes: Long,
        totalChunks: Int,
        totalFiles: Int,
        emit: suspend (DownloadEvent) -> Unit,
        log: suspend (String) -> Unit,
    ) {
        var completedFiles = 0
        manifest.files.forEach { file ->
            if (!file.linkTarget.isNullOrBlank()) {
                log("Skipping symlink-like manifest entry ${file.path} -> ${file.linkTarget}")
                return@forEach
            }

            val target = File(outputDir, file.path.replace('/', File.separatorChar))
            target.parentFile?.mkdirs()

            val reuse = target.exists() && target.length() == file.size && validateFileSha(target, file.shaContent)
            if (!reuse) {
                RandomAccessFile(target, "rw").use { output ->
                    output.setLength(file.size)
                    file.chunks.forEach { chunk ->
                        val chunkFile = File(stageDir, "${chunk.idHex}.chunk")
                        output.seek(chunk.offset)
                        chunkFile.inputStream().buffered().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) {
                                    break
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }

                if (file.shaContent.isNotEmpty() && !validateFileSha(target, file.shaContent)) {
                    throw WorkshopDownloadException("Assembled file checksum mismatch for ${file.path}")
                }
            }

            emit(
                DownloadEvent.FileCompleted(
                    DownloadedFileInfo(
                        relativePath = file.path,
                        sizeBytes = target.length(),
                        modifiedEpochMillis = target.lastModified(),
                    ),
                ),
            )
            completedFiles += 1
            emit(
                DownloadEvent.Progress(
                    writtenBytes = totalBytes,
                    totalBytes = totalBytes,
                    completedChunks = totalChunks,
                    totalChunks = totalChunks,
                    completedFiles = completedFiles,
                    totalFiles = totalFiles,
                ),
            )
        }
    }

    private suspend fun requestZipBytes(
        server: CdnServer,
        path: String,
        appId: UInt,
        depotId: UInt,
        contentClient: SteamContentClient,
    ): ByteArray = requestBytes(server, path, null, appId, depotId, contentClient)

    private suspend fun requestBytes(
        server: CdnServer,
        path: String,
        query: String?,
        appId: UInt,
        depotId: UInt,
        contentClient: SteamContentClient,
    ): ByteArray {
        var currentQuery = query
        repeat(2) { attempt ->
            val request = Request.Builder().url(buildServerUrl(server, path, currentQuery)).build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> return response.body?.bytes() ?: ByteArray(0)
                    response.code == 403 && attempt == 0 -> {
                        currentQuery = contentClient.getCdnAuthToken(appId, depotId, server.host).token
                    }
                    else -> throw WorkshopDownloadException("Steam CDN request failed: ${response.code}")
                }
            }
        }
        throw WorkshopDownloadException("Steam CDN request exhausted retries")
    }

    private fun buildServerUrl(server: CdnServer, path: String, query: String?): HttpUrl {
        return HttpUrl.Builder()
            .scheme(server.secureScheme)
            .host(server.vHost)
            .port(server.port)
            .addEncodedPathSegments(path)
            .apply {
                if (!query.isNullOrBlank()) {
                    encodedQuery(query)
                }
            }
            .build()
    }

    private fun unzipSingleEntry(zipBytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            val entry = zip.nextEntry ?: throw WorkshopDownloadException("Zip payload was empty")
            val output = ByteArrayOutputStream()
            zip.copyTo(output)
            zip.closeEntry()
            return output.toByteArray()
        }
    }

    private fun validateChunkFile(file: File, chunk: ManifestChunk): Boolean {
        if (!file.isFile || file.length() != chunk.uncompressedLength.toLong()) {
            return false
        }
        file.inputStream().buffered().use { input ->
            val checksum = steamAdler32(input)
            return checksum == chunk.checksum
        }
    }

    private fun validateFileSha(file: File, expectedSha: ByteArray): Boolean {
        if (expectedSha.isEmpty()) {
            return true
        }
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest()
        return actual.contentEquals(expectedSha)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun rotateServers(
        servers: List<CdnServer>,
        offset: Int,
    ): List<CdnServer> {
        if (servers.isEmpty()) {
            return emptyList()
        }
        return List(servers.size) { index ->
            servers[(index + offset) % servers.size]
        }
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT_CHUNKS = 4
        private const val MAX_CHUNK_DOWNLOAD_ATTEMPTS = 3
        private const val CHUNK_RETRY_DELAY_MILLIS = 750L
    }
}
