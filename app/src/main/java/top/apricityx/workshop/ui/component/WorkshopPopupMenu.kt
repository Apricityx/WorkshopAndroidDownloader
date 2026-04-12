package top.apricityx.workshop.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.apricityx.workshop.ui.theme.LocalWorkshopBackdrop
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled
import top.apricityx.workshop.ui.theme.shouldReduceLiquidGlassEffects
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val WorkshopPopupAnimationDurationMillis = 180
private const val WorkshopPopupHiddenScale = 0.92f
private val WorkshopPopupShape = RoundedCornerShape(16.dp)
private val WorkshopPopupBlurRadius = 1.5.dp
private val WorkshopPopupLensHeight = 12.dp
private val WorkshopPopupLensAmount = 18.dp
private const val WorkshopPopupSurfaceAlpha = 0.14f
private const val WorkshopPopupDarkBorderAlpha = 0.14f
private const val WorkshopPopupLightBorderAlpha = 0.1f

internal data class WorkshopPopupThemeSnapshot(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
)

internal data class WorkshopPopupEntry(
    val ownerId: Any,
    val anchorBounds: Rect,
    val modifier: Modifier,
    val useLiquidPopup: Boolean,
    val isDark: Boolean,
    val backdrop: Backdrop?,
    val theme: WorkshopPopupThemeSnapshot,
    val onDismissRequest: () -> Unit,
    val content: @Composable ColumnScope.() -> Unit,
)

class WorkshopPopupHostState internal constructor() {
    internal var currentEntry by mutableStateOf<WorkshopPopupEntry?>(null)
        private set

    internal fun show(entry: WorkshopPopupEntry) {
        currentEntry = entry
    }

    fun dismiss(ownerId: Any? = null) {
        val current = currentEntry ?: return
        if (ownerId == null || current.ownerId === ownerId) {
            currentEntry = null
        }
    }
}

private data class WorkshopPopupPlacement(
    val offset: IntOffset,
    val alignEnd: Boolean,
    val openAbove: Boolean,
)

val LocalWorkshopPopupHostState = staticCompositionLocalOf<WorkshopPopupHostState?> { null }
val LocalWorkshopPopupBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberWorkshopPopupHostState(): WorkshopPopupHostState =
    remember { WorkshopPopupHostState() }

