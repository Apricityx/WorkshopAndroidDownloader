package top.apricityx.workshop

import android.content.Context

class BaiduTranslationCredentialsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        createEncryptedPrefsOrFallback(
            context = appContext,
            encryptedPrefsName = PREFS_NAME,
            fallbackPrefsName = FALLBACK_PREFS_NAME,
            storageLabel = "Baidu translation credentials",
        )
    }

    fun getCredentials(): BaiduTranslationCredentials =
        BaiduTranslationCredentials(
            appId = prefs.getString(KEY_APP_ID, null)?.trim().orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty(),
        )

    fun hasConfiguredCredentials(): Boolean = getCredentials().isConfigured()

    fun setCredentials(credentials: BaiduTranslationCredentials) {
        val normalizedAppId = credentials.appId.trim()
        val normalizedApiKey = credentials.apiKey.trim()
        prefs.edit().apply {
            if (normalizedAppId.isEmpty()) {
                remove(KEY_APP_ID)
            } else {
                putString(KEY_APP_ID, normalizedAppId)
            }

            if (normalizedApiKey.isEmpty()) {
                remove(KEY_API_KEY)
            } else {
                putString(KEY_API_KEY, normalizedApiKey)
            }
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "baidu_translation_credentials"
        private const val FALLBACK_PREFS_NAME = "baidu_translation_credentials_fallback"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_API_KEY = "api_key"
    }
}
