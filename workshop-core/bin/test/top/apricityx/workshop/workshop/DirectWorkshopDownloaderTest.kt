package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DirectWorkshopDownloaderTest {
    @Test
    fun download_resumesFromPartialFile() {
        runBlocking {
        val server = MockWebServer()
        server.enqueue(mockResponse("world", code = 206))
        server.start()

        val tempDir = Files.createTempDirectory("direct-resume").toFile()
        val partialFile = File(tempDir, "mod.bin.part").apply { writeText("hello ") }
        val outputFile = File(tempDir, "mod.bin")
        val events = mutableListOf<DownloadEvent>()
        val downloader = DirectWorkshopDownloader(OkHttpClient())

        downloader.download(
            request = WorkshopDownloadRequest(
                appId = 1u,
                publishedFileId = 2uL,
                outputDir = tempDir,
            ),
            item = ResolvedWorkshopItem.DirectUrlItem(
                fileName = "mod.bin",
                fileUrl = server.url("/mod.bin").toString(),
                size = 11L,
                title = "Mod",
                metadataJson = "{}",
            ),
            emit = { events += it },
            log = {},
        )

        val request = server.takeRequest()
        assertThat(request.headers["Range"]).isEqualTo("bytes=6-")
        assertThat(outputFile.readText()).isEqualTo("hello world")
        assertThat(partialFile.exists()).isFalse()
        assertThat(events.filterIsInstance<DownloadEvent.FileCompleted>()).hasSize(1)

        server.close()
        tempDir.deleteRecursively()
        }
    }

    @Test
    fun download_reusesCompletedFileWithoutNetwork() {
        runBlocking {
        val tempDir = Files.createTempDirectory("direct-reuse").toFile()
        val outputFile = File(tempDir, "mod.bin").apply { writeText("done") }
        val events = mutableListOf<DownloadEvent>()
        val downloader = DirectWorkshopDownloader(OkHttpClient())

        downloader.download(
            request = WorkshopDownloadRequest(
                appId = 1u,
                publishedFileId = 2uL,
                outputDir = tempDir,
            ),
            item = ResolvedWorkshopItem.DirectUrlItem(
                fileName = "mod.bin",
                fileUrl = "https://example.invalid/mod.bin",
                size = 4L,
                title = "Mod",
                metadataJson = "{}",
            ),
            emit = { events += it },
            log = {},
        )

        assertThat(outputFile.readText()).isEqualTo("done")
        assertThat(events.filterIsInstance<DownloadEvent.FileCompleted>()).hasSize(1)
        tempDir.deleteRecursively()
        }
    }
}

private fun mockResponse(
    body: String,
    code: Int = 200,
): MockResponse =
    MockResponse.Builder()
        .code(code)
        .body(body)
        .build()
