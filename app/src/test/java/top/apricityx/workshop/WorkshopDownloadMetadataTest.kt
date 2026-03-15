package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import org.junit.Test

class WorkshopDownloadMetadataTest {
    @Test
    fun readWorkshopDownloadMetadata_parsesTitleAndPreviewUrl() {
        val stagingDir = Files.createTempDirectory("workshop-metadata").toFile()
        stagingDir.resolve("metadata.json").writeText(
            """
            {
              "response": {
                "publishedfiledetails": [
                  {
                    "title": "Exact Workshop Title",
                    "filename": "mod.jar",
                    "preview_url": "https://cdn.example.com/preview.webp",
                    "time_updated": "1772900923"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val metadata = readWorkshopDownloadMetadata(stagingDir)

        assertThat(metadata?.title).isEqualTo("Exact Workshop Title")
        assertThat(metadata?.filename).isEqualTo("mod.jar")
        assertThat(metadata?.previewImageUrl).isEqualTo("https://cdn.example.com/preview.webp")
        assertThat(metadata?.timeUpdatedEpochSeconds).isEqualTo(1772900923L)
        stagingDir.deleteRecursively()
    }
}
