package com.example.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun OrbitDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    iconTint: Color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    showCloseButton: Boolean = false,
    scrollable: Boolean = true,
    maxWidth: Dp = 420.dp,
    maxBodyHeight: Dp = 440.dp,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.(OrbitSurfacePalette) -> Unit
) {
    val palette = rememberOrbitSurfacePalette()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = maxWidth),
            shape = RoundedCornerShape(OrbitControlTokens.DialogCornerRadius),
            color = palette.canvas,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(OrbitControlTokens.HairlineWidth, palette.hairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(palette.tile),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showCloseButton) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                val bodyModifier = if (scrollable) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = maxBodyHeight)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(
                    modifier = bodyModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content(palette)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
fun OrbitConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Cancel",
    icon: ImageVector? = Icons.Rounded.Warning,
    isDestructive: Boolean = true,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
    acknowledgeText: String? = null,
    acknowledged: Boolean = false,
    onAcknowledgedChange: ((Boolean) -> Unit)? = null
) {
    OrbitDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = icon,
        isDestructive = isDestructive,
        modifier = modifier,
        actions = {
            OrbitSecondaryButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                enabled = confirmEnabled && (acknowledgeText == null || acknowledged),
                isDestructive = isDestructive,
                modifier = Modifier
                    .weight(1f)
                    .then(if (confirmTestTag != null) Modifier.testTag(confirmTestTag) else Modifier)
            )
        }
    ) { _ ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!acknowledgeText.isNullOrBlank() && onAcknowledgedChange != null) {
            OrbitSheetPanel {
                OrbitCheckboxRow(
                    title = acknowledgeText,
                    checked = acknowledged,
                    onCheckedChange = onAcknowledgedChange,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
