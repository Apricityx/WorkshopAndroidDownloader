package top.apricityx.workshop

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.OkHttpClient
import top.apricityx.workshop.steam.protocol.DEFAULT_MACHINE_NAME
import top.apricityx.workshop.steam.protocol.OkHttpSteamCmSession
import top.apricityx.workshop.steam.protocol.buildSteamMachineId
import java.util.UUID

internal class SteamClientIdentity(
    context: Context,
) {
    private val appContext = context.applicationContext

    val machineName: String = DEFAULT_MACHINE_NAME
    val machineId: ByteArray by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val installationId = resolveInstallationId()
        val baseId = resolveAndroidId() ?: installationId
        val hardwareSummary = listOf(
            normalizeBuildField(Build.MANUFACTURER, "unknown-manufacturer"),
            normalizeBuildField(Build.BRAND, "unknown-brand"),
            normalizeBuildField(Build.MODEL, "unknown-model"),
            normalizeBuildField(Build.DEVICE, "unknown-device"),
            normalizeBuildField(Build.BOARD, "unknown-board"),
        ).joinToString(separator = "|")
        val storageSummary = listOf(
            normalizeBuildField(Build.FINGERPRINT, "unknown-fingerprint"),
            normalizeBuildField(Build.PRODUCT, "unknown-product"),
            normalizeBuildField(Build.HARDWARE, "unknown-hardware"),
            Build.SUPPORTED_ABIS.joinToString(separator = ",").ifBlank { "unknown-abi" },
        ).joinToString(separator = "|")

        buildSteamMachineId(
            machineGuidSource = "$baseId|${appContext.packageName}".toByteArray(Charsets.UTF_8),
            macAddressSource = "$baseId|$hardwareSummary".toByteArray(Charsets.UTF_8),
            diskIdSource = "$baseId|$storageSummary".toByteArray(Charsets.UTF_8),
        )
    }

    fun createSession(client: OkHttpClient): OkHttpSteamCmSession =
        OkHttpSteamCmSession(
            client = client,
            machineName = machineName,
            machineId = machineId,
        )

    private fun resolveAndroidId(): String? =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            ?.takeUnless { value ->
                value.isBlank() || value == INVALID_ANDROID_ID
            }

    private fun resolveInstallationId(): String {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALLATION_ID, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALLATION_ID, created).apply()
        return created
    }

    private fun normalizeBuildField(
        value: String?,
        fallback: String,
    ): String =
        value
            ?.trim()
            ?.takeIf { candidate ->
                candidate.isNotEmpty() && !candidate.equals("unknown", ignoreCase = true)
            }
            ?: fallback

    private companion object {
        private const val PREFS_NAME = "steam_client_identity"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val INVALID_ANDROID_ID = "9774d56d682e549c"
    }
}
