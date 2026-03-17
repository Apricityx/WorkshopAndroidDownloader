package top.apricityx.workshop.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.RadioButton as MaterialRadioButton
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import top.apricityx.workshop.ui.component.liquid.LiquidButton
import top.apricityx.workshop.ui.component.liquid.LiquidSlider
import top.apricityx.workshop.ui.component.liquid.LiquidToggle
import top.apricityx.workshop.ui.theme.LocalWorkshopBackdrop
import top.apricityx.workshop.ui.theme.isLiquidGlassFrontendEnabled
import kotlin.math.round

private enum class WorkshopButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Destructive,
}

@Composable
fun WorkshopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    WorkshopAdaptiveButton(
        variant = WorkshopButtonVariant.Primary,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun WorkshopOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    WorkshopAdaptiveButton(
        variant = WorkshopButtonVariant.Secondary,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun WorkshopTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    WorkshopAdaptiveButton(
        variant = WorkshopButtonVariant.Ghost,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun WorkshopDestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    WorkshopAdaptiveButton(
        variant = WorkshopButtonVariant.Destructive,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
private fun WorkshopAdaptiveButton(
    variant: WorkshopButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isLiquidGlassFrontendEnabled()) {
        when (variant) {
            WorkshopButtonVariant.Primary -> MaterialButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content,
            )

            WorkshopButtonVariant.Secondary -> MaterialOutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content,
            )

            WorkshopButtonVariant.Ghost -> MaterialTextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content,
            )

            WorkshopButtonVariant.Destructive -> MaterialButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                content = content,
            )
        }
        return
    }

    val backdrop = LocalWorkshopBackdrop.current
    if (backdrop == null) {
        when (variant) {
            WorkshopButtonVariant.Primary -> MaterialButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content,
            )

            WorkshopButtonVariant.Secondary -> MaterialOutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content,
            )

            WorkshopButtonVariant.Ghost -> MaterialTextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content,
            )

            WorkshopButtonVariant.Destructive -> MaterialButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                content = content,
            )
        }
        return
    }

    val contentColor = when (variant) {
        WorkshopButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
        WorkshopButtonVariant.Secondary -> MaterialTheme.colorScheme.onSurface
        WorkshopButtonVariant.Ghost -> MaterialTheme.colorScheme.onSurfaceVariant
        WorkshopButtonVariant.Destructive -> MaterialTheme.colorScheme.onError
    }
    val tintColor = when (variant) {
        WorkshopButtonVariant.Primary -> MaterialTheme.colorScheme.primary
        WorkshopButtonVariant.Secondary -> Unspecified
        WorkshopButtonVariant.Ghost -> Unspecified
        WorkshopButtonVariant.Destructive -> MaterialTheme.colorScheme.error
    }
    val surfaceColor = when (variant) {
        WorkshopButtonVariant.Primary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        WorkshopButtonVariant.Secondary -> MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
        WorkshopButtonVariant.Ghost -> MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
        WorkshopButtonVariant.Destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
    }

    LiquidButton(
        onClick = if (enabled) onClick else null,
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.52f),
        isInteractive = enabled,
        tint = tintColor,
        surfaceColor = surfaceColor,
        height = if (variant == WorkshopButtonVariant.Ghost) 40.dp else 48.dp,
        horizontalPadding = if (variant == WorkshopButtonVariant.Ghost) 14.dp else 16.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

@Composable
fun WorkshopSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!isLiquidGlassFrontendEnabled()) {
        MaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }

    val backdrop = LocalWorkshopBackdrop.current
    if (backdrop == null || onCheckedChange == null) {
        MaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }

    LiquidToggle(
        checked = { checked },
        onCheckedChange = onCheckedChange,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun WorkshopRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!isLiquidGlassFrontendEnabled()) {
        MaterialRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    WorkshopGlassSurface(
        modifier = modifier
            .size(24.dp)
            .alpha(if (enabled) 1f else 0.52f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && onClick != null,
                onClick = { onClick?.invoke() },
            ),
        shape = CircleShape,
        blurRadius = 8.dp,
        lensHeight = 5.dp,
        lensAmount = 5.dp,
        surfaceColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)
        },
        borderColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        },
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun WorkshopSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    onInteractionActiveChange: ((Boolean) -> Unit)? = null,
) {
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val emitValue: (Float) -> Unit = { nextValue ->
        val snappedValue = nextValue.snapToSliderStep(valueRange = valueRange, steps = steps)
        onValueChange(snappedValue)
    }

    if (!isLiquidGlassFrontendEnabled()) {
        MaterialSlider(
            value = clampedValue,
            onValueChange = emitValue,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
        return
    }

    val backdrop = LocalWorkshopBackdrop.current
    if (backdrop == null) {
        MaterialSlider(
            value = clampedValue,
            onValueChange = emitValue,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
        return
    }

    LiquidSlider(
        value = { clampedValue },
        onValueChange = { nextValue ->
            if (enabled) {
                onValueChange(nextValue.coerceIn(valueRange.start, valueRange.endInclusive))
            }
        },
        valueRange = valueRange,
        visibilityThreshold = 0.01f,
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.52f),
        onInteractionActiveChange = onInteractionActiveChange,
    )
}

private fun Float.snapToSliderStep(
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val clampedValue = coerceIn(valueRange.start, valueRange.endInclusive)
    if (steps <= 0) {
        return clampedValue
    }
    val intervals = steps + 1
    val stepSize = (valueRange.endInclusive - valueRange.start) / intervals
    if (stepSize <= 0f) {
        return valueRange.start
    }
    val offset = round((clampedValue - valueRange.start) / stepSize) * stepSize
    return (valueRange.start + offset).coerceIn(valueRange.start, valueRange.endInclusive)
}

@Composable
fun WorkshopOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    if (!isLiquidGlassFrontendEnabled()) {
        MaterialOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            label = label,
            supportingText = supportingText,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            minLines = minLines,
        )
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val labelColor = if (!enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.24f else 0.4f)
        isFocused -> MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.92f else 0.98f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.86f else 0.94f)
    }
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (label != null) {
                    CompositionLocalProvider(LocalContentColor provides labelColor) {
                        ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                            label()
                        }
                    }
                }

                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    ),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = singleLine,
                        minLines = minLines,
                        keyboardOptions = keyboardOptions,
                        visualTransformation = visualTransformation,
                        textStyle = MaterialTheme.typography.bodyLarge.merge(
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = if (singleLine) 24.dp else (24 * minLines).dp)
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                            },
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                innerTextField()
                            }
                        },
                    )
                }
            }
        }

        if (supportingText != null) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                        supportingText()
                    }
                }
            }
        }
    }
}
