package top.apricityx.workshop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import top.apricityx.workshop.ui.component.WorkshopSwitch
import top.apricityx.workshop.ui.theme.LocalWorkshopBackdrop
import top.apricityx.workshop.ui.theme.SteamWorkshopDemoTheme

class LiquidToggleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun liquidGlassSwitch_supportsTouchClickAndSemanticsClick() {
        composeRule.setContent {
            SteamWorkshopDemoTheme(frontendMode = AppFrontendMode.LiquidGlass) {
                val backdrop = rememberLayerBackdrop()
                var checked by remember { mutableStateOf(false) }

                Box(modifier = Modifier.layerBackdrop(backdrop)) {
                    CompositionLocalProvider(LocalWorkshopBackdrop provides backdrop) {
                        WorkshopSwitch(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            modifier = Modifier.testTag("liquidSwitch"),
                        )
                    }
                }
            }
        }

        val liquidSwitch = composeRule.onNodeWithTag("liquidSwitch")
        liquidSwitch.assertIsOff()
        liquidSwitch.performTouchInput { click() }
        liquidSwitch.assertIsOn()
        liquidSwitch.performClick()
        liquidSwitch.assertIsOff()
    }
}
