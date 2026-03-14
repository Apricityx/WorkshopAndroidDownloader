package top.apricityx.workshop

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class BaiduTranslationCredentialsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
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
        private const val KEY_APP_ID = "app_id"
        private const val KEY_API_KEY = "api_key"
    }
}
