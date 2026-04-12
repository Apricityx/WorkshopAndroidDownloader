package top.apricityx.workshop.workshop

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.thread
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SteamWorkshopSteamppLikeLiveTest {
    @Test
    fun `steampp-like doh and host override can browse workshop search`() {
        assumeTrue(
            "Steam++-like live Steam Workshop test disabled. Enable with -Dworkshop.live.steampp=true " +
                "or WORKSHOP_LIVE_STEAMPP=true.",
            isSteamppLikeLiveSearchEnabled(),
        )

        val payload = fetchWorkshopBrowsePageWithSteamppLikeStrategy()
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

    private fun isSteamppLikeLiveSearchEnabled(): Boolean =
        System.getProperty(LIVE_TEST_SYSTEM_PROPERTY)?.toBooleanStrictOrNull() == true ||
            System.getenv(LIVE_TEST_ENVIRONMENT_VARIABLE)?.toBooleanStrictOrNull() == true

    private fun fetchWorkshopBrowsePageWithSteamppLikeStrategy(): String {
        val attemptLog = mutableListOf<String>()
        val candidateIps = linkedSetOf<String>()

        dohResolvers.forEach { resolver ->
            val resolvedIps = resolveIpv4CandidatesWithDoh(resolver)
            attemptLog += "${resolver.name} resolved ${resolvedIps.ifEmpty { listOf("<empty>") }.joinToString()}"
            candidateIps += resolvedIps
        }

        if (candidateIps.isEmpty()) {
            error("Steam++-like probe did not resolve any candidate IPs. Attempts: ${attemptLog.joinToString(" | ")}")
        }

        candidateIps.forEach { ipAddress ->
            val probe = runProcess(
                listOf(
                    "curl.exe",
                    "-fsSL",
                    "--connect-timeout",
                    CURL_CONNECT_TIMEOUT_SECONDS.toString(),
                    "--resolve",
                    "steamcommunity.com:443:$ipAddress",
                    "-A",
                    USER_AGENT,
                    REQUEST_URL,
                ),
                PROCESS_TIMEOUT_SECONDS,
            )
            if (probe.exitCode == 0 && probe.output.contains("""class="workshopItem"""")) {
                return probe.output
            }
            val outputSnippet = probe.output.trim().lineSequence().take(8).joinToString(" | ")
            attemptLog += "curl via $ipAddress exit=${probe.exitCode} output=$outputSnippet"
        }

        error(
            "Steam++-like probe failed after trying ${candidateIps.size} candidate IPs. " +
                "Attempts: ${attemptLog.joinToString(" || ")}",
        )
    }

    private fun resolveIpv4CandidatesWithDoh(resolver: DohResolver): List<String> {
        val probe = runProcess(
            listOf(
                "pwsh",
                "-NoProfile",
                "-Command",
                """
                ${'$'}ProgressPreference='SilentlyContinue'
                ${'$'}result = Invoke-RestMethod -Uri '${resolver.resolveUrl}?name=steamcommunity.com&type=A'
                if (${ '$' }result.Answer) {
                    ${ '$' }result.Answer | ForEach-Object { if (${ '$' }_.data) { ${ '$' }_.data } }
                }
                """.trimIndent(),
            ),
            PROCESS_TIMEOUT_SECONDS,
        )
        if (probe.exitCode != 0) {
            return emptyList()
        }
        return probe.output.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && ipv4Regex.matches(it) }
            .distinct()
            .toList()
    }

    private fun runProcess(
        command: List<String>,
        timeoutSeconds: Long,
    ): ProcessResult {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        val readerThread = thread(name = "steam-workshop-live-reader") {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    output.appendLine(line)
                }
            }
        }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            readerThread.join(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            return ProcessResult(exitCode = -1, output = "timed out after ${timeoutSeconds}s")
        }

        readerThread.join(OUTPUT_DRAIN_TIMEOUT_MILLIS)
        return ProcessResult(
            exitCode = process.exitValue(),
            output = output.toString(),
        )
    }

    private data class ProcessResult(
        val exitCode: Int,
        val output: String,
    )

    private data class DohResolver(
        val name: String,
        val resolveUrl: String,
    )

    private data class WorkshopHoverItem(
        val publishedFileId: ULong,
        val appId: UInt,
        val title: String,
        val description: String,
    )

    private companion object {
        private const val LIVE_TEST_SYSTEM_PROPERTY = "workshop.live.steampp"
        private const val LIVE_TEST_ENVIRONMENT_VARIABLE = "WORKSHOP_LIVE_STEAMPP"
        private const val PROCESS_TIMEOUT_SECONDS = 30L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 5_000L
        private const val CURL_CONNECT_TIMEOUT_SECONDS = 12L
        private const val SEARCH_QUERY = "basemod"
        private const val USER_AGENT = "WorkshopOnAndroid/1.0"
        private const val REQUEST_URL =
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
        private val SLAY_THE_SPIRE_APP_ID = 646570u
        private val json = Json { ignoreUnknownKeys = true }
        private val ipv4Regex = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
        private val hoverRegex = Regex(
            """SharedFileBindMouseHover\(\s*"sharedfile_(\d+)"\s*,\s*false\s*,\s*(\{.*?\})\s*\);""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        private val dohResolvers = listOf(
            DohResolver(name = "DNSPod", resolveUrl = "https://doh.pub/resolve"),
            DohResolver(name = "AliDNS", resolveUrl = "https://dns.alidns.com/resolve"),
        )
    }
}
