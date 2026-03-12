package top.apricityx.workshop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.apricityx.workshop.AppThemeMode

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

@Composable
fun SteamWorkshopDemoTheme(
    themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.FollowSystem -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
