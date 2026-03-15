package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Test
import top.apricityx.workshop.steam.protocol.SteamDirectoryClient

class WorkshopDownloadEngineTest {
    @Test
    fun download_ignores_stale_files_left_in_output_directory() {
        runBlocking {
            val server = MockWebServer()
            server.start()
            val downloadUrl = server.url("/mod.jar").toString()
            server.enqueue(
                mockEngineResponse(
                    """
                    {
                      "response": {
                        "publishedfiledetails": [
                          {
                            "result": 1,
                            "title": "Test Mod",
                            "filename": "mod.jar",
                            "file_size": 11,
                            "file_url": "$downloadUrl"
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(mockEngineResponse("hello world"))

            val client = OkHttpClient()
            val tempDir = Files.createTempDirectory("workshop-engine").toFile()
            File(tempDir, "stale/garbled.bin").apply {
                parentFile?.mkdirs()
                writeText("stale")
            }
            val events = mutableListOf<DownloadEvent>()
            val engine = WorkshopDownloadEngine(
                resolver = PublishedFileResolver(client, baseUrl = server.url("/")),
                directDownloader = DirectWorkshopDownloader(client),
                ugcWorkshopDownloader = UgcWorkshopDownloader(
                    client = client,
                    directoryClient = SteamDirectoryClient(client),
                ),
            )

            engine.download(
                WorkshopDownloadRequest(
                    appId = 1u,
                    publishedFileId = 2uL,
                    outputDir = tempDir,
                ),
            ).collect(events::add)

            val completed = events.filterIsInstance<DownloadEvent.Completed>().single()
            assertThat(completed.files.map(DownloadedFileInfo::relativePath)).containsExactly("mod.jar")
            assertThat(File(tempDir, "mod.jar").readText()).isEqualTo("hello world")
            assertThat(File(tempDir, "stale/garbled.bin").exists()).isTrue()

            server.close()
            tempDir.deleteRecursively()
        }
    }
}

private fun mockEngineResponse(
    body: String,
    code: Int = 200,
): MockResponse =
    MockResponse.Builder()
        .code(code)
        .body(body)
        .build()
