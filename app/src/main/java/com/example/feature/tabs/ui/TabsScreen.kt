package com.example.feature.tabs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.StartBackgroundMode
import com.example.core.datastore.TabViewMode
import com.example.core.ui.components.OrbitActionPill
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitExtendedFloatingActionButton
import com.example.core.ui.components.OrbitFloatingActionButton
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.components.rememberOrbitSurfacePalette
import com.example.core.ui.theme.OrbitIncognitoPurple
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.browser.model.BrowserTabModel

@Composable
fun TabsScreen(
    tabs: List<BrowserTabModel>,
    activeTabId: String,
    tabViewMode: TabViewMode,
    tabPreviewEnabled: Boolean,
    themeMode: AppThemeMode,
    startBackgroundMode: StartBackgroundMode,
    startWallpaperUri: String,
    previewProvider: (String) -> Bitmap?,
    onChangeViewMode: (TabViewMode) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAll: () -> Unit,
    onOpenSandboxes: () -> Unit = {},
    onBack: () -> Unit
) {
    val standardTabs = tabs.filter { !it.isIncognito }
    val incognitoTabs = tabs.filter { it.isIncognito }
    val subtitleText = if (tabViewMode == TabViewMode.CARD) {
        val previewLabel = if (tabPreviewEnabled) {
            stringResource(R.string.tabs_previews_on)
        } else {
            stringResource(R.string.tabs_previews_off)
        }
        "${tabs.size} open · $previewLabel"
    } else {
        "${tabs.size} open"
    }

    val palette = rememberOrbitSurfacePalette()

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.tabs_title),
                subtitle = subtitleText,
                onBackClick = onBack,
                actions = {
                    OrbitGhostButton(
                        text = stringResource(R.string.tabs_close_all),
                        icon = Icons.Rounded.DeleteSweep,
                        isDestructive = true,
                        onClick = onCloseAll,
                        modifier = Modifier.testTag("tabs_close_all_button")
                    )
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbitFloatingActionButton(
                    onClick = { onNewTab(true) },
                    icon = Icons.Rounded.Security,
                    contentDescription = stringResource(R.string.tabs_new_incognito),
                    containerColor = palette.panel,
                    contentColor = OrbitIncognitoPurple,
                    modifier = Modifier.testTag("new_incognito_tab_fab")
                )
                OrbitExtendedFloatingActionButton(
                    text = stringResource(R.string.tabs_new),
                    icon = Icons.Rounded.Add,
                    onClick = { onNewTab(false) },
                    modifier = Modifier.testTag("new_tab_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabViewModeSwitcherBar(
                selectedMode = tabViewMode,
                onSelectMode = onChangeViewMode,
                onOpenSandboxes = onOpenSandboxes
            )

            if (tabs.isEmpty()) {
                OrbitEmptyState(
                    title = stringResource(R.string.tabs_empty_title),
                    subtitle = stringResource(R.string.tabs_empty_subtitle),
                    actionLabel = stringResource(R.string.tabs_new),
                    onActionClick = { onNewTab(false) },
                    modifier = Modifier.weight(1f)
                )
            } else if (tabViewMode == TabViewMode.CARD) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = OrbitSpacing.md,
                        end = OrbitSpacing.md,
                        top = 4.dp,
                        bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    if (standardTabs.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OrbitSectionHeader(title = stringResource(R.string.tabs_standard_section))
                        }
                        items(standardTabs, key = { it.id }) { tab ->
                            TabGridCard(
                                tab = tab,
                                isActive = tab.id == activeTabId,
                                previewEnabled = tabPreviewEnabled,
                                previewBitmap = if (tabPreviewEnabled) previewProvider(tab.id) else null,
                                themeMode = themeMode,
                                startBackgroundMode = startBackgroundMode,
                                startWallpaperUri = startWallpaperUri,
                                onSelect = { onSelectTab(tab.id) },
                                onClose = { onCloseTab(tab.id) }
                            )
                        }
                    }

                    if (incognitoTabs.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OrbitSectionHeader(title = stringResource(R.string.tabs_incognito_section))
                        }
                        items(incognitoTabs, key = { it.id }) { tab ->
                            TabGridCard(
                                tab = tab,
                                isActive = tab.id == activeTabId,
                                previewEnabled = tabPreviewEnabled,
                                previewBitmap = if (tabPreviewEnabled) previewProvider(tab.id) else null,
                                themeMode = themeMode,
                                startBackgroundMode = startBackgroundMode,
                                startWallpaperUri = startWallpaperUri,
                                onSelect = { onSelectTab(tab.id) },
                                onClose = { onCloseTab(tab.id) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = OrbitSpacing.md,
                        end = OrbitSpacing.md,
                        top = 4.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    if (standardTabs.isNotEmpty()) {
                        item(key = "section_standard") {
                            OrbitSectionHeader(title = stringResource(R.string.tabs_standard_section))
                        }
                        items(standardTabs, key = { it.id }) { tab ->
                            TabListRow(
                                tab = tab,
                                isActive = tab.id == activeTabId,
                                previewEnabled = tabPreviewEnabled,
                                previewBitmap = if (tabPreviewEnabled) previewProvider(tab.id) else null,
                                themeMode = themeMode,
                                startBackgroundMode = startBackgroundMode,
                                startWallpaperUri = startWallpaperUri,
                                onSelect = { onSelectTab(tab.id) },
                                onClose = { onCloseTab(tab.id) }
                            )
                        }
                    }

                    if (incognitoTabs.isNotEmpty()) {
                        item(key = "section_incognito") {
                            OrbitSectionHeader(title = stringResource(R.string.tabs_incognito_section))
                        }
                        items(incognitoTabs, key = { it.id }) { tab ->
                            TabListRow(
                                tab = tab,
                                isActive = tab.id == activeTabId,
                                previewEnabled = tabPreviewEnabled,
                                previewBitmap = if (tabPreviewEnabled) previewProvider(tab.id) else null,
                                themeMode = themeMode,
                                startBackgroundMode = startBackgroundMode,
                                startWallpaperUri = startWallpaperUri,
                                onSelect = { onSelectTab(tab.id) },
                                onClose = { onCloseTab(tab.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabViewModeSwitcherBar(
    selectedMode: TabViewMode,
    onSelectMode: (TabViewMode) -> Unit,
    onOpenSandboxes: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = OrbitSpacing.md, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrbitSegmentPill(
            label = stringResource(R.string.tabs_view_card),
            selected = selectedMode == TabViewMode.CARD,
            onClick = { onSelectMode(TabViewMode.CARD) },
            leadingIcon = Icons.Rounded.GridView,
            modifier = Modifier.testTag("tabs_view_mode_card")
        )
        OrbitSegmentPill(
            label = stringResource(R.string.tabs_view_list),
            selected = selectedMode == TabViewMode.LIST,
            onClick = { onSelectMode(TabViewMode.LIST) },
            leadingIcon = Icons.Rounded.ViewAgenda,
            modifier = Modifier.testTag("tabs_view_mode_list")
        )
        OrbitActionPill(
            label = "Sandboxes",
            leadingIcon = Icons.Rounded.Layers,
            onClick = onOpenSandboxes,
            modifier = Modifier.testTag("tabs_sandboxes_button")
        )
    }
}
