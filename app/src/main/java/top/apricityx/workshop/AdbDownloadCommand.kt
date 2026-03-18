package top.apricityx.workshop

import android.content.Intent

sealed interface AdbCommand

data class AdbDownloadCommand(
    val appIdText: String,
    val publishedFileIdText: String,
    val autoStart: Boolean,
) : AdbCommand

data class AdbWorkshopSearchProbeCommand(
    val appIdText: String,
    val searchQuery: String,
    val expectedPublishedFileIdText: String,
) : AdbCommand

object AdbDownloadCommandParser {
    const val actionDownload = WorkshopAppContract.adbDownloadAction
    const val actionWorkshopSearchProbe = WorkshopAppContract.adbWorkshopSearchProbeAction

    private val appIdKeys = listOf("app_id", "appId", "appid")
    private val publishedFileIdKeys = listOf("published_file_id", "publishedFileId", "published_field_id", "publishedFieldId")
    private val autoStartKeys = listOf("auto_start", "autostart")
    private val searchQueryKeys = listOf("search_query", "searchQuery", "query", "q")
    private val expectedPublishedFileIdKeys = listOf(
        "expected_published_file_id",
        "expectedPublishedFileId",
        "probe_published_file_id",
        "probePublishedFileId",
    )

    fun parse(intent: Intent?): AdbCommand? {
        val extras = intent?.extras ?: return null
        @Suppress("DEPRECATION")
        val rawExtras = extras.keySet().associateWith(extras::get)
        return parse(intent.action, rawExtras)
    }

    fun parse(action: String?, extras: Map<String, Any?>): AdbCommand? {
        val appIdText = extras.findValue(appIdKeys)
        val searchQuery = extras.findValue(searchQueryKeys)
        if (!appIdText.isNullOrBlank() && !searchQuery.isNullOrBlank()) {
            return AdbWorkshopSearchProbeCommand(
                appIdText = appIdText,
                searchQuery = searchQuery,
                expectedPublishedFileIdText = extras.findValue(expectedPublishedFileIdKeys).orEmpty(),
            )
        }

        val publishedFileIdText = extras.findValue(publishedFileIdKeys)
        if (appIdText == null && publishedFileIdText == null) {
            return null
        }

        return AdbDownloadCommand(
            appIdText = appIdText.orEmpty(),
            publishedFileIdText = publishedFileIdText.orEmpty(),
            autoStart = action == actionDownload || extras.findBoolean(autoStartKeys),
        )
    }

    private fun Map<String, Any?>.findValue(keys: List<String>): String? {
        val key = keys.firstOrNull(::containsKey) ?: return null
        return this[key]?.toString()?.trim()
    }

    private fun Map<String, Any?>.findBoolean(keys: List<String>): Boolean {
        val key = keys.firstOrNull(::containsKey) ?: return false
        return when (val value = this[key]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            is Number -> value.toInt() != 0
            else -> false
        }
    }
}
