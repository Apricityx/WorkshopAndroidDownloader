package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceCompatibilityTest {
    @Test
    fun `bypass steam cm websocket on huawei harmony incremental builds`() {
        val result = DeviceCompatibility.shouldBypassSteamCmWebSocket(
            manufacturer = "HUAWEI",
            brand = "HUAWEI",
            incremental = "104.5.0.228DHSP10",
            display = "PLR-AL00 104.5.0.228(C00E228R10P10)",
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `do not bypass steam cm websocket on non harmony android devices`() {
        val result = DeviceCompatibility.shouldBypassSteamCmWebSocket(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            incremental = "OS2.0.210.0.VLGCNXM",
            display = "HyperOS",
        )

        assertThat(result).isFalse()
    }
}
