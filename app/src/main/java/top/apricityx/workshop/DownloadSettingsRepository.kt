package top.apricityx.workshop

import android.content.Context

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

    companion object {
        private const val PREFS_NAME = "download_settings"
        private const val KEY_DOWNLOAD_THREADS = "download_threads"
        private const val KEY_CONCURRENT_DOWNLOAD_TASKS = "concurrent_download_tasks"
        private const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_DOWNLOAD_THREADS = 4
        const val MIN_DOWNLOAD_THREADS = 1
        const val MAX_DOWNLOAD_THREADS = 8
        const val DEFAULT_CONCURRENT_DOWNLOAD_TASKS = 1
        const val MIN_CONCURRENT_DOWNLOAD_TASKS = 1
        const val MAX_CONCURRENT_DOWNLOAD_TASKS = 3
        val DEFAULT_THEME_MODE: AppThemeMode = AppThemeMode.FollowSystem
    }
}
