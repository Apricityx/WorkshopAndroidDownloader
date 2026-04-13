package top.apricityx.workshop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled

@Composable
fun WorkshopDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassStyle = rememberWorkshopDialogGlassStyle(transparent = false)
    WorkshopGlassDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        dismissOnClickOutside = dismissOnClickOutside,
        dismissOnBackPress = dismissOnBackPress,
        buttons = buttons,
        glassStyle = glassStyle,
        content = content,
    )
}

@Composable
fun WorkshopTransparentGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassStyle = rememberWorkshopDialogGlassStyle(transparent = true)
    WorkshopGlassDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        dismissOnClickOutside = dismissOnClickOutside,
        dismissOnBackPress = dismissOnBackPress,
        buttons = buttons,
        glassStyle = glassStyle,
        content = content,
    )
}

@Composable
private fun WorkshopGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    glassStyle: WorkshopDialogGlassStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    val popupHostState = rememberWorkshopPopupHostState()
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val dialogInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = dismissOnBackPress,
            usePlatformDefaultWidth = false,
        ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalWorkshopPopupHostState provides popupHostState,
            LocalWorkshopPreferLensButtons provides glassStyle.preferLensButtons,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassStyle.scrimColor)
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                        enabled = dismissOnClickOutside,
                        onClick = onDismissRequest,
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                WorkshopGlassSurface(
                    modifier = modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .clickable(
                            interactionSource = dialogInteractionSource,
                            indication = null,
                        ) {},
                    shape = MaterialTheme.shapes.extraLarge,
                    blurRadius = glassStyle.blurRadius,
                    lensHeight = glassStyle.lensHeight,
                    lensAmount = glassStyle.lensAmount,
                    surfaceColor = glassStyle.surfaceColor,
                    borderColor = glassStyle.borderColor,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        title?.let { titleContent ->
                            ProvideTextStyle(
                                value = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            ) {
                                titleContent()
                            }
                        }
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            content()
                        }
                        buttons?.let { buttonContent ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                content = buttonContent,
                            )
                        }
                    }
                }

                WorkshopPopupHost(state = popupHostState)
            }
        }
    }
}

@Composable
private fun rememberWorkshopDialogGlassStyle(
    transparent: Boolean,
): WorkshopDialogGlassStyle {
    val liquidEnabled = isLiquidGlassFrontendEnabled()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f

    return if (transparent) {
        WorkshopDialogGlassStyle(
            scrimColor = if (liquidEnabled) {
                Color.Black.copy(alpha = if (isDark) 0.32f else 0.12f)
            } else {
                Color.Black.copy(alpha = 0.24f)
            },
            surfaceColor = if (liquidEnabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.16f else 0.22f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.84f else 0.9f)
            },
            borderColor = if (liquidEnabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.2f else 0.14f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            },
            blurRadius = 32.dp,
            lensHeight = 14.dp,
            lensAmount = 20.dp,
            preferLensButtons = true,
        )
    } else {
        WorkshopDialogGlassStyle(
            scrimColor = if (liquidEnabled) {
                Color.Black.copy(alpha = if (isDark) 0.42f else 0.18f)
            } else {
                Color.Black.copy(alpha = 0.38f)
            },
            surfaceColor = if (liquidEnabled) {
                MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.24f else 0.62f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            },
            borderColor = if (liquidEnabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.16f else 0.12f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            },
            blurRadius = 24.dp,
            lensHeight = 12.dp,
            lensAmount = 16.dp,
            preferLensButtons = false,
        )
    }
}

private data class WorkshopDialogGlassStyle(
    val scrimColor: Color,
    val surfaceColor: Color,
    val borderColor: Color,
    val blurRadius: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val preferLensButtons: Boolean,
)
