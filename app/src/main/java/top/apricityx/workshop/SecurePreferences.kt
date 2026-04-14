package top.apricityx.workshop

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal fun createEncryptedPrefsOrFallback(
    context: Context,
    encryptedPrefsName: String,
    fallbackPrefsName: String,
    storageLabel: String,
): SharedPreferences =
    runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            encryptedPrefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { error ->
        workshopLogWarn(
            "Encrypted SharedPreferences unavailable for $storageLabel; falling back to plaintext storage.",
            error,
        )
        // Keep fallback data in a separate file so encrypted and plaintext entries never share the same XML.
        context.getSharedPreferences(fallbackPrefsName, Context.MODE_PRIVATE)
    }
