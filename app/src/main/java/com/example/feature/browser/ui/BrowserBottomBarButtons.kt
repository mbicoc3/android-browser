package com.example.feature.browser.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.theme.OrbitIncognitoPurple
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class TabGestureAxis {
    NONE,
    HORIZONTAL,
    VERTICAL
}

@Composable
internal fun BottomTabSwitchButton(
    tabsCount: Int,
    isIncognito: Boolean,
    iconColor: Color,
    onOpenTabs: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onSwitchTab: (Int) -> Unit,
    onOpenTools: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val badgeOffsetX = remember { Animatable(0f) }
    val maxVisualPx = remember(density) { with(density) { 11.dp.toPx() } }

    val currentTabsCount by rememberUpdatedState(tabsCount)
    val currentOnOpenTabs by rememberUpdatedState(onOpenTabs)
    val currentOnNewIncognitoTab by rememberUpdatedState(onNewIncognitoTab)
    val currentOnSwitchTab by rememberUpdatedState(onSwitchTab)
    val currentOnOpenTools by rememberUpdatedState(onOpenTools)
    val tabsDesc = stringResource(R.string.action_tabs)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .indication(interactionSource, LocalIndication.current)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$tabsDesc ($currentTabsCount)"
                onClick {
                    currentOnOpenTabs()
                    true
                }
                onLongClick {
                    currentOnNewIncognitoTab()
                    true
                }
            }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                val switchThresholdPx = 22.dp.toPx()
                val flickMinDragPx = 12.dp.toPx()
                val flickVelocityPx = 280.dp.toPx()
                val swipeUpToolsThresholdPx = 22.dp.toPx()
                val bounceSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 540f)
                val velocityTracker = VelocityTracker()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)

                    val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                    val startTime = SystemClock.uptimeMillis()
                    var axis = TabGestureAxis.NONE
                    var switched = false
                    var pressCancelled = false
                    var handledTerminalAction = false

                    while (true) {
                        val elapsed = SystemClock.uptimeMillis() - startTime
                        if (axis == TabGestureAxis.NONE && elapsed >= longPressTimeoutMs) {
                            handledTerminalAction = true
                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                            pressCancelled = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnNewIncognitoTab()
                            break
                        }

                        val event = if (axis == TabGestureAxis.NONE) {
                            val remaining = (longPressTimeoutMs - elapsed).coerceAtLeast(1L)
                            withTimeoutOrNull(remaining) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            if (axis == TabGestureAxis.NONE) {
                                handledTerminalAction = true
                                interactionSource.tryEmit(PressInteraction.Cancel(press))
                                pressCancelled = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnNewIncognitoTab()
                            }
                            break
                        }

                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            val upPos = change?.position ?: down.position
                            if (change != null) {
                                velocityTracker.addPosition(change.uptimeMillis, upPos)
                            }
                            if (axis == TabGestureAxis.HORIZONTAL) {
                                if (!switched && currentTabsCount > 1) {
                                    val finalDx = upPos.x - down.position.x
                                    val vx = velocityTracker.calculateVelocity().x
                                    val triggered = abs(finalDx) >= switchThresholdPx ||
                                        (abs(finalDx) >= flickMinDragPx && abs(vx) >= flickVelocityPx)
                                    if (triggered) {
                                        val dir = if (finalDx < 0f || (abs(finalDx) < 2f && vx < 0f)) 1 else -1
                                        currentOnSwitchTab(dir)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                coroutineScope.launch {
                                    badgeOffsetX.animateTo(0f, bounceSpring)
                                }
                            } else if (axis == TabGestureAxis.NONE) {
                                val dist = (upPos - down.position).getDistance()
                                if (dist <= touchSlop * 1.5f) {
                                    currentOnOpenTabs()
                                }
                            }
                            break
                        }

                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val totalDx = change.position.x - down.position.x
                        val totalDy = change.position.y - down.position.y

                        if (axis == TabGestureAxis.NONE) {
                            if (totalDy <= -swipeUpToolsThresholdPx && abs(totalDy) > abs(totalDx) * 1.15f) {
                                axis = TabGestureAxis.VERTICAL
                                handledTerminalAction = true
                                change.consume()
                                if (!pressCancelled) {
                                    interactionSource.tryEmit(PressInteraction.Cancel(press))
                                    pressCancelled = true
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnOpenTools()
                                break
                            }

                            if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * 1.15f) {
                                axis = TabGestureAxis.HORIZONTAL
                                change.consume()
                                if (!pressCancelled) {
                                    interactionSource.tryEmit(PressInteraction.Cancel(press))
                                    pressCancelled = true
                                }
                            }
                        }

                        if (axis == TabGestureAxis.HORIZONTAL) {
                            change.consume()
                            if (!switched) {
                                val visualLimit = if (currentTabsCount > 1) maxVisualPx else maxVisualPx * 0.35f
                                val visualTarget = (totalDx * 0.45f).coerceIn(-visualLimit, visualLimit)
                                coroutineScope.launch {
                                    badgeOffsetX.snapTo(visualTarget)
                                }

                                if (currentTabsCount > 1 && abs(totalDx) >= switchThresholdPx) {
                                    switched = true
                                    val dir = if (totalDx < 0f) 1 else -1
                                    currentOnSwitchTab(dir)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        badgeOffsetX.snapTo(-dir * (maxVisualPx * 0.65f))
                                        badgeOffsetX.animateTo(0f, bounceSpring)
                                    }
                                }
                            }
                        }
                    }

                    if (!pressCancelled) {
                        interactionSource.tryEmit(
                            if (handledTerminalAction) PressInteraction.Cancel(press)
                            else PressInteraction.Release(press)
                        )
                    }
                    if (handledTerminalAction) {
                        while (true) {
                            val drain = awaitPointerEvent()
                            if (drain.changes.none { it.pressed }) break
                        }
                    }
                }
            }
            .testTag("bottom_tabs_button"),
        contentAlignment = Alignment.Center
    ) {
        val badgeColor = if (isIncognito) OrbitIncognitoPurple else iconColor
        Surface(
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(width = 1.8.dp, color = badgeColor),
            color = Color.Transparent,
            modifier = Modifier
                .size(23.dp)
                .graphicsLayer {
                    val offsetPx = badgeOffsetX.value
                    translationX = offsetPx
                    val progress = (abs(offsetPx) / maxVisualPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val squeeze = 1f - (progress * 0.07f)
                    scaleX = squeeze
                    scaleY = squeeze
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (tabsCount > 99) "99+" else tabsCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}

@Composable
internal fun BottomNewTabHomeButton(
    isStartPage: Boolean,
    iconColor: Color,
    onNewTab: () -> Unit,
    onGoHome: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val currentIsStartPage by rememberUpdatedState(isStartPage)
    val currentOnNewTab by rememberUpdatedState(onNewTab)
    val currentOnGoHome by rememberUpdatedState(onGoHome)
    val newTabDesc = stringResource(R.string.tabs_new)
    val newTabOrHomeDesc = stringResource(R.string.action_new_tab_or_home)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .indication(interactionSource, LocalIndication.current)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (currentIsStartPage) newTabDesc else newTabOrHomeDesc
                onClick {
                    currentOnNewTab()
                    true
                }
                if (!currentIsStartPage) {
                    onLongClick {
                        currentOnGoHome()
                        true
                    }
                }
            }
            .pointerInput(Unit) {
                val swipeThresholdPx = 18.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                    var triggeredHome = false
                    val startTime = SystemClock.uptimeMillis()

                    while (true) {
                        val elapsed = SystemClock.uptimeMillis() - startTime
                        if (!currentIsStartPage && elapsed >= longPressTimeoutMs) {
                            triggeredHome = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnGoHome()
                            break
                        }

                        val event = if (!currentIsStartPage) {
                            val remaining = (longPressTimeoutMs - elapsed).coerceAtLeast(1L)
                            withTimeoutOrNull(remaining) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            if (!currentIsStartPage) {
                                triggeredHome = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnGoHome()
                            }
                            break
                        }

                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            val upPos = change?.position ?: down.position
                            val totalDrag = (upPos - down.position).getDistance()
                            if (!currentIsStartPage && totalDrag >= swipeThresholdPx) {
                                triggeredHome = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnGoHome()
                            } else if (totalDrag <= viewConfiguration.touchSlop * 2f) {
                                currentOnNewTab()
                            }
                            break
                        }

                        val dragDist = (change.position - down.position).getDistance()
                        if (!currentIsStartPage && dragDist >= swipeThresholdPx) {
                            triggeredHome = true
                            change.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnGoHome()
                            break
                        }
                    }

                    if (triggeredHome) {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                        while (true) {
                            val drain = awaitPointerEvent()
                            if (drain.changes.none { it.pressed }) break
                        }
                    } else {
                        interactionSource.tryEmit(PressInteraction.Release(press))
                    }
                }
            }
            .testTag("bottom_new_tab_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = iconColor
        )
    }
}
