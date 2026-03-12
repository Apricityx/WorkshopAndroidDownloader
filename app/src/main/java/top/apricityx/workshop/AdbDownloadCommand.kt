package top.apricityx.workshop

import android.content.Intent

data class AdbDownloadCommand(
    val appIdText: String,
    val publishedFileIdText: String,
    val autoStart: Boolean,
)

object AdbDownloadCommandParser {
    const val actionDownload = WorkshopAppContract.adbDownloadAction

    private val appIdKeys = listOf("app_id", "appId", "appid")
    private val publishedFileIdKeys = listOf("published_file_id", "publishedFileId", "published_field_id", "publishedFieldId")
    private val autoStartKeys = listOf("auto_start", "autostart")

    fun parse(intent: Intent?): AdbDownloadCommand? {
        val extras = intent?.extras ?: return null
        @Suppress("DEPRECATION")
        val rawExtras = extras.keySet().associateWith(extras::get)
        return parse(intent.action, rawExtras)
    }

    fun parse(action: String?, extras: Map<String, Any?>): AdbDownloadCommand? {
        val appIdText = extras.findValue(appIdKeys)
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
