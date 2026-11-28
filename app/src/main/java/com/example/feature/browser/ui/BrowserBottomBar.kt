package com.example.feature.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.components.rememberOrbitSurfacePalette
import com.example.core.ui.theme.OrbitSpacing
import kotlin.math.abs

@Composable
fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabsCount: Int,
    isIncognito: Boolean,
    isStartPage: Boolean,
    isLightSolidStart: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
    onGoHome: () -> Unit,
    onOpenTabs: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onSwitchTab: (Int) -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberOrbitSurfacePalette()
    val barColor = if (isStartPage) Color.Transparent else MaterialTheme.colorScheme.surface
    val iconColor = when {
        isStartPage && isLightSolidStart -> MaterialTheme.colorScheme.onBackground
        isStartPage -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val disabledColor = iconColor.copy(alpha = 0.35f)
    val haptic = LocalHapticFeedback.current
    val currentTabsCount by rememberUpdatedState(tabsCount)
    val currentOnOpenTools by rememberUpdatedState(onOpenTools)
    val currentOnSwitchTab by rememberUpdatedState(onSwitchTab)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                val swipeUpThresholdPx = 22.dp.toPx()
                val swipeHorizThresholdPx = 34.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed || change.isConsumed) break

                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y

                        if (dy <= -swipeUpThresholdPx && abs(dy) > abs(dx) * 1.15f) {
                            change.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnOpenTools()
                            break
                        }

                        if (currentTabsCount > 1 &&
                            abs(dx) >= swipeHorizThresholdPx &&
                            abs(dx) > abs(dy) * 1.25f
                        ) {
                            change.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnSwitchTab(if (dx < 0f) 1 else -1)
                            break
                        }
                    }
                }
            },
        color = barColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (!isStartPage) {
                HorizontalDivider(thickness = 0.6.dp, color = palette.hairline)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 3.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.22f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = OrbitSpacing.sm),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.testTag("bottom_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = if (canGoBack) iconColor else disabledColor
                    )
                }
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.testTag("bottom_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.action_forward),
                        tint = if (canGoForward) iconColor else disabledColor
                    )
                }
                BottomNewTabHomeButton(
                    isStartPage = isStartPage,
                    iconColor = iconColor,
                    onNewTab = onNewTab,
                    onGoHome = onGoHome
                )
                BottomTabSwitchButton(
                    tabsCount = tabsCount,
                    isIncognito = isIncognito,
                    iconColor = iconColor,
                    onOpenTabs = onOpenTabs,
                    onNewIncognitoTab = onNewIncognitoTab,
                    onSwitchTab = onSwitchTab,
                    onOpenTools = onOpenTools
                )
                IconButton(
                    onClick = onOpenTools,
                    modifier = Modifier.testTag("bottom_tools_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.action_tools),
                        tint = iconColor
                    )
                }
            }
        }
    }
}
