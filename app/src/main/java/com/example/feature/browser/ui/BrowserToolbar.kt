package com.example.feature.browser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.example.R
import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitControlTokens
import com.example.core.ui.components.OrbitSearchPillSurface
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitIncognitoPurple
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.OmniboxLocalMatch

@Composable
fun BrowserOmniboxBar(
    activeTab: BrowserTabModel?,
    omniboxText: String,
    isOmniboxFocused: Boolean,
    isQuickPanelOpen: Boolean,
    isBookmarked: Boolean,
    lastShareComponent: String,
    lastShareAppLabel: String,
    activeSearchEngine: SearchEngine,
    suggestions: List<String>,
    localMatches: List<OmniboxLocalMatch>,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onToggleQuickPanel: () -> Unit,
    onSubmit: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenTabs: () -> Unit = {},
    onSwitchTab: (Int) -> Unit = {},
    onInspectSiteData: (() -> Unit)? = null,
    onDirectImageDownload: () -> Unit = {},
    multiPurposeCallbacks: OmniboxMultiPurposeCallbacks,
    pageMenuCallbacks: BrowserPageMenuCallbacks,
    modifier: Modifier = Modifier
) {
    val currentUrl = activeTab?.url.orEmpty()
    val isStartPage = UrlUtils.isStartPage(currentUrl)
    val isSecure = UrlUtils.isSecureHttps(currentUrl)
    val isIncognito = activeTab?.isIncognito == true
    val directImage = activeTab?.directImage
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val currentFocused by rememberUpdatedState(isOmniboxFocused)
    val currentQuickOpen by rememberUpdatedState(isQuickPanelOpen)
    val currentOnOpenTabs by rememberUpdatedState(onOpenTabs)
    val currentOnSwitchTab by rememberUpdatedState(onSwitchTab)
    var showMoreMenu by remember { mutableStateOf(false) }
    val pillScope = rememberCoroutineScope()
    val pillOffsetX = remember { Animatable(0f) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OrbitSpacing.sm, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbitSearchPillSurface(
                    focused = isOmniboxFocused || isQuickPanelOpen,
                    height = OrbitControlTokens.OmniboxHeight,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { translationX = pillOffsetX.value }
                        .pointerInput(Unit) {
                            val touchSlop = viewConfiguration.touchSlop
                            val horizThreshold = 28.dp.toPx()
                            val downThreshold = 24.dp.toPx()
                            val maxSlidePx = 12.dp.toPx()
                            val settleSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 560f)
                            val velocityTracker = VelocityTracker()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (currentFocused || currentQuickOpen) return@awaitEachGesture
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                var trackingHoriz = false
                                var switched = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed || change.isConsumed) {
                                        if (trackingHoriz && !switched && change != null) {
                                            val dx = change.position.x - down.position.x
                                            val vx = velocityTracker.calculateVelocity().x
                                            if (abs(dx) >= 14.dp.toPx() && abs(vx) >= 320.dp.toPx()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentOnSwitchTab(if (dx < 0f) 1 else -1)
                                            }
                                        }
                                        if (trackingHoriz) {
                                            pillScope.launch { pillOffsetX.animateTo(0f, settleSpring) }
                                        }
                                        break
                                    }
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (!trackingHoriz && dy >= downThreshold && abs(dy) > abs(dx) * 1.2f) {
                                        change.consume()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentOnOpenTabs()
                                        break
                                    }
                                    if (!trackingHoriz && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.25f) {
                                        trackingHoriz = true
                                    }
                                    if (trackingHoriz) {
                                        change.consume()
                                        pillScope.launch {
                                            pillOffsetX.snapTo((dx * 0.35f).coerceIn(-maxSlidePx, maxSlidePx))
                                        }
                                        if (abs(dx) >= horizThreshold) {
                                            switched = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentOnSwitchTab(if (dx < 0f) 1 else -1)
                                            pillScope.launch { pillOffsetX.animateTo(0f, settleSpring) }
                                            break
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val inspectAction = if (!isStartPage) onInspectSiteData else null
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 38.dp)
                                .then(
                                    if (inspectAction != null) {
                                        Modifier
                                            .clickable(onClick = inspectAction)
                                            .testTag("omnibox_site_data_button")
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isIncognito -> Icons.Rounded.Security
                                    isStartPage -> Icons.Rounded.Search
                                    isSecure -> Icons.Rounded.Lock
                                    else -> Icons.Rounded.Public
                                },
                                contentDescription = if (inspectAction != null) {
                                    stringResource(R.string.site_data_inspect_icon_desc)
                                } else {
                                    null
                                },
                                tint = when {
                                    isIncognito -> OrbitIncognitoPurple
                                    isSecure -> OrbitEmerald
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (omniboxText.isEmpty() && !isOmniboxFocused) {
                                Text(
                                    text = if (isStartPage) {
                                        stringResource(R.string.omnibox_placeholder)
                                    } else {
                                        UrlUtils.extractHost(currentUrl)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = if (isOmniboxFocused) omniboxText else if (isStartPage) "" else UrlUtils.extractHost(currentUrl),
                                onValueChange = onTextChange,
                                singleLine = true,
                                textStyle = OrbitMonoStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { onFocusChange(it.isFocused) }
                                    .testTag("omnibox_input")
                            )
                        }

                        if (!isOmniboxFocused && !isStartPage && !activeTab?.sandboxName.isNullOrBlank()) {
                            val sbColor = Color(activeTab?.sandboxColorHex ?: 0xFF38BDF8)
                            OrbitBadgeChip(
                                text = activeTab?.sandboxName.orEmpty(),
                                accentColor = sbColor,
                                leadingDot = true,
                                onClick = pageMenuCallbacks.onOpenSandboxPicker,
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .testTag("omnibox_sandbox_badge")
                            )
                        } else if (!isOmniboxFocused && !isStartPage && directImage != null) {
                            OrbitBadgeChip(
                                text = directImage.dimensionLabel,
                                accentColor = MaterialTheme.colorScheme.primary,
                                leadingIcon = Icons.Rounded.Image,
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .testTag("omnibox_image_dimension_badge")
                            )
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                IconButton(
                                    onClick = onDirectImageDownload,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("omnibox_direct_image_download_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = stringResource(R.string.omnibox_action_download),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else if (!isOmniboxFocused && !isStartPage && (activeTab?.blockedTrackersCount ?: 0) > 0) {
                            Text(
                                text = "${activeTab?.blockedTrackersCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrbitEmerald,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }

                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            if (isOmniboxFocused) {
                                if (omniboxText.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onTextChange("") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.action_clear),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                    if (clipText.isNotBlank()) {
                                        IconButton(
                                            onClick = { multiPurposeCallbacks.onPasteAndGo(clipText) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentPasteGo,
                                                contentDescription = stringResource(R.string.omnibox_action_paste_go),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (!isStartPage) {
                                IconButton(
                                    onClick = onToggleQuickPanel,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("omnibox_quick_actions_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = stringResource(R.string.omnibox_url_actions_desc),
                                        tint = if (isQuickPanelOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onReloadOrStop,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("omnibox_reload_button")
                                ) {
                                    Icon(
                                        imageVector = if (activeTab?.isLoading == true) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.action_reload),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isStartPage) {
                    Box {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier
                                    .size(width = 36.dp, height = 38.dp)
                                    .testTag("omnibox_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_options),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        BrowserPageMoreMenu(
                            expanded = showMoreMenu,
                            onDismiss = { showMoreMenu = false },
                            currentUrl = currentUrl,
                            isBookmarked = isBookmarked,
                            isDesktopMode = activeTab?.isDesktopMode == true,
                            isDirectImage = directImage != null,
                            activeSandboxName = activeTab?.sandboxName,
                            lastShareComponent = lastShareComponent,
                            lastShareAppLabel = lastShareAppLabel,
                            callbacks = pageMenuCallbacks
                        )
                    }
                }
            }

            if (activeTab?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress.coerceIn(5, 100)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (isOmniboxFocused || isQuickPanelOpen) {
                OmniboxMultiPurposePanel(
                    query = omniboxText,
                    currentUrl = currentUrl,
                    isStartPage = isStartPage,
                    isDesktopMode = activeTab?.isDesktopMode == true,
                    activeSearchEngine = activeSearchEngine,
                    directImage = directImage,
                    localMatches = localMatches,
                    suggestions = suggestions,
                    isInputFocused = isOmniboxFocused,
                    callbacks = multiPurposeCallbacks
                )
            }
        }
    }
}