@Composable
fun WorkshopPopupHost(
    state: WorkshopPopupHostState,
    modifier: Modifier = Modifier,
) {
    var hostBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val targetEntry = state.currentEntry
    val targetOwnerId = targetEntry?.ownerId
    var renderedEntry by remember { mutableStateOf<WorkshopPopupEntry?>(null) }
    var isPopupVisible by remember { mutableStateOf(false) }
    var pendingShow by remember { mutableStateOf(false) }
    val entry = if (targetEntry != null && renderedEntry?.ownerId === targetEntry.ownerId) {
        targetEntry
    } else {
        renderedEntry
    }
    var menuSize by remember(entry?.ownerId) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(targetOwnerId, targetEntry == null) {
        if (targetEntry != null) {
            renderedEntry = targetEntry
            pendingShow = true
            isPopupVisible = false
        } else if (renderedEntry != null || entry != null) {
            pendingShow = false
            isPopupVisible = false
            delay(WorkshopPopupAnimationDurationMillis.toLong())
            if (state.currentEntry == null) {
                renderedEntry = null
            }
        }
    }

    LaunchedEffect(entry?.ownerId, menuSize, pendingShow) {
        val currentEntry = entry ?: return@LaunchedEffect
        if (!pendingShow || menuSize == IntSize.Zero) {
            return@LaunchedEffect
        }
        delay(16)
        if (entry?.ownerId === currentEntry.ownerId && pendingShow) {
            isPopupVisible = true
            pendingShow = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1000f)
            .onGloballyPositioned { coordinates ->
                hostBounds = coordinates.boundsInWindow()
            },
    ) {
        if (entry == null) {
            return@Box
        }
        val currentHostBounds = hostBounds ?: return@Box

        val scrimInteractionSource = remember(entry.ownerId) { MutableInteractionSource() }
        val horizontalPaddingPx = with(density) { 8.dp.toPx() }
        val verticalPaddingPx = with(density) { 8.dp.toPx() }
        val verticalGapPx = with(density) { 8.dp.toPx() }
        val preferEndAlignment = entry.anchorBounds.width <= with(density) { 72.dp.toPx() }

        val placement = remember(entry.anchorBounds, currentHostBounds, menuSize, horizontalPaddingPx, verticalPaddingPx, verticalGapPx, preferEndAlignment) {
            val menuWidth = menuSize.width.toFloat()
            val menuHeight = menuSize.height.toFloat()
            val hostWidth = currentHostBounds.width
            val hostHeight = currentHostBounds.height
            val anchorLeft = entry.anchorBounds.left - currentHostBounds.left
            val anchorRight = entry.anchorBounds.right - currentHostBounds.left
            val anchorTop = entry.anchorBounds.top - currentHostBounds.top
            val anchorBottom = entry.anchorBounds.bottom - currentHostBounds.top

            val desiredX = if (preferEndAlignment) {
                anchorRight - menuWidth
            } else {
                anchorLeft
            }
            val maxX = (hostWidth - menuWidth - horizontalPaddingPx).coerceAtLeast(horizontalPaddingPx)
            val clampedX = desiredX.coerceIn(horizontalPaddingPx, maxX)

            val belowY = anchorBottom + verticalGapPx
            val aboveY = anchorTop - menuHeight - verticalGapPx
            val maxY = (hostHeight - menuHeight - verticalPaddingPx).coerceAtLeast(verticalPaddingPx)
            val openAbove = when {
                belowY <= maxY -> false
                aboveY >= verticalPaddingPx -> true
                else -> false
            }
            val resolvedY = when {
                belowY <= maxY -> belowY
                aboveY >= verticalPaddingPx -> aboveY
                else -> belowY.coerceIn(verticalPaddingPx, maxY)
            }

            WorkshopPopupPlacement(
                offset = IntOffset(clampedX.roundToInt(), resolvedY.roundToInt()),
                alignEnd = preferEndAlignment,
                openAbove = openAbove,
            )
        }
        val scrimAlpha by animateFloatAsState(
            targetValue = if (isPopupVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = WorkshopPopupAnimationDurationMillis,
                easing = FastOutSlowInEasing,
            ),
            label = "workshopPopupScrimAlpha",
        )
        val popupAlpha by animateFloatAsState(
            targetValue = if (isPopupVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = WorkshopPopupAnimationDurationMillis,
                easing = FastOutSlowInEasing,
            ),
            label = "workshopPopupAlpha",
        )
        val popupScale by animateFloatAsState(
            targetValue = if (isPopupVisible) 1f else WorkshopPopupHiddenScale,
            animationSpec = tween(
                durationMillis = WorkshopPopupAnimationDurationMillis,
                easing = FastOutSlowInEasing,
            ),
            label = "workshopPopupScale",
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(scrimAlpha)
                .clickable(
                    interactionSource = scrimInteractionSource,
                    indication = null,
                    enabled = isPopupVisible,
                    onClick = entry.onDismissRequest,
                ),
        )

        MaterialTheme(
            colorScheme = entry.theme.colorScheme,
            typography = entry.theme.typography,
            shapes = entry.theme.shapes,
        ) {
            val popupInteractionSource = remember(entry.ownerId) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .offset { placement.offset }
                    .onSizeChanged { menuSize = it }
                    .graphicsLayer {
                        alpha = popupAlpha
                        scaleX = popupScale
                        scaleY = popupScale
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (placement.alignEnd) 1f else 0f,
                            pivotFractionY = if (placement.openAbove) 1f else 0f,
                        )
                    }
                    .clickable(
                        interactionSource = popupInteractionSource,
                        indication = null,
                    ) {},
            ) {
                if (entry.useLiquidPopup && entry.backdrop != null) {
                    LiquidPopupMenuSurface(
                        modifier = entry.modifier.widthIn(min = 220.dp, max = 320.dp),
                        backdrop = entry.backdrop,
                        isDark = entry.isDark,
                        content = entry.content,
                    )
                } else {
                    PlainPopupMenuSurface(
                        modifier = entry.modifier.widthIn(min = 220.dp, max = 320.dp),
                        liquidEnabled = entry.backdrop != null,
                        isDark = entry.isDark,
                        content = entry.content,
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.WorkshopPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val popupHostState = LocalWorkshopPopupHostState.current
    val backdrop = LocalWorkshopPopupBackdrop.current ?: LocalWorkshopBackdrop.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val useLiquidPopup =
        isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null

    if (popupHostState == null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            if (useLiquidPopup) {
                LiquidPopupMenuSurface(
                    modifier = modifier.widthIn(min = 220.dp, max = 320.dp),
                    backdrop = backdrop,
                    isDark = isDark,
                    content = content,
                )
            } else {
                PlainPopupMenuSurface(
                    modifier = modifier.widthIn(min = 220.dp, max = 320.dp),
                    liquidEnabled = isLiquidGlassFrontendEnabled(),
                    isDark = isDark,
                    content = content,
                )
            }
        }
        return
    }

    val ownerId = remember { Any() }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val themeSnapshot = remember(
        colorScheme,
        typography,
        shapes,
    ) {
        WorkshopPopupThemeSnapshot(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
        )
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { coordinates ->
                anchorBounds = coordinates.boundsInWindow()
            },
    )

    SideEffect {
        val bounds = anchorBounds
        if (expanded && bounds != null) {
            popupHostState.show(
                WorkshopPopupEntry(
                    ownerId = ownerId,
                    anchorBounds = bounds,
                    modifier = modifier,
                    useLiquidPopup = useLiquidPopup,
                    isDark = isDark,
                    backdrop = backdrop,
                    theme = themeSnapshot,
                    onDismissRequest = onDismissRequest,
                    content = content,
                ),
            )
        } else {
            popupHostState.dismiss(ownerId)
        }
    }

    DisposableEffect(popupHostState, ownerId) {
        onDispose {
            popupHostState.dismiss(ownerId)
        }
    }
}

@Composable
private fun LiquidPopupMenuSurface(
    modifier: Modifier,
    backdrop: Backdrop,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = WorkshopPopupSurfaceAlpha)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isDark) {
            WorkshopPopupDarkBorderAlpha
        } else {
            WorkshopPopupLightBorderAlpha
        },
    )
    val contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { WorkshopPopupShape },
                    effects = {
                        vibrancy()
                        blur(WorkshopPopupBlurRadius.toPx())
                        lens(WorkshopPopupLensHeight.toPx(), WorkshopPopupLensAmount.toPx())
                    },
                    onDrawSurface = {
                        drawRect(surfaceColor)
                    },
                )
                .clip(WorkshopPopupShape)
                .border(width = 1.dp, color = borderColor, shape = WorkshopPopupShape)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun PlainPopupMenuSurface(
    modifier: Modifier,
    liquidEnabled: Boolean,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape: Shape = RoundedCornerShape(24.dp)
    val surfaceColor = if (liquidEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.92f else 0.96f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (liquidEnabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.12f else 0.08f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    }
    val contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = if (liquidEnabled) 0.dp else 4.dp,
            shadowElevation = if (liquidEnabled) 0.dp else 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }
}

@Composable
fun WorkshopPopupMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reserveLeadingSpace: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.52f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null || reserveLeadingSpace) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                leadingIcon?.invoke()
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                text()
            }
        }
    }
}
