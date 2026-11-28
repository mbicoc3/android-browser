package com.example.feature.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.ui.components.OrbitActionPill
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.browser.model.DirectImageInfo
import com.example.feature.browser.model.OmniboxLocalMatch
import com.example.feature.browser.model.OmniboxSlashCommand
import com.example.feature.browser.model.OmniboxSmartHelper

data class OmniboxMultiPurposeCallbacks(
    val onPasteAndGo: (String) -> Unit,
    val onCopyCurrentUrl: () -> Unit,
    val onDownloadCurrentUrl: () -> Unit,
    val onCollectCurrentImageUrl: () -> Unit = {},
    val onShareCurrentUrl: () -> Unit,
    val onInspectSiteData: () -> Unit,
    val onFindInPage: (String) -> Unit,
    val onToggleDesktopMode: () -> Unit,
    val onPinToHome: () -> Unit,
    val onOpenInPrivateTab: () -> Unit,
    val onSearchWithEngine: (String, SearchEngine) -> Unit,
    val onLocalMatchClick: (String) -> Unit,
    val onSuggestionClick: (String) -> Unit,
    val onFillOmniboxText: (String) -> Unit,
    val onCopyText: (String) -> Unit,
    val onExecuteSlashCommand: (OmniboxSlashCommand) -> Unit
)

@Composable
fun OmniboxMultiPurposePanel(
    query: String,
    currentUrl: String,
    isStartPage: Boolean,
    isDesktopMode: Boolean,
    activeSearchEngine: SearchEngine,
    directImage: DirectImageInfo?,
    localMatches: List<OmniboxLocalMatch>,
    suggestions: List<String>,
    isInputFocused: Boolean,
    callbacks: OmniboxMultiPurposeCallbacks,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val clipboardText = clipboardManager.getText()?.text?.trim().orEmpty()
    val trimmedQuery = query.trim()

    val smartAnswer = remember(trimmedQuery) {
        if (isInputFocused) OmniboxSmartHelper.evaluateSmartQuery(trimmedQuery) else null
    }
    val slashCommands = remember(trimmedQuery, isStartPage) {
        if (isInputFocused) OmniboxSmartHelper.matchCommands(trimmedQuery, isPageActive = !isStartPage) else emptyList()
    }
    val showEngineSwitcher = isInputFocused &&
        trimmedQuery.isNotBlank() &&
        !trimmedQuery.startsWith("/") &&
        !UrlUtils.looksLikeDirectUrl(trimmedQuery) &&
        trimmedQuery != currentUrl

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = OrbitSpacing.xs)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        if (!isStartPage && directImage != null) {
            DirectImageDetailCard(
                info = directImage,
                onCollect = callbacks.onCollectCurrentImageUrl,
                onDownload = callbacks.onDownloadCurrentUrl,
                onCopyUrl = callbacks.onCopyCurrentUrl
            )
        }

        if (smartAnswer != null) {
            OrbitSurfaceCard(
                selected = true,
                accentColor = MaterialTheme.colorScheme.secondary,
                onClick = { callbacks.onCopyText(smartAnswer.value) },
                modifier = Modifier
                    .padding(horizontal = OrbitSpacing.sm, vertical = 6.dp)
                    .testTag("omnibox_smart_answer_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = smartAnswer.value,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = smartAnswer.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.omnibox_action_copy_url),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        QuickActionChipStrip(
            isStartPage = isStartPage,
            isDesktopMode = isDesktopMode,
            hasClipboard = clipboardText.isNotBlank(),
            onPasteAndGo = { callbacks.onPasteAndGo(clipboardText) },
            onShowCommands = { callbacks.onFillOmniboxText("/") },
            callbacks = callbacks
        )

        if (showEngineSwitcher) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = OrbitSpacing.sm, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(SearchEngine.entries) { engine ->
                    OrbitSegmentPill(
                        label = engine.displayName,
                        selected = engine == activeSearchEngine,
                        onClick = { callbacks.onSearchWithEngine(trimmedQuery, engine) },
                        modifier = Modifier.testTag("omnibox_engine_${engine.id}")
                    )
                }
            }
        }

        slashCommands.forEach { command ->
            OmniboxActionRow(
                icon = Icons.Rounded.Terminal,
                title = "${command.trigger}  ·  ${command.label}",
                subtitle = command.description,
                badge = "CMD",
                onClick = { callbacks.onExecuteSlashCommand(command) },
                modifier = Modifier.testTag("omnibox_cmd_${command.trigger.removePrefix("/")}")
            )
        }

        if (isInputFocused) {
            localMatches.forEach { match ->
                OmniboxActionRow(
                    icon = if (match.isBookmark) Icons.Rounded.Bookmark else Icons.Rounded.History,
                    title = match.title,
                    subtitle = match.url,
                    badge = if (match.isBookmark) "Saved" else "History",
                    onClick = { callbacks.onLocalMatchClick(match.url) },
                    tintPrimary = match.isBookmark
                )
            }

            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { callbacks.onSuggestionClick(suggestion) }
                        .padding(start = OrbitSpacing.md, end = OrbitSpacing.xs, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { callbacks.onFillOmniboxText(suggestion) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NorthWest,
                            contentDescription = "Edit suggestion",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (!isStartPage &&
                trimmedQuery.length >= 2 &&
                !trimmedQuery.startsWith("/") &&
                trimmedQuery != currentUrl
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                OmniboxActionRow(
                    icon = Icons.Rounded.FindInPage,
                    title = "Find \"$trimmedQuery\" on this page",
                    subtitle = UrlUtils.extractHost(currentUrl),
                    badge = "Page",
                    onClick = { callbacks.onFindInPage(trimmedQuery) },
                    modifier = Modifier.testTag("omnibox_find_on_page_row")
                )
            }
        }
    }
}

