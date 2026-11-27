package com.example.feature.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.SearchEngine
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.DEFAULT_SPEED_DIAL_COLLECTIONS
import com.example.core.datastore.DEFAULT_START_WALLPAPER_URL
import com.example.core.datastore.StartBackgroundMode
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitDarkOutline
import com.example.core.ui.theme.OrbitDarkSolidStartBg
import com.example.core.ui.theme.OrbitLightOutline
import com.example.core.ui.theme.OrbitLightPrimary
import com.example.core.ui.theme.OrbitLightSolidStartBg
import com.example.core.ui.theme.OrbitSpacing
import com.example.core.ui.theme.isOrbitDarkTheme
import com.example.feature.browser.model.SpeedDialShortcut
import kotlinx.coroutines.launch
import java.io.File

private sealed interface GridSlot {
    data class Shortcut(val item: SpeedDialShortcut) : GridSlot
    data object AddButton : GridSlot
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPageContent(
    isIncognito: Boolean,
    themeMode: AppThemeMode,
    backgroundMode: StartBackgroundMode,
    wallpaperUri: String,
    searchEngine: SearchEngine,
    blockTrackers: Boolean,
    omniboxText: String,
    isOmniboxFocused: Boolean,
    suggestions: List<String>,
    localMatches: List<com.example.feature.browser.model.OmniboxLocalMatch>,
    speedDialItems: List<SpeedDialShortcut>,
    collectionNames: List<String>,
    focusRequester: FocusRequester,
    bottomBarPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onOmniboxTextChange: (String) -> Unit,
    onOmniboxFocusChange: (Boolean) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onCycleSearchEngine: () -> Unit,
    onShortcutClick: (String) -> Unit,
    onAddShortcut: (title: String, url: String, collectionIndex: Int) -> Unit,
    onMoveShortcut: (Long, Int) -> Unit,
    onRefreshShortcutIcon: (Long, String) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    onRenameCollection: (Int, String) -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBackgroundModeSelected: (StartBackgroundMode) -> Unit,
    onWallpaperUriSelected: (String) -> Unit,
    onNavigateToRoute: (String) -> Unit,
    multiPurposeCallbacks: OmniboxMultiPurposeCallbacks,
    modifier: Modifier = Modifier
) {
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addTargetCollection by remember { mutableIntStateOf(0) }
    var shortcutToInspect by remember { mutableStateOf<SpeedDialShortcut?>(null) }
    var collectionToRename by remember { mutableStateOf<Int?>(null) }

    val normalizedCollections = remember(collectionNames) {
        if (collectionNames.size >= 4) collectionNames.take(4) else DEFAULT_SPEED_DIAL_COLLECTIONS
    }
    val pagerState = rememberPagerState(pageCount = { normalizedCollections.size })
    val coroutineScope = rememberCoroutineScope()

    val isDarkTheme = isOrbitDarkTheme(themeMode)
    val isLightSolidSurface = backgroundMode == StartBackgroundMode.SOLID && !isDarkTheme
    val clipboardManager = LocalClipboardManager.current

    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundMode == StartBackgroundMode.SOLID) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDarkTheme) OrbitDarkSolidStartBg else OrbitLightSolidStartBg
                    )
            )
        } else {
            val resolvedSource: Any = remember(wallpaperUri) {
                val target = wallpaperUri.ifBlank { DEFAULT_START_WALLPAPER_URL }
                if (target.startsWith("/")) {
                    File(target).takeIf { it.exists() } ?: DEFAULT_START_WALLPAPER_URL
                } else {
                    target
                }
            }
            AsyncImage(
                model = resolvedSource,
                contentDescription = null,
                placeholder = painterResource(id = R.drawable.img_home_hero_1795536368553),
                error = painterResource(id = R.drawable.img_home_hero_1795536368553),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x28000000),
                            0.48f to Color(0x3D000000),
                            0.78f to Color(0x8818181A),
                            1.0f to Color(0xAD18181A)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OrbitSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StartPageHeaderRow(
                isIncognito = isIncognito,
                isDarkTheme = isDarkTheme,
                isLightSolidSurface = isLightSolidSurface,
                searchEngine = searchEngine,
                blockTrackers = blockTrackers,
                onAvatarClick = { onNavigateToRoute(com.example.core.navigation.BrowserRoutes.PRIVACY_VAULT) },
                onStatusPillClick = onCycleSearchEngine,
                onCustomizeClick = { showCustomizeSheet = true }
            )

            Spacer(modifier = Modifier.weight(1f, fill = true))
            Spacer(modifier = Modifier.height(OrbitSpacing.lg))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingStartSearchPill(
                    query = omniboxText,
                    isFocused = isOmniboxFocused,
                    isIncognito = isIncognito,
                    isDarkTheme = isDarkTheme,
                    searchEngine = searchEngine,
                    focusRequester = focusRequester,
                    onTextChange = onOmniboxTextChange,
                    onFocusChange = onOmniboxFocusChange,
                    onSubmit = { onSubmitSearch(omniboxText) },
                    onEngineIconClick = onCycleSearchEngine,
                    onPasteAndGo = {
                        val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                        if (clip.isNotBlank()) {
                            onSubmitSearch(clip)
                        } else {
                            focusRequester.requestFocus()
                        }
                    }
                )

                if (isOmniboxFocused && (omniboxText.isNotBlank() || suggestions.isNotEmpty() || localMatches.isNotEmpty())) {
                    Spacer(modifier = Modifier.height(OrbitSpacing.sm))
                    val suggestionBorder = if (isDarkTheme) OrbitDarkOutline else OrbitLightOutline
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, suggestionBorder)
                    ) {
                        OmniboxMultiPurposePanel(
                            query = omniboxText,
                            currentUrl = com.example.core.common.UrlUtils.START_PAGE_URL,
                            isStartPage = true,
                            isDesktopMode = false,
                            activeSearchEngine = searchEngine,
                            directImage = null,
                            localMatches = localMatches,
                            suggestions = suggestions,
                            isInputFocused = true,
                            callbacks = multiPurposeCallbacks
                        )
                    }
                }

                Spacer(modifier = Modifier.height(OrbitSpacing.lg))

                val groupedShortcuts = remember(speedDialItems) {
                    (0 until normalizedCollections.size).map { colIdx ->
                        speedDialItems.filter { it.collectionIndex == colIdx }
                    }
                }
                val maxCollectionRows = remember(groupedShortcuts) {
                    groupedShortcuts.maxOfOrNull { items ->
                        val slotCount = items.take(14).size + 1
                        (slotCount + 4) / 5
                    }?.coerceIn(2, 3) ?: 2
                }
                val uniformPagerHeight = remember(maxCollectionRows) {
                    (94.dp * maxCollectionRows) + (14.dp * (maxCollectionRows - 1)) + 8.dp
                }

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    pageSpacing = 12.dp,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(uniformPagerHeight)
                        .testTag("speed_dial_collections_pager")
                ) { pageIndex ->
                    val pageItems = groupedShortcuts.getOrElse(pageIndex) { emptyList() }
                    val slots: List<GridSlot> =
                        pageItems.take(14).map { GridSlot.Shortcut(it) } + GridSlot.AddButton

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("speed_dial_collection_container_$pageIndex"),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        slots.chunked(5).forEach { rowSlots ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSlots.forEach { slot ->
                                    when (slot) {
                                        is GridSlot.Shortcut -> SpeedDialTile(
                                            shortcut = slot.item,
                                            isLightSolidSurface = isLightSolidSurface,
                                            onClick = { onShortcutClick(slot.item.url) },
                                            onLongClick = { shortcutToInspect = slot.item },
                                            modifier = Modifier.weight(1f)
                                        )
                                        is GridSlot.AddButton -> AddShortcutTile(
                                            isDarkTheme = isDarkTheme,
                                            isLightSolidSurface = isLightSolidSurface,
                                            onClick = {
                                                addTargetCollection = pageIndex
                                                showAddDialog = true
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                repeat(5 - rowSlots.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(normalizedCollections.size) { idx ->
                        val active = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                                    },
                                    onLongClick = { collectionToRename = idx }
                                )
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                                .testTag("speed_dial_collection_dot_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = if (active) 18.dp else 7.dp, height = 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            active && isLightSolidSurface -> OrbitLightPrimary
                                            active -> Color.White
                                            isLightSolidSurface -> Color(0xFFB4B4BD)
                                            else -> Color.White.copy(alpha = 0.35f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(OrbitSpacing.lg + bottomBarPadding))
        }
    }

    if (showCustomizeSheet) {
        HomeCustomizeSheet(
            themeMode = themeMode,
            backgroundMode = backgroundMode,
            wallpaperUri = wallpaperUri,
            onThemeModeSelected = onThemeModeSelected,
            onBackgroundModeSelected = onBackgroundModeSelected,
            onWallpaperUriSelected = onWallpaperUriSelected,
            onDismiss = { showCustomizeSheet = false }
        )
    }

    if (showAddDialog) {
        AddSpeedDialDialog(
            collectionNames = normalizedCollections,
            defaultCollectionIndex = addTargetCollection,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url, collectionIdx ->
                onAddShortcut(title, url, collectionIdx)
                showAddDialog = false
            }
        )
    }

    shortcutToInspect?.let { target ->
        SpeedDialShortcutActionsDialog(
            shortcut = target,
            collectionNames = normalizedCollections,
            onMoveToCollection = { newCollection ->
                onMoveShortcut(target.id, newCollection)
                shortcutToInspect = null
            },
            onRefreshIcon = {
                onRefreshShortcutIcon(target.id, target.url)
                shortcutToInspect = null
            },
            onDelete = {
                onDeleteShortcut(target.id)
                shortcutToInspect = null
            },
            onDismiss = { shortcutToInspect = null }
        )
    }

    collectionToRename?.let { targetIndex ->
        RenameCollectionDialog(
            initialName = normalizedCollections.getOrElse(targetIndex) { "" },
            onConfirm = { updated ->
                onRenameCollection(targetIndex, updated)
                collectionToRename = null
            },
            onDismiss = { collectionToRename = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedDialShortcutActionsDialog(
    shortcut: SpeedDialShortcut,
    collectionNames: List<String>,
    onMoveToCollection: (Int) -> Unit,
    onRefreshIcon: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    OrbitDialog(
        onDismissRequest = onDismiss,
        title = shortcut.title,
        subtitle = shortcut.url,
        icon = Icons.Rounded.PushPin,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.action_delete),
                icon = Icons.Rounded.DeleteOutline,
                isDestructive = true,
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
        }
    ) { palette ->
        Text(
            text = stringResource(R.string.shortcut_actions_move_to),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            collectionNames.forEachIndexed { idx, name ->
                OrbitSegmentPill(
                    label = name,
                    selected = shortcut.collectionIndex == idx,
                    onClick = {
                        if (shortcut.collectionIndex != idx) {
                            onMoveToCollection(idx)
                        }
                    }
                )
            }
        }
        HorizontalDivider(color = palette.innerDivider)
        OrbitSecondaryButton(
            text = stringResource(R.string.shortcut_actions_refresh_icon),
            icon = Icons.Rounded.Refresh,
            onClick = onRefreshIcon,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RenameCollectionDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.shortcut_rename_collection),
        icon = Icons.Rounded.Edit,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = name,
            onValueChange = { name = it },
            label = "Collection Name",
            singleLine = true
        )
    }
}
