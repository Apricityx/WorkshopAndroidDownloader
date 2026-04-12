package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.thread
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SteamWorkshopLiveSearchTest {
    @Test
    fun `direct workshop browse search returns matching mods`() {
        assumeTrue(
            "Live Steam Workshop test disabled. Enable with -Dworkshop.live.search=true " +
                "or WORKSHOP_LIVE_SEARCH=true.",
            isLiveWorkshopSearchEnabled(),
        )

        val payload = fetchWorkshopBrowsePageWithPowerShell()
        val hoverItems = parseHoverItems(payload)

        assertThat(payload).contains("""class="workshopItem"""")
        assertThat(hoverItems).isNotEmpty()
        assertThat(hoverItems.all { it.appId == SLAY_THE_SPIRE_APP_ID }).isTrue()
        assertThat(hoverItems.any { it.title.isNotBlank() }).isTrue()
        assertThat(
            hoverItems.any { item ->
                item.title.contains(SEARCH_QUERY, ignoreCase = true) ||
                    item.description.contains(SEARCH_QUERY, ignoreCase = true)
            },
        ).isTrue()
    }

    private fun parseHoverItems(payload: String): List<WorkshopHoverItem> =
        hoverRegex.findAll(payload)
            .mapNotNull { match ->
                runCatching {
                    val jsonObject = json.parseToJsonElement(match.groupValues[2]).jsonObject
                    WorkshopHoverItem(
                        publishedFileId = match.groupValues[1].toULong(),
                        appId = jsonObject.getValue("appid").jsonPrimitive.content.toUInt(),
                        title = jsonObject.getValue("title").jsonPrimitive.content,
                        description = jsonObject.getValue("description").jsonPrimitive.content,
                    )
                }.getOrNull()
            }
            .toList()

    private fun isLiveWorkshopSearchEnabled(): Boolean =
        System.getProperty(LIVE_TEST_SYSTEM_PROPERTY)?.toBooleanStrictOrNull() == true ||
            System.getenv(LIVE_TEST_ENVIRONMENT_VARIABLE)?.toBooleanStrictOrNull() == true

    private fun fetchWorkshopBrowsePageWithPowerShell(): String {
        // Use the host PowerShell 7 HTTP stack to verify bare-machine connectivity.
        val process = ProcessBuilder(
            "pwsh",
            "-NoProfile",
            "-Command",
            powerShellFetchScript,
        )
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        val readerThread = thread(name = "powershell-workshop-probe-reader") {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    output.appendLine(line)
                }
            }
        }

        if (!process.waitFor(LIVE_PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("PowerShell 7 Steam Workshop probe timed out after $LIVE_PROCESS_TIMEOUT_SECONDS seconds.")
        }

        readerThread.join(LIVE_OUTPUT_DRAIN_TIMEOUT_MILLIS)
        val stdout = output.toString()

        if (process.exitValue() != 0) {
            error(
                buildString {
                    append("PowerShell Steam Workshop probe failed with exit code ")
                    append(process.exitValue())
                    if (stdout.isNotBlank()) {
                        append(": ")
                        append(stdout.trim())
                    }
                },
            )
        }

        return stdout
    }

    private data class WorkshopHoverItem(
        val publishedFileId: ULong,
        val appId: UInt,
        val title: String,
        val description: String,
    )

    private companion object {
        private const val LIVE_TEST_SYSTEM_PROPERTY = "workshop.live.search"
        private const val LIVE_TEST_ENVIRONMENT_VARIABLE = "WORKSHOP_LIVE_SEARCH"
        private const val LIVE_PROCESS_TIMEOUT_SECONDS = 45L
        private const val LIVE_OUTPUT_DRAIN_TIMEOUT_MILLIS = 5_000L
        private const val SEARCH_QUERY = "basemod"
        private const val USER_AGENT = "WorkshopOnAndroid/1.0"
        private val SLAY_THE_SPIRE_APP_ID = 646570u
        private const val requestUrl =
            "https://steamcommunity.com/workshop/browse/" +
                "?appid=646570" +
                "&searchtext=basemod" +
                "&childpublishedfileid=0" +
                "&l=english" +
                "&browsesort=trend" +
                "&section=readytouseitems" +
                "&actualsort=trend" +
                "&days=3650" +
                "&p=1" +
                "&numperpage=30"
        private val powerShellFetchScript =
            """
            ${'$'}ProgressPreference='SilentlyContinue'
            (Invoke-WebRequest -Uri '$requestUrl' -Headers @{ 'User-Agent'='$USER_AGENT' }).Content
            """.trimIndent()
        private val json = Json { ignoreUnknownKeys = true }
        private val hoverRegex = Regex(
            """SharedFileBindMouseHover\(\s*"sharedfile_(\d+)"\s*,\s*false\s*,\s*(\{.*?\})\s*\);""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
