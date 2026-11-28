package com.example.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import com.example.core.ui.theme.OrbitDarkSheetActiveFill
import com.example.core.ui.theme.OrbitDarkSheetCanvas
import com.example.core.ui.theme.OrbitDarkSheetHairline
import com.example.core.ui.theme.OrbitDarkSheetHandle
import com.example.core.ui.theme.OrbitDarkSheetPanel
import com.example.core.ui.theme.OrbitDarkSheetScrim
import com.example.core.ui.theme.OrbitDarkSheetTile
import com.example.core.ui.theme.OrbitDarkSheetTopHighlight
import com.example.core.ui.theme.OrbitLightSheetActiveFill
import com.example.core.ui.theme.OrbitLightSheetCanvas
import com.example.core.ui.theme.OrbitLightSheetHairline
import com.example.core.ui.theme.OrbitLightSheetHandle
import com.example.core.ui.theme.OrbitLightSheetPanel
import com.example.core.ui.theme.OrbitLightSheetScrim
import com.example.core.ui.theme.OrbitLightSheetTile
import com.example.core.ui.theme.OrbitLightSheetTopHighlight

object OrbitControlTokens {
    val ControlHeight = 40.dp
    val InputMinHeight = 42.dp
    val CapsuleHeight = 32.dp
    val OmniboxHeight = 40.dp
    val StartSearchHeight = 52.dp
    val ControlCornerRadius = 12.dp
    val SegmentCornerRadius = 16.dp
    val CardCornerRadius = 16.dp
    val PanelCornerRadius = 16.dp
    val DialogCornerRadius = 22.dp
    val PopupCornerRadius = 16.dp
    val SheetCornerRadius = 24.dp
    val FabCornerRadius = 16.dp
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
            activeFill = OrbitDarkSheetActiveFill,
            activeBorder = primary.copy(alpha = 0.72f)
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
            activeFill = OrbitLightSheetActiveFill,
            activeBorder = primary.copy(alpha = 0.72f)
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
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
                Spacer(modifier = Modifier.width(6.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
) {
    val palette = rememberOrbitSurfacePalette()
    val borderColor = when {
        !enabled -> palette.hairline.copy(alpha = 0.4f)
        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
        else -> palette.hairline
    }
    val containerColor = when {
        !enabled -> palette.panel.copy(alpha = 0.5f)
        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = if (palette.isDark) 0.12f else 0.06f)
        else -> palette.panel
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                Spacer(modifier = Modifier.width(6.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
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
                    MaterialTheme.colorScheme.error.copy(alpha = if (palette.isDark) 0.10f else 0.05f)
                } else {
                    Color.Transparent
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
                    modifier = Modifier.size(16.dp)
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
fun OrbitFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(OrbitControlTokens.FabCornerRadius),
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        )
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
fun OrbitExtendedFloatingActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(OrbitControlTokens.FabCornerRadius),
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        ),
        icon = { Icon(imageVector = icon, contentDescription = null) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    )
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
        focusedBorderColor = primary.copy(alpha = 0.9f),
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
fun OrbitSearchPillSurface(
    focused: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = OrbitControlTokens.OmniboxHeight,
    shape: Shape = RoundedCornerShape(OrbitControlTokens.OmniboxHeight / 2),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
    focusedBorderColor: Color = MaterialTheme.colorScheme.primary,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.height(height),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = if (focused) 1.2.dp else OrbitControlTokens.HairlineWidth,
            color = if (focused) focusedBorderColor else borderColor
        ),
        shadowElevation = shadowElevation
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}
