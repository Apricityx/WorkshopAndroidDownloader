package top.apricityx.workshop.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import top.apricityx.workshop.ui.component.liquid.LiquidButton
import top.apricityx.workshop.ui.theme.LocalWorkshopBackdrop
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled

@Composable
fun WorkshopLiquidGlassWallpaper(
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val backgroundStops = if (isDark) {
        listOf(Color(0xFF06131E), Color(0xFF0D2234), Color(0xFF081A28))
    } else {
        listOf(Color(0xFFF3F8FF), Color(0xFFE6F1FF), Color(0xFFFFF1E9))
    }
    val blueGlow = if (isDark) Color(0xFF0F8BFF).copy(alpha = 0.48f) else Color(0xFF2B8BFF).copy(alpha = 0.35f)
    val peachGlow = if (isDark) Color(0xFFFF9B6A).copy(alpha = 0.34f) else Color(0xFFFFB38C).copy(alpha = 0.28f)
    val mintGlow = if (isDark) Color(0xFF2DD5B6).copy(alpha = 0.22f) else Color(0xFF6EE0C9).copy(alpha = 0.22f)

    Canvas(modifier = modifier.background(backgroundStops.first())) {
        drawRect(
            brush = Brush.linearGradient(
                colors = backgroundStops,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blueGlow, Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.18f),
                radius = size.minDimension * 0.52f,
            ),
            radius = size.minDimension * 0.52f,
            center = Offset(size.width * 0.15f, size.height * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(peachGlow, Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.2f),
                radius = size.minDimension * 0.4f,
            ),
            radius = size.minDimension * 0.4f,
            center = Offset(size.width * 0.88f, size.height * 0.2f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(mintGlow, Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.82f),
                radius = size.minDimension * 0.44f,
            ),
            radius = size.minDimension * 0.44f,
            center = Offset(size.width * 0.72f, size.height * 0.82f),
        )

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    blueGlow.copy(alpha = blueGlow.alpha * 0.6f),
                    peachGlow.copy(alpha = peachGlow.alpha * 0.12f),
                    Color.Transparent,
                ),
                start = Offset(size.width * 0.1f, size.height * 0.35f),
                end = Offset(size.width * 0.78f, size.height * 0.9f),
            ),
            topLeft = Offset(size.width * 0.04f, size.height * 0.48f),
            size = androidx.compose.ui.geometry.Size(
                width = size.width * 0.78f,
                height = size.height * 0.28f,
            ),
            cornerRadius = CornerRadius(size.minDimension * 0.12f, size.minDimension * 0.12f),
            alpha = if (isDark) 0.32f else 0.2f,
        )
    }
}

@Composable
fun WorkshopGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 18.dp,
    lensHeight: Dp = 10.dp,
    lensAmount: Dp = 12.dp,
    surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    content: @Composable BoxScope.() -> Unit,
) {
    val liquidEnabled = isLiquidGlassFrontendEnabled()
    val backdrop = LocalWorkshopBackdrop.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f

    if (!liquidEnabled || backdrop == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = surfaceColor,
        ) {
            Box(content = content)
        }
        return
    }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    lens(lensHeight.toPx(), lensAmount.toPx())
                },
                highlight = {
                    Highlight.Ambient.copy(alpha = if (isDark) 0.24f else 0.16f)
                },
                shadow = {
                    Shadow(
                        radius = 28.dp,
                        alpha = if (isDark) 0.72f else 0.22f,
                        color = Color.Black.copy(alpha = if (isDark) 0.28f else 0.12f),
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 14.dp,
                        alpha = if (isDark) 0.32f else 0.18f,
                        color = Color.Black.copy(alpha = if (isDark) 0.2f else 0.08f),
                    )
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                },
            )
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clip(shape),
        content = content,
    )
}

@Composable
fun WorkshopGlassIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val backdrop = LocalWorkshopBackdrop.current
    if (isLiquidGlassFrontendEnabled() && backdrop != null) {
        LiquidButton(
            onClick = if (enabled) onClick else null,
            backdrop = backdrop,
            modifier = modifier,
            isInteractive = enabled,
            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
            height = 42.dp,
            horizontalPadding = 10.dp,
        ) {
            if (content != null) {
                content()
            } else {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }

    WorkshopGlassSurface(
        modifier = modifier
            .size(42.dp)
            .let { baseModifier ->
                if (enabled) {
                    baseModifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    baseModifier
                }
            },
        shape = CircleShape,
        blurRadius = 12.dp,
        lensHeight = 8.dp,
        lensAmount = 8.dp,
        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
    ) {
        if (content != null) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                content()
            }
        } else {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
fun WorkshopGlassNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    imageVector: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalWorkshopBackdrop.current
    if (isLiquidGlassFrontendEnabled() && backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            surfaceColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
            },
            height = 48.dp,
            horizontalPadding = 16.dp,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    val baseModifier = modifier
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )

    if (selected) {
        WorkshopGlassSurface(
            modifier = baseModifier,
            shape = shape,
            blurRadius = 12.dp,
            lensHeight = 8.dp,
            lensAmount = 8.dp,
            surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        return
    }

    Row(
        modifier = baseModifier
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
