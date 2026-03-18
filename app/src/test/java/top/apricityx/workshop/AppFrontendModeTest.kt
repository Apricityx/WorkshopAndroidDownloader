package top.apricityx.workshop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppFrontendModeTest {
    @Test
    fun fromStorageValue_returnsMatchingMode() {
        assertThat(AppFrontendMode.fromStorageValue("liquid_glass")).isEqualTo(AppFrontendMode.LiquidGlass)
        assertThat(AppFrontendMode.fromStorageValue("lite_liquid_glass")).isEqualTo(AppFrontendMode.LiteLiquidGlass)
        assertThat(AppFrontendMode.fromStorageValue("legacy")).isEqualTo(AppFrontendMode.Legacy)
    }

    @Test
    fun fromStorageValue_fallsBackToLiquidGlass() {
        assertThat(AppFrontendMode.fromStorageValue("unknown")).isEqualTo(AppFrontendMode.LiquidGlass)
    }

    @Test
    fun displayName_matchesFrontendLabel() {
        assertThat(AppFrontendMode.LiquidGlass.displayName()).isEqualTo("液态玻璃")
        assertThat(AppFrontendMode.LiteLiquidGlass.displayName()).isEqualTo("轻量液态")
        assertThat(AppFrontendMode.Legacy.displayName()).isEqualTo("旧版经典")
    }
}
