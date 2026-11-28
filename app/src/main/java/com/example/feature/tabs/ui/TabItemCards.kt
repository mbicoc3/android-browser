package com.example.feature.tabs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.UrlUtils
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.StartBackgroundMode
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.rememberOrbitSurfacePalette
import com.example.core.ui.theme.OrbitIncognitoPurple
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.browser.model.BrowserTabModel

@Composable
fun TabGridCard(
    tab: BrowserTabModel,
    isActive: Boolean,
    previewEnabled: Boolean,
    previewBitmap: Bitmap?,
    themeMode: AppThemeMode,
    startBackgroundMode: StartBackgroundMode,
    startWallpaperUri: String,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStartPage = UrlUtils.isStartPage(tab.url)
    val cardHeight = if (previewEnabled) 176.dp else 112.dp
    val hasSandbox = !tab.sandboxName.isNullOrBlank()
    val cardAccent = when {
        isActive && tab.isIncognito -> OrbitIncognitoPurple
        hasSandbox -> Color(tab.sandboxColorHex ?: 0xFF38BDF8)
        else -> MaterialTheme.colorScheme.primary
    }

    OrbitSurfaceCard(
        selected = isActive || hasSandbox,
        accentColor = cardAccent,
        onClick = onSelect,
        modifier = modifier
            .height(cardHeight)
            .testTag("tab_card_${tab.id}")
    ) {
        if (previewEnabled) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(98.dp)
                ) {
                    TabPreviewViewport(
                        tab = tab,
                        isStartPage = isStartPage,
                        previewBitmap = previewBitmap,
                        themeMode = themeMode,
                        startBackgroundMode = startBackgroundMode,
                        startWallpaperUri = startWallpaperUri,
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (tab.isIncognito) {
                                OrbitBadgeChip(text = stringResource(R.string.tabs_badge_private))
                            } else if (!tab.sandboxName.isNullOrBlank()) {
                                OrbitBadgeChip(text = tab.sandboxName)
                            } else if (isActive) {
                                OrbitBadgeChip(text = stringResource(R.string.tabs_badge_active))
                            }
                        }
                        TabCloseButton(
                            tabId = tab.id,
                            onClose = onClose
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tab.title.ifBlank { stringResource(R.string.tabs_default_title) },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                tab.isIncognito -> Icons.Rounded.Security
                                isStartPage -> Icons.Rounded.Home
                                else -> Icons.Rounded.Public
                            },
                            contentDescription = null,
                            tint = if (tab.isIncognito) OrbitIncognitoPurple else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isStartPage) {
                                stringResource(R.string.tabs_start_host)
                            } else {
                                UrlUtils.extractHost(tab.url)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when {
                                tab.isIncognito -> Icons.Rounded.Security
                                isStartPage -> Icons.Rounded.Home
                                else -> Icons.Rounded.Public
                            },
                            contentDescription = null,
                            tint = if (tab.isIncognito) OrbitIncognitoPurple else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (tab.isIncognito) {
                            OrbitBadgeChip(text = stringResource(R.string.tabs_badge_private))
                        } else if (!tab.sandboxName.isNullOrBlank()) {
                            OrbitBadgeChip(text = tab.sandboxName)
                        } else if (isActive) {
                            OrbitBadgeChip(text = stringResource(R.string.tabs_badge_active))
                        }
                    }
                    TabCloseButton(
                        tabId = tab.id,
                        onClose = onClose
                    )
                }

                Column {
                    Text(
                        text = tab.title.ifBlank { stringResource(R.string.tabs_default_title) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isStartPage) {
                            stringResource(R.string.tabs_start_host)
                        } else {
                            UrlUtils.extractHost(tab.url)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TabListRow(
    tab: BrowserTabModel,
    isActive: Boolean,
    previewEnabled: Boolean,
    previewBitmap: Bitmap?,
    themeMode: AppThemeMode,
    startBackgroundMode: StartBackgroundMode,
    startWallpaperUri: String,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStartPage = UrlUtils.isStartPage(tab.url)
    val palette = rememberOrbitSurfacePalette()
    val hasSandbox = !tab.sandboxName.isNullOrBlank()
    val rowAccent = when {
        isActive && tab.isIncognito -> OrbitIncognitoPurple
        hasSandbox -> Color(tab.sandboxColorHex ?: 0xFF38BDF8)
        else -> MaterialTheme.colorScheme.primary
    }

    OrbitSurfaceCard(
        selected = isActive || hasSandbox,
        accentColor = rowAccent,
        onClick = onSelect,
        modifier = modifier.testTag("tab_list_row_${tab.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = OrbitSpacing.sm, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.tile),
                contentAlignment = Alignment.Center
            ) {
                when {
                    previewEnabled && isStartPage -> {
                        StartPageMiniPreview(
                            isIncognito = tab.isIncognito,
                            themeMode = themeMode,
                            backgroundMode = startBackgroundMode,
                            wallpaperUri = startWallpaperUri,
                            compact = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    previewEnabled && previewBitmap != null -> {
                        val imageBitmap = remember(previewBitmap) { previewBitmap.asImageBitmap() }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = tab.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = when {
                                tab.isIncognito -> Icons.Rounded.Security
                                isStartPage -> Icons.Rounded.Home
                                else -> Icons.Rounded.Public
                            },
                            contentDescription = null,
                            tint = if (tab.isIncognito) OrbitIncognitoPurple else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(OrbitSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tab.title.ifBlank { stringResource(R.string.tabs_default_title) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tab.isIncognito) {
                        OrbitBadgeChip(text = stringResource(R.string.tabs_badge_private))
                    } else if (!tab.sandboxName.isNullOrBlank()) {
                        OrbitBadgeChip(text = tab.sandboxName)
                    } else if (isActive) {
                        OrbitBadgeChip(text = stringResource(R.string.tabs_badge_active))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isStartPage) {
                        stringResource(R.string.tabs_start_host)
                    } else {
                        UrlUtils.extractHost(tab.url)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TabCloseButton(
                tabId = tab.id,
                onClose = onClose
            )
        }
    }
}
