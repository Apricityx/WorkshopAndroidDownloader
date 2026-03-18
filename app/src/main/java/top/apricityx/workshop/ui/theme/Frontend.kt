package top.apricityx.workshop.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import top.apricityx.workshop.AppFrontendMode

data class WorkshopChromePadding(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

val LocalWorkshopFrontendMode = staticCompositionLocalOf { AppFrontendMode.Legacy }
val LocalWorkshopBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalWorkshopChromePadding = staticCompositionLocalOf { WorkshopChromePadding() }

@Composable
fun isLiquidGlassFrontendEnabled(): Boolean =
    LocalWorkshopFrontendMode.current == AppFrontendMode.LiquidGlass ||
        LocalWorkshopFrontendMode.current == AppFrontendMode.LiteLiquidGlass

@Composable
fun isLiteLiquidGlassFrontendEnabled(): Boolean =
    LocalWorkshopFrontendMode.current == AppFrontendMode.LiteLiquidGlass

@Composable
fun shouldReduceLiquidGlassEffects(): Boolean =
    isLiteLiquidGlassFrontendEnabled()

@Composable
fun workshopListContentPadding(
    topExtra: Dp = 0.dp,
    bottomExtra: Dp = 0.dp,
): PaddingValues {
    val chromePadding = LocalWorkshopChromePadding.current
    val liquidTopOverlap = if (isLiquidGlassFrontendEnabled()) 24.dp else 0.dp
    return PaddingValues(
        top = (chromePadding.top + topExtra - liquidTopOverlap).coerceAtLeast(0.dp),
        bottom = chromePadding.bottom + bottomExtra,
    )
}

@Composable
fun Modifier.workshopChromePadding(
    topExtra: Dp = 0.dp,
    bottomExtra: Dp = 0.dp,
): Modifier {
    val chromePadding = LocalWorkshopChromePadding.current
    return this.padding(
        top = chromePadding.top + topExtra,
        bottom = chromePadding.bottom + bottomExtra,
    )
}
