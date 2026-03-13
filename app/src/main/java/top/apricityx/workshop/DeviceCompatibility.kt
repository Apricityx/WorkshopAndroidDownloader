package top.apricityx.workshop

import android.os.Build

object DeviceCompatibility {
    fun shouldBypassSteamCmWebSocket(): Boolean =
        shouldBypassSteamCmWebSocket(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            incremental = Build.VERSION.INCREMENTAL.orEmpty(),
            display = Build.DISPLAY.orEmpty(),
        )

    internal fun shouldBypassSteamCmWebSocket(
        manufacturer: String,
        brand: String,
        incremental: String,
        display: String,
    ): Boolean {
        val vendorMatched = sequenceOf(manufacturer, brand).any { value ->
            value.contains("huawei", ignoreCase = true) || value.contains("honor", ignoreCase = true)
        }
        if (!vendorMatched) {
            return false
        }

        return sequenceOf(incremental, display).any { value ->
            value.contains("DHSP", ignoreCase = true) ||
                value.contains("HMOS", ignoreCase = true) ||
                value.contains("Harmony", ignoreCase = true)
        }
    }
}
