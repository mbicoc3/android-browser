package com.example.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

typealias OrbitSheetPalette = OrbitSurfacePalette

@Composable
fun rememberOrbitSheetPalette(): OrbitSheetPalette = rememberOrbitSurfacePalette()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.(OrbitSheetPalette) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val palette = rememberOrbitSheetPalette()
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = palette.canvas,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = palette.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = palette.topHighlight,
                            start = Offset(size.width * 0.14f, 0.5f),
                            end = Offset(size.width * 0.86f, 0.5f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 3.5.dp)
                        .clip(CircleShape)
                        .background(palette.handle)
                )
            }
        }
    ) {
        val scrollModifier = if (scrollable) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = if (palette.isDark) 0.08f else 0.05f),
                                secondary.copy(alpha = if (palette.isDark) 0.03f else 0.015f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, 0f),
                            radius = size.width * 0.85f
                        )
                    )
                }
                .then(scrollModifier)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = 0.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content(palette)
        }
    }
}

@Composable
fun OrbitSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val palette = rememberOrbitSheetPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = if (palette.isDark) 0.16f else 0.12f))
                    .border(
                        width = 0.8.dp,
                        color = iconTint.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = trailing
            )
        }
    }
}

@Composable
fun OrbitSheetKicker(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
        modifier = modifier.padding(horizontal = 2.dp)
    )
}

@Composable
fun OrbitSheetPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = rememberOrbitSheetPalette()
    val containerColor by animateColorAsState(
        targetValue = if (selected) palette.activeFill else palette.panel,
        animationSpec = tween(180),
        label = "sheet_panel_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.activeBorder else palette.hairline,
        animationSpec = tween(180),
        label = "sheet_panel_border"
    )
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(width = if (selected) 1.dp else OrbitControlTokens.HairlineWidth, color = borderColor)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = border
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = border
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun OrbitSheetSegmentPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) = OrbitSegmentPill(
    label = label,
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    leadingIcon = leadingIcon
)

@Composable
fun OrbitSheetPagerTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "sheet_tab"
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            OrbitSegmentPill(
                label = label,
                selected = selectedIndex == index,
                onClick = { onSelectTab(index) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_${label.lowercase()}")
            )
        }
    }
}

@Composable
fun OrbitSheetHairlineDivider(
    modifier: Modifier = Modifier,
    insetStart: Boolean = true
) {
    val palette = rememberOrbitSheetPalette()
    HorizontalDivider(
        modifier = modifier.padding(
            start = if (insetStart) 48.dp else 12.dp,
            end = 12.dp
        ),
        thickness = 0.7.dp,
        color = palette.innerDivider
    )
}

@Composable
fun OrbitSheetStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val palette = rememberOrbitSheetPalette()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = if (palette.isDark) 0.16f else 0.11f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun OrbitSheetPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) = OrbitPrimaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    icon = icon
)

@Composable
fun OrbitSheetSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) = OrbitSecondaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
)
