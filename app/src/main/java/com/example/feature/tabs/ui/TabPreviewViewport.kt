package com.example.feature.tabs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.UrlUtils
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.StartBackgroundMode
import com.example.core.ui.theme.OrbitDarkSolidStartBg
import com.example.core.ui.theme.OrbitIncognitoPurple
import com.example.core.ui.theme.OrbitLightSolidStartBg
import com.example.core.ui.theme.OrbitSpacing
import com.example.core.ui.theme.isOrbitDarkTheme
import com.example.feature.browser.model.BrowserTabModel

@Composable
internal fun TabPreviewViewport(
    tab: BrowserTabModel,
    isStartPage: Boolean,
    previewBitmap: Bitmap?,
    themeMode: AppThemeMode,
    startBackgroundMode: StartBackgroundMode,
    startWallpaperUri: String,
    modifier: Modifier = Modifier
) {
    when {
        previewBitmap != null -> {
            val imageBitmap = remember(previewBitmap) { previewBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = tab.title,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        isStartPage -> {
            StartPageMiniPreview(
                isIncognito = tab.isIncognito,
                themeMode = themeMode,
                backgroundMode = startBackgroundMode,
                wallpaperUri = startWallpaperUri,
                compact = false,
                modifier = modifier
            )
        }
        else -> {
            val host = UrlUtils.extractHost(tab.url).ifBlank { "Web" }
            val accent = if (tab.isIncognito) OrbitIncognitoPurple else MaterialTheme.colorScheme.primary
            Box(
                modifier = modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        )
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = OrbitSpacing.sm)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.16f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (tab.isIncognito) Icons.Rounded.Security else Icons.Rounded.Public,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = host,
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
internal fun StartPageMiniPreview(
    isIncognito: Boolean,
    themeMode: AppThemeMode,
    backgroundMode: StartBackgroundMode,
    wallpaperUri: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isOrbitDarkTheme(themeMode)
    val solidBg = if (isDark || isIncognito) OrbitDarkSolidStartBg else OrbitLightSolidStartBg
    Box(
        modifier = modifier.background(solidBg),
        contentAlignment = Alignment.Center
    ) {
        if (!isIncognito && backgroundMode == StartBackgroundMode.IMAGE && wallpaperUri.isNotBlank()) {
            AsyncImage(
                model = wallpaperUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f))
            )
        }
        if (compact) {
            Icon(
                imageVector = if (isIncognito) Icons.Rounded.Security else Icons.Rounded.Home,
                contentDescription = null,
                tint = if (isIncognito) OrbitIncognitoPurple else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isIncognito) "Private Search" else "Search",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.80f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TabCloseButton(
    tabId: String,
    onClose: () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(28.dp)
                .testTag("close_tab_$tabId")
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
