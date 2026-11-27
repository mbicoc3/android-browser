package com.example.feature.browser.ui

import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.core.common.OrbitShareHelper
import com.example.core.common.UrlUtils
import com.example.core.ui.components.OrbitControlTokens
import com.example.core.ui.components.OrbitPopupMenu
import com.example.core.ui.components.OrbitPopupMenuItem
import com.example.core.ui.components.rememberOrbitSurfacePalette

data class BrowserPageMenuCallbacks(
    val onToggleBookmark: () -> Unit,
    val onShare: () -> Unit,
    val onQuickShare: () -> Unit,
    val onToggleDesktopMode: () -> Unit,
    val onAddToSpeedDial: () -> Unit,
    val onFindInPage: () -> Unit,
    val onSaveOffline: () -> Unit,
    val onOpenSiteData: () -> Unit,
    val onOpenSandboxPicker: () -> Unit = {}
)

@Composable
fun BrowserPageMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    currentUrl: String,
    isBookmarked: Boolean,
    isDesktopMode: Boolean,
    activeSandboxName: String? = null,
    lastShareComponent: String,
    lastShareAppLabel: String,
    callbacks: BrowserPageMenuCallbacks
) {
    val context = LocalContext.current
    val palette = rememberOrbitSurfacePalette()
    val isOfflineArchive = UrlUtils.isOfflineArchiveUrl(currentUrl)
    val hasQuickTarget = remember(lastShareComponent) {
        val comp = ComponentName.unflattenFromString(lastShareComponent)
        comp != null && OrbitShareHelper.isComponentAvailable(context, comp)
    }
    val quickAppIcon = remember(lastShareComponent, hasQuickTarget) {
        if (!hasQuickTarget) null
        else OrbitShareHelper.resolveComponentIcon(context, lastShareComponent)
            ?.let { drawable -> runCatching { drawable.toBitmap(64, 64).asImageBitmap() }.getOrNull() }
    }

    OrbitPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("browser_page_more_menu")
    ) {
        OrbitPopupMenuItem(
            text = if (isBookmarked) {
                stringResource(R.string.more_menu_bookmarked)
            } else {
                stringResource(R.string.more_menu_add_bookmark)
            },
            icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            active = isBookmarked,
            onClick = {
                onDismiss()
                callbacks.onToggleBookmark()
            },
            modifier = Modifier.testTag("more_menu_bookmark")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = OrbitControlTokens.ControlHeight)
                .testTag("more_menu_share_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = OrbitControlTokens.ControlHeight)
                    .clickable {
                        onDismiss()
                        callbacks.onShare()
                    }
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    .testTag("more_menu_share_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.tile.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.more_menu_share),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            val quickShareDesc = if (hasQuickTarget && lastShareAppLabel.isNotBlank()) {
                stringResource(R.string.more_menu_quick_share_app, lastShareAppLabel)
            } else {
                stringResource(R.string.more_menu_quick_share)
            }
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        onDismiss()
                        callbacks.onQuickShare()
                    }
                    .testTag("more_menu_quick_share_button"),
                contentAlignment = Alignment.Center
            ) {
                if (quickAppIcon != null) {
                    Image(
                        bitmap = quickAppIcon,
                        contentDescription = quickShareDesc,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = quickShareDesc,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            thickness = 0.7.dp,
            color = palette.innerDivider
        )

        OrbitPopupMenuItem(
            text = stringResource(R.string.more_menu_desktop_agent),
            icon = Icons.Rounded.DesktopWindows,
            active = isDesktopMode,
            trailing = {
                Switch(
                    checked = isDesktopMode,
                    onCheckedChange = null
                )
            },
            onClick = {
                onDismiss()
                callbacks.onToggleDesktopMode()
            },
            modifier = Modifier.testTag("more_menu_desktop_agent")
        )

        OrbitPopupMenuItem(
            text = stringResource(R.string.more_menu_add_speed_dial),
            icon = Icons.Rounded.PushPin,
            enabled = !isOfflineArchive,
            onClick = {
                onDismiss()
                callbacks.onAddToSpeedDial()
            },
            modifier = Modifier.testTag("more_menu_speed_dial")
        )

        OrbitPopupMenuItem(
            text = stringResource(R.string.more_menu_find_page),
            icon = Icons.Rounded.FindInPage,
            onClick = {
                onDismiss()
                callbacks.onFindInPage()
            },
            modifier = Modifier.testTag("more_menu_find_in_page")
        )

        OrbitPopupMenuItem(
            text = stringResource(R.string.more_menu_save_offline),
            icon = Icons.Rounded.OfflinePin,
            enabled = !isOfflineArchive,
            onClick = {
                onDismiss()
                callbacks.onSaveOffline()
            },
            modifier = Modifier.testTag("more_menu_save_offline")
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            thickness = 0.7.dp,
            color = palette.innerDivider
        )

        OrbitPopupMenuItem(
            text = if (!activeSandboxName.isNullOrBlank()) {
                stringResource(R.string.more_menu_sandbox_active, activeSandboxName)
            } else {
                stringResource(R.string.more_menu_sandbox)
            },
            icon = Icons.Rounded.Layers,
            active = !activeSandboxName.isNullOrBlank(),
            enabled = !isOfflineArchive,
            onClick = {
                onDismiss()
                callbacks.onOpenSandboxPicker()
            },
            modifier = Modifier.testTag("more_menu_sandbox")
        )

        OrbitPopupMenuItem(
            text = stringResource(R.string.more_menu_site_data),
            icon = Icons.Rounded.Storage,
            enabled = !isOfflineArchive,
            onClick = {
                onDismiss()
                callbacks.onOpenSiteData()
            },
            modifier = Modifier.testTag("more_menu_site_data")
        )
    }
}