@Composable
private fun DirectImageDetailCard(
    info: DirectImageInfo,
    onCollect: () -> Unit,
    onDownload: () -> Unit,
    onCopyUrl: () -> Unit
) {
    OrbitSurfaceCard(
        selected = true,
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = OrbitSpacing.sm, vertical = 6.dp)
            .testTag("omnibox_direct_image_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (info.hasValidDimensions) {
                        "${info.width} × ${info.height} px"
                    } else {
                        "Direct Image"
                    },
                    style = OrbitMonoStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = OmniboxSmartHelper.formatImageSummary(info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OrbitGhostButton(
                text = stringResource(R.string.collections_collect_action),
                onClick = onCollect,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.testTag("omnibox_direct_image_collect_button")
            )
            OrbitGhostButton(
                text = stringResource(R.string.omnibox_action_copy_url),
                onClick = onCopyUrl,
                contentPadding = PaddingValues(horizontal = 8.dp)
            )
            OrbitGhostButton(
                text = stringResource(R.string.omnibox_action_download),
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun QuickActionChipStrip(
    isStartPage: Boolean,
    isDesktopMode: Boolean,
    hasClipboard: Boolean,
    onPasteAndGo: () -> Unit,
    onShowCommands: () -> Unit,
    callbacks: OmniboxMultiPurposeCallbacks
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("omnibox_quick_action_strip"),
        contentPadding = PaddingValues(horizontal = OrbitSpacing.sm, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (hasClipboard) {
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_paste_go),
                    icon = Icons.Rounded.ContentPasteGo,
                    onClick = onPasteAndGo,
                    testTag = "omnibox_action_paste_go"
                )
            }
        }
        if (!isStartPage) {
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_copy_url),
                    icon = Icons.Rounded.ContentCopy,
                    onClick = callbacks.onCopyCurrentUrl,
                    testTag = "omnibox_action_copy_url"
                )
            }
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_download),
                    icon = Icons.Rounded.Download,
                    onClick = callbacks.onDownloadCurrentUrl,
                    testTag = "omnibox_action_download"
                )
            }
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_share),
                    icon = Icons.Rounded.Share,
                    onClick = callbacks.onShareCurrentUrl,
                    testTag = "omnibox_action_share"
                )
            }
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_site_data),
                    icon = Icons.Rounded.Storage,
                    onClick = callbacks.onInspectSiteData,
                    testTag = "omnibox_action_site_data"
                )
            }
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_find),
                    icon = Icons.Rounded.FindInPage,
                    onClick = { callbacks.onFindInPage("") },
                    testTag = "omnibox_action_find"
                )
            }
            item {
                OrbitSegmentPill(
                    label = stringResource(R.string.omnibox_action_desktop),
                    selected = isDesktopMode,
                    leadingIcon = Icons.Rounded.DesktopWindows,
                    onClick = callbacks.onToggleDesktopMode,
                    modifier = Modifier.testTag("omnibox_action_desktop")
                )
            }
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_pin),
                    icon = Icons.Rounded.PushPin,
                    onClick = callbacks.onPinToHome,
                    testTag = "omnibox_action_pin"
                )
            }
            item {
                QuickChip(
                    label = stringResource(R.string.omnibox_action_private),
                    icon = Icons.Rounded.Security,
                    onClick = callbacks.onOpenInPrivateTab,
                    testTag = "omnibox_action_private"
                )
            }
        }
        item {
            QuickChip(
                label = "/ Commands",
                icon = Icons.Rounded.Terminal,
                onClick = onShowCommands,
                testTag = "omnibox_action_commands"
            )
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    OrbitActionPill(
        label = label,
        leadingIcon = icon,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun OmniboxActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tintPrimary: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = OrbitSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (tintPrimary) OrbitEmerald else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OrbitBadgeChip(text = badge)
    }
}
