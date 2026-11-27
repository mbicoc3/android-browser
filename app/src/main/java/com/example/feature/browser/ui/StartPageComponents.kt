package com.example.feature.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPasteGo
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.SearchEngine
import com.example.core.network.FaviconHtmlParser
import com.example.core.ui.theme.OrbitCyan
import com.example.core.ui.theme.OrbitDarkOnSurface
import com.example.core.ui.theme.OrbitDarkOnSurfaceVariant
import com.example.core.ui.theme.OrbitDarkOutline
import com.example.core.ui.theme.OrbitDarkSurface
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitIncognitoPurple
import com.example.core.ui.theme.OrbitLightEmerald
import com.example.core.ui.theme.OrbitLightOnSurface
import com.example.core.ui.theme.OrbitLightOnSurfaceVariant
import com.example.core.ui.theme.OrbitLightOutline
import com.example.core.ui.theme.OrbitLightPrimary
import com.example.core.ui.theme.OrbitLightSurfaceVariant
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.browser.model.SpeedDialShortcut
import java.io.File

@Composable
internal fun StartPageHeaderRow(
    isIncognito: Boolean,
    isDarkTheme: Boolean,
    isLightSolidSurface: Boolean,
    searchEngine: SearchEngine,
    blockTrackers: Boolean,
    onAvatarClick: () -> Unit,
    onStatusPillClick: () -> Unit,
    onCustomizeClick: () -> Unit
) {
    val pillForeground = if (isDarkTheme) OrbitDarkOnSurface else OrbitLightOnSurface
    val customizeColor = if (isLightSolidSurface) OrbitLightOnSurface else Color.White
    val pillBg = if (isDarkTheme) OrbitDarkSurface else OrbitLightSurfaceVariant
    val pillBorder = if (isDarkTheme) OrbitDarkOutline else OrbitLightOutline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OrbitSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onAvatarClick,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("start_avatar_button"),
                shape = CircleShape,
                color = pillBg,
                border = BorderStroke(1.5.dp, pillBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isIncognito) Icons.Rounded.Security else Icons.Rounded.Person,
                        contentDescription = stringResource(R.string.vault_title),
                        tint = if (isIncognito) OrbitIncognitoPurple else pillForeground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                onClick = onStatusPillClick,
                shape = RoundedCornerShape(20.dp),
                color = pillBg,
                border = BorderStroke(1.dp, pillBorder),
                modifier = Modifier.testTag("start_status_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isIncognito -> Icons.Rounded.NightsStay
                            blockTrackers -> Icons.Rounded.Shield
                            else -> Icons.Rounded.Public
                        },
                        contentDescription = null,
                        tint = when {
                            isIncognito -> OrbitIncognitoPurple
                            blockTrackers -> if (isDarkTheme) OrbitEmerald else OrbitLightEmerald
                            else -> if (isDarkTheme) OrbitCyan else OrbitLightPrimary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isIncognito) "Private" else searchEngine.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = pillForeground
                    )
                }
            }
        }

        IconButton(
            onClick = onCustomizeClick,
            modifier = Modifier.testTag("start_customize_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Customize Home Page",
                tint = customizeColor
            )
        }
    }
}

@Composable
internal fun FloatingStartSearchPill(
    query: String,
    isFocused: Boolean,
    isIncognito: Boolean,
    isDarkTheme: Boolean,
    searchEngine: SearchEngine,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onEngineIconClick: () -> Unit,
    onPasteAndGo: () -> Unit
) {
    val pillBackground = if (isDarkTheme) OrbitDarkSurface else OrbitLightSurfaceVariant
    val textColor = if (isDarkTheme) OrbitDarkOnSurface else OrbitLightOnSurface
    val hintColor = if (isDarkTheme) OrbitDarkOnSurfaceVariant else OrbitLightOnSurfaceVariant
    val defaultBorder = if (isDarkTheme) OrbitDarkOutline else OrbitLightOutline
    val activeAccent = if (isDarkTheme) OrbitCyan else OrbitLightPrimary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = pillBackground,
        border = BorderStroke(
            width = 1.dp,
            color = if (isFocused) activeAccent else defaultBorder
        ),
        shadowElevation = if (isDarkTheme) 6.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onEngineIconClick,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isIncognito) {
                                    listOf(OrbitIncognitoPurple, Color(0xFF6366F1))
                                } else {
                                    listOf(OrbitCyan, Color(0xFF6366F1), OrbitEmerald)
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncognito) Icons.Rounded.Security else Icons.Rounded.Public,
                        contentDescription = searchEngine.displayName,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.omnibox_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = hintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onTextChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    cursorBrush = SolidColor(activeAccent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChange(it.isFocused) }
                        .testTag("start_page_search_input")
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onTextChange("") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = hintColor
                    )
                }
            } else {
                IconButton(
                    onClick = onPasteAndGo,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("start_paste_and_go")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPasteGo,
                        contentDescription = "Paste and go",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpeedDialTile(
    shortcut: SpeedDialShortcut,
    isLightSolidSurface: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelColor = if (isLightSolidSurface) OrbitLightOnSurface else Color.White
    val iconFile = remember(shortcut.iconPath) {
        shortcut.iconPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    }
    var iconLoadFailed by remember(shortcut.iconPath) { mutableStateOf(false) }
    val showExtractedIcon = iconFile != null && !iconLoadFailed
    val firstLetter = remember(shortcut.title, shortcut.url) {
        FaviconHtmlParser.extractFirstLetterBadge(shortcut.title, shortcut.url)
    }

    Column(
        modifier = modifier
            .height(94.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 2.dp)
            .testTag("speed_dial_${shortcut.title.lowercase().replace(" ", "_")}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (showExtractedIcon) Color.White else shortcut.accentColor,
            border = if (showExtractedIcon) {
                if (isLightSolidSurface) BorderStroke(0.5.dp, OrbitLightOutline) else null
            } else {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            },
            shadowElevation = if (isLightSolidSurface) 1.dp else 4.dp
        ) {
            if (showExtractedIcon) {
                AsyncImage(
                    model = iconFile,
                    contentDescription = shortcut.title,
                    contentScale = ContentScale.Crop,
                    onError = { iconLoadFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val letterColor = if (shortcut.accentColor.luminance() > 0.6f) {
                        Color(0xFF0F172A)
                    } else {
                        Color.White
                    }
                    Text(
                        text = firstLetter,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = letterColor,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = shortcut.title,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 2,
            lineHeight = 14.sp,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun AddShortcutTile(
    isDarkTheme: Boolean,
    isLightSolidSurface: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileSurface = if (isDarkTheme) OrbitDarkSurface else OrbitLightSurfaceVariant
    val tileBorder = if (isDarkTheme) OrbitDarkOutline else OrbitLightOutline
    val iconColor = if (isDarkTheme) OrbitDarkOnSurface else OrbitLightOnSurface
    val labelColor = if (isLightSolidSurface) OrbitLightOnSurface else Color.White

    Column(
        modifier = modifier
            .height(94.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag("speed_dial_add_button"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = tileSurface,
            border = BorderStroke(1.dp, tileBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_add),
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.action_add),
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
