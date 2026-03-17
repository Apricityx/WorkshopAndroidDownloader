package top.apricityx.workshop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.apricityx.workshop.AppFrontendMode
import top.apricityx.workshop.AppThemeMode

private val LegacyLightColors = lightColorScheme(
    primary = HarborBlue,
    secondary = EmberAccent,
    tertiary = HarborBlueDark,
    background = MistSurface,
    surface = Color.White,
    surfaceVariant = Color(0xFFE1EAF1),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = SteelInk,
    onSurface = SteelInk,
    onSurfaceVariant = Color(0xFF4B5B69),
)

private val LegacyDarkColors = darkColorScheme(
    primary = HarborBlueDark,
    secondary = EmberAccentDark,
    tertiary = EmberAccent,
    background = SteelInkDark,
    surface = SlateSurface,
    surfaceVariant = Color(0xFF213241),
    onPrimary = SteelInk,
    onSecondary = SteelInk,
    onBackground = Color(0xFFE6EEF4),
    onSurface = Color(0xFFE6EEF4),
    onSurfaceVariant = Color(0xFFA8BBCB),
)

private val LiquidLightColors = lightColorScheme(
    primary = AuroraBlue,
    secondary = AuroraPeach,
    tertiary = AuroraMint,
    background = AuroraSky,
    surface = Color.White.copy(alpha = 0.86f),
    surfaceVariant = Color.White.copy(alpha = 0.52f),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = AuroraNavy,
    onBackground = AuroraNavy,
    onSurface = AuroraNavy,
    onSurfaceVariant = Color(0xFF516479),
)

private val LiquidDarkColors = darkColorScheme(
    primary = AuroraBlueDark,
    secondary = AuroraPeachDark,
    tertiary = AuroraMintDark,
    background = AuroraSkyDark,
    surface = Color(0xFF0F2031),
    surfaceVariant = Color(0xFF17304A),
    onPrimary = AuroraNavy,
    onSecondary = AuroraNavy,
    onTertiary = AuroraNavy,
    onBackground = AuroraNavyDark,
    onSurface = AuroraNavyDark,
    onSurfaceVariant = Color(0xFF9AB2C9),
)

private val LiquidGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun SteamWorkshopDemoTheme(
    themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    frontendMode: AppFrontendMode = AppFrontendMode.Legacy,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.FollowSystem -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val colors = when (frontendMode) {
        AppFrontendMode.LiquidGlass -> if (darkTheme) LiquidDarkColors else LiquidLightColors
        AppFrontendMode.Legacy -> if (darkTheme) LegacyDarkColors else LegacyLightColors
    }

    CompositionLocalProvider(LocalWorkshopFrontendMode provides frontendMode) {
        MaterialTheme(
            colorScheme = colors,
            shapes = if (frontendMode == AppFrontendMode.LiquidGlass) LiquidGlassShapes else Shapes(),
            content = content,
        )
    }
}
