package com.example.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ui.theme.OrbitDarkSheetCanvas
import com.example.core.ui.theme.OrbitDarkSheetHairline
import com.example.core.ui.theme.OrbitDarkSheetHandle
import com.example.core.ui.theme.OrbitDarkSheetPanel
import com.example.core.ui.theme.OrbitDarkSheetScrim
import com.example.core.ui.theme.OrbitDarkSheetTile
import com.example.core.ui.theme.OrbitDarkSheetTopHighlight
import com.example.core.ui.theme.OrbitLightSheetCanvas
import com.example.core.ui.theme.OrbitLightSheetHairline
import com.example.core.ui.theme.OrbitLightSheetHandle
import com.example.core.ui.theme.OrbitLightSheetPanel
import com.example.core.ui.theme.OrbitLightSheetScrim
import com.example.core.ui.theme.OrbitLightSheetTile
import com.example.core.ui.theme.OrbitLightSheetTopHighlight

object OrbitControlTokens {
    val ControlHeight = 36.dp
    val InputMinHeight = 38.dp
    val CapsuleHeight = 28.dp
    val ControlCornerRadius = 10.dp
    val SegmentCornerRadius = 9.dp
    val DialogCornerRadius = 18.dp
    val PopupCornerRadius = 14.dp
    val HairlineWidth = 0.8.dp
}

@Immutable
data class OrbitSurfacePalette(
    val isDark: Boolean,
    val canvas: Color,
    val panel: Color,
    val tile: Color,
    val hairline: Color,
    val topHighlight: Color,
    val scrim: Color,
    val handle: Color,
    val innerDivider: Color,
    val activeFill: Color,
    val activeBorder: Color
)

@Composable
fun rememberOrbitSurfacePalette(): OrbitSurfacePalette {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primary = MaterialTheme.colorScheme.primary
    return if (isDark) {
        OrbitSurfacePalette(
            isDark = true,
            canvas = OrbitDarkSheetCanvas,
            panel = OrbitDarkSheetPanel,
            tile = OrbitDarkSheetTile,
            hairline = OrbitDarkSheetHairline,
            topHighlight = OrbitDarkSheetTopHighlight,
            scrim = OrbitDarkSheetScrim,
            handle = OrbitDarkSheetHandle,
            innerDivider = OrbitDarkSheetHairline,
            activeFill = primary.copy(alpha = 0.17f),
            activeBorder = primary.copy(alpha = 0.55f)
        )
    } else {
        OrbitSurfacePalette(
            isDark = false,
            canvas = OrbitLightSheetCanvas,
            panel = OrbitLightSheetPanel,
            tile = OrbitLightSheetTile,
            hairline = OrbitLightSheetHairline,
            topHighlight = OrbitLightSheetTopHighlight,
            scrim = OrbitLightSheetScrim,
            handle = OrbitLightSheetHandle,
            innerDivider = OrbitLightSheetHairline,
            activeFill = primary.copy(alpha = 0.12f),
            activeBorder = primary.copy(alpha = 0.48f)
        )
    }
}

@Composable
fun OrbitPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
) {
    val palette = rememberOrbitSurfacePalette()
    val containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Button(
            onClick = onClick,
            enabled = enabled && !isLoading,
            modifier = modifier.height(OrbitControlTokens.ControlHeight),
            shape = RoundedCornerShape(OrbitControlTokens.ControlCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = palette.tile,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            ),
            contentPadding = contentPadding
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 1.8.dp,
                    color = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OrbitSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
) {
    val palette = rememberOrbitSurfacePalette()
    val borderColor = when {
        !enabled -> palette.hairline.copy(alpha = 0.45f)
        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        else -> palette.hairline
    }
    val containerColor = when {
        !enabled -> palette.panel.copy(alpha = 0.5f)
        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = if (palette.isDark) 0.14f else 0.08f)
        else -> palette.tile.copy(alpha = 0.72f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled && !isLoading,
            modifier = modifier.height(OrbitControlTokens.ControlHeight),
            shape = RoundedCornerShape(OrbitControlTokens.ControlCornerRadius),
            border = BorderStroke(OrbitControlTokens.HairlineWidth, borderColor),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = palette.panel.copy(alpha = 0.5f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            ),
            contentPadding = contentPadding
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 1.8.dp,
                    color = iconTint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OrbitGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
) {
    val palette = rememberOrbitSurfacePalette()
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(OrbitControlTokens.ControlHeight),
            shape = RoundedCornerShape(OrbitControlTokens.ControlCornerRadius),
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (isDestructive) {
                    MaterialTheme.colorScheme.error.copy(alpha = if (palette.isDark) 0.10f else 0.06f)
                } else {
                    palette.tile.copy(alpha = 0.36f)
                },
                contentColor = tint,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            ),
            contentPadding = contentPadding
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val palette = rememberOrbitSurfacePalette()
    val primary = MaterialTheme.colorScheme.primary
    val unfocusedContainer = palette.panel
    val focusedContainer = if (palette.isDark) palette.tile else palette.canvas
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(OrbitControlTokens.ControlCornerRadius)
    val effectiveTextStyle = textStyle.merge(
        TextStyle(
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            fontSize = 13.5.sp
        )
    )
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = focusedContainer,
        unfocusedContainerColor = unfocusedContainer,
        disabledContainerColor = unfocusedContainer.copy(alpha = 0.5f),
        errorContainerColor = unfocusedContainer,
        focusedBorderColor = primary.copy(alpha = 0.88f),
        unfocusedBorderColor = palette.hairline,
        disabledBorderColor = palette.hairline.copy(alpha = 0.45f),
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedLabelColor = primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = primary,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = primary
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = OrbitControlTokens.InputMinHeight),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = effectiveTextStyle,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                isError = isError,
                label = label?.let { text ->
                    {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                placeholder = placeholder?.let { text ->
                    {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                supportingText = supportingText,
                colors = colors,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape,
                        focusedBorderThickness = 1.2.dp,
                        unfocusedBorderThickness = OrbitControlTokens.HairlineWidth
                    )
                }
            )
        }
    )
}

@Composable
fun OrbitSegmentPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val palette = rememberOrbitSurfacePalette()
    val primary = MaterialTheme.colorScheme.primary
    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> palette.panel.copy(alpha = 0.5f)
            selected -> primary.copy(alpha = if (palette.isDark) 0.22f else 0.14f)
            else -> palette.tile
        },
        animationSpec = tween(160),
        label = "orbit_segment_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> palette.hairline.copy(alpha = 0.4f)
            selected -> primary.copy(alpha = 0.6f)
            else -> palette.hairline
        },
        animationSpec = tween(160),
        label = "orbit_segment_border"
    )
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        selected -> primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(OrbitControlTokens.CapsuleHeight),
            shape = RoundedCornerShape(OrbitControlTokens.SegmentCornerRadius),
            color = bgColor,
            border = BorderStroke(
                width = if (selected) 1.dp else OrbitControlTokens.HairlineWidth,
                color = borderColor
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (selected || !enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun OrbitActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    OrbitSegmentPill(
        label = label,
        selected = false,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon
    )
}
