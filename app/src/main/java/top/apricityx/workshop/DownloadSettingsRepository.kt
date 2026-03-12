package top.apricityx.workshop

import android.content.Context
import top.apricityx.workshop.update.UpdateSource

class DownloadSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDownloadThreadCount(): Int =
        prefs.getInt(KEY_DOWNLOAD_THREADS, DEFAULT_DOWNLOAD_THREADS)

    fun setDownloadThreadCount(value: Int) {
        prefs.edit().putInt(KEY_DOWNLOAD_THREADS, value).apply()
    }

    fun getConcurrentDownloadTaskCount(): Int =
        prefs.getInt(KEY_CONCURRENT_DOWNLOAD_TASKS, DEFAULT_CONCURRENT_DOWNLOAD_TASKS)

    fun setConcurrentDownloadTaskCount(value: Int) {
        prefs.edit().putInt(KEY_CONCURRENT_DOWNLOAD_TASKS, value).apply()
    }

    fun getThemeMode(): AppThemeMode =
        prefs.getString(KEY_THEME_MODE, null)
            ?.let(AppThemeMode::fromStorageValue)
            ?: DEFAULT_THEME_MODE

    fun setThemeMode(value: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, value.storageValue).apply()
    }

    fun isAutoCheckUpdatesEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_CHECK_UPDATES_ENABLED, DEFAULT_AUTO_CHECK_UPDATES_ENABLED)

    fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES_ENABLED, enabled).apply()
    }

    fun getPreferredUpdateSource(): UpdateSource =
        UpdateSource.normalizePreferredUserSource(prefs.getString(KEY_PREFERRED_UPDATE_SOURCE_ID, null))

    fun setPreferredUpdateSource(source: UpdateSource) {
        prefs.edit().putString(KEY_PREFERRED_UPDATE_SOURCE_ID, source.id).apply()
    }

    fun getLastUpdateCheckAtMs(): Long =
        prefs.getLong(KEY_LAST_UPDATE_CHECK_AT_MS, 0L)

    fun setLastUpdateCheckAtMs(value: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT_MS, value).apply()
    }

    fun getLastKnownRemoteTag(): String? =
        prefs.getString(KEY_LAST_KNOWN_REMOTE_TAG, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setLastKnownRemoteTag(value: String?) {
        prefs.edit().putString(KEY_LAST_KNOWN_REMOTE_TAG, value?.trim()).apply()
    }

    fun getLastSuccessfulMetadataSourceId(): String? =
        prefs.getString(KEY_LAST_SUCCESSFUL_METADATA_SOURCE_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setLastSuccessfulMetadataSourceId(value: String?) {
        prefs.edit().putString(KEY_LAST_SUCCESSFUL_METADATA_SOURCE_ID, value?.trim()).apply()
    }

    fun getLastSuccessfulDownloadSourceId(): String? =
        prefs.getString(KEY_LAST_SUCCESSFUL_DOWNLOAD_SOURCE_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setLastSuccessfulDownloadSourceId(value: String?) {
        prefs.edit().putString(KEY_LAST_SUCCESSFUL_DOWNLOAD_SOURCE_ID, value?.trim()).apply()
    }

    fun getLastUpdateErrorSummary(): String? =
        prefs.getString(KEY_LAST_UPDATE_ERROR_SUMMARY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setLastUpdateErrorSummary(value: String?) {
        prefs.edit().putString(KEY_LAST_UPDATE_ERROR_SUMMARY, value?.trim()).apply()
    }

    companion object {
        private const val PREFS_NAME = "download_settings"
        private const val KEY_DOWNLOAD_THREADS = "download_threads"
        private const val KEY_CONCURRENT_DOWNLOAD_TASKS = "concurrent_download_tasks"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AUTO_CHECK_UPDATES_ENABLED = "auto_check_updates_enabled"
        private const val KEY_PREFERRED_UPDATE_SOURCE_ID = "preferred_update_source_id"
        private const val KEY_LAST_UPDATE_CHECK_AT_MS = "last_update_check_at_ms"
        private const val KEY_LAST_KNOWN_REMOTE_TAG = "last_known_remote_tag"
        private const val KEY_LAST_SUCCESSFUL_METADATA_SOURCE_ID = "last_successful_metadata_source_id"
        private const val KEY_LAST_SUCCESSFUL_DOWNLOAD_SOURCE_ID = "last_successful_download_source_id"
        private const val KEY_LAST_UPDATE_ERROR_SUMMARY = "last_update_error_summary"
        const val DEFAULT_DOWNLOAD_THREADS = 4
        const val MIN_DOWNLOAD_THREADS = 1
        const val MAX_DOWNLOAD_THREADS = 8
        const val DEFAULT_CONCURRENT_DOWNLOAD_TASKS = 1
        const val MIN_CONCURRENT_DOWNLOAD_TASKS = 1
        const val MAX_CONCURRENT_DOWNLOAD_TASKS = 3
        const val DEFAULT_AUTO_CHECK_UPDATES_ENABLED = true
        val DEFAULT_THEME_MODE: AppThemeMode = AppThemeMode.FollowSystem
    }
}
