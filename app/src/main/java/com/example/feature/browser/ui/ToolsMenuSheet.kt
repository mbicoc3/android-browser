package com.example.feature.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.core.registry.BrowserFeatureModule
import com.example.core.registry.BrowserToolRegistry
import com.example.core.ui.components.OrbitActionPill
import com.example.core.ui.components.OrbitCompactSwitchTile
import com.example.core.ui.components.OrbitModalBottomSheet
import com.example.core.ui.components.OrbitSheetBentoTile
import com.example.core.ui.components.OrbitSheetHairlineDivider
import com.example.core.ui.components.OrbitSheetHeader
import com.example.core.ui.components.OrbitSheetPagerTabs
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitSheetStatusPill
import com.example.core.ui.theme.OrbitEmerald
import kotlinx.coroutines.launch

@Composable
fun ToolsMenuSheet(
    isDesktopMode: Boolean,
    isPageActive: Boolean,
    blockTrackers: Boolean,
    cosmeticFilteringEnabled: Boolean,
    blockedTrackersCount: Int,
    onToggleBlockTrackers: (Boolean) -> Unit,
    onToggleCosmeticFiltering: (Boolean) -> Unit,
    onReloadActivePage: () -> Unit,
    onDismiss: () -> Unit,
    onModuleClick: (BrowserFeatureModule) -> Unit
) {
    val modules = remember { BrowserToolRegistry.getQuickSheetTools() }
    val toolPages = remember(modules) {
        if (modules.size <= 8) {
            listOf(modules)
        } else if (modules.size <= 20) {
            listOf(modules.take(8), modules.drop(8))
        } else {
            listOf(modules.take(8)) + modules.drop(8).chunked(12)
        }
    }
    val maxToolRows = remember(toolPages) {
        (toolPages.maxOfOrNull { (it.size + 3) / 4 } ?: 2).coerceAtLeast(2)
    }
    val pagerHeight = remember(maxToolRows) {
        (maxToolRows * 62 + (maxToolRows - 1) * 8 + 4).dp.coerceAtLeast(136.dp)
    }
    val pageTitles = remember(toolPages.size) {
        toolPages.indices.map { idx ->
            when (idx) {
                0 -> "Tools"
                1 -> "More"
                else -> "Page ${idx + 1}"
            }
        } + "Gestures"
    }
    val pagerState = rememberPagerState(pageCount = { pageTitles.size })
    val coroutineScope = rememberCoroutineScope()

    OrbitModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = false
    ) {
        OrbitSheetHeader(
            title = "Orbit",
            subtitle = "v${BuildConfig.VERSION_NAME}",
            icon = Icons.Rounded.Tune,
            iconTint = if (blockTrackers) OrbitEmerald else MaterialTheme.colorScheme.onSurface,
            trailing = {
                if (isPageActive && blockTrackers) {
                    OrbitSheetStatusPill(
                        text = "$blockedTrackersCount blocked",
                        color = OrbitEmerald
                    )
                }
                if (isPageActive) {
                    OrbitActionPill(
                        label = stringResource(R.string.menu_reload_page),
                        leadingIcon = Icons.Rounded.Refresh,
                        onClick = {
                            onDismiss()
                            onReloadActivePage()
                        },
                        modifier = Modifier.testTag("menu_protection_reload_button")
                    )
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitCompactSwitchTile(
                label = "Ad Block",
                icon = Icons.Rounded.Shield,
                checked = blockTrackers,
                enabled = true,
                activeTint = OrbitEmerald,
                testTag = "menu_toggle_track_ad_blocker",
                onCheckedChange = onToggleBlockTrackers,
                modifier = Modifier.weight(1f)
            )
            OrbitCompactSwitchTile(
                label = "Cosmetic",
                icon = Icons.Rounded.AutoFixHigh,
                checked = cosmeticFilteringEnabled,
                enabled = blockTrackers,
                activeTint = OrbitEmerald,
                testTag = "menu_toggle_cosmetic_filtering",
                onCheckedChange = onToggleCosmeticFiltering,
                modifier = Modifier.weight(1f)
            )
        }

        OrbitSheetPagerTabs(
            tabs = pageTitles,
            selectedIndex = pagerState.currentPage,
            onSelectTab = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
            testTagPrefix = "tools_sheet_tab"
        )

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = (pageTitles.size - 1).coerceAtLeast(1),
            modifier = Modifier
                .fillMaxWidth()
                .height(pagerHeight)
                .testTag("tools_sheet_pager")
        ) { pageIndex ->
            if (pageIndex < toolPages.size) {
                ToolGridSlide(
                    modules = toolPages[pageIndex],
                    isDesktopMode = isDesktopMode,
                    isPageActive = isPageActive,
                    blockTrackers = blockTrackers,
                    onDismiss = onDismiss,
                    onModuleClick = onModuleClick
                )
            } else {
                GesturesGuideSlide()
            }
        }
    }
}

@Composable
private fun ToolGridSlide(
    modules: List<BrowserFeatureModule>,
    isDesktopMode: Boolean,
    isPageActive: Boolean,
    blockTrackers: Boolean,
    onDismiss: () -> Unit,
    onModuleClick: (BrowserFeatureModule) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modules.chunked(4).forEach { rowModules ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowModules.forEach { module ->
                    val isHighlighted =
                        (module.id == BrowserToolRegistry.ACTION_TOGGLE_DESKTOP && isDesktopMode) ||
                            (module.id == "shield" && blockTrackers)
                    val enabled = !module.isInPageAction ||
                        module.id == BrowserToolRegistry.ACTION_TOGGLE_INCOGNITO ||
                        isPageActive

                    OrbitSheetBentoTile(
                        label = module.title,
                        icon = module.icon,
                        enabled = enabled,
                        selected = isHighlighted,
                        accentColor = if (module.id == "shield" && blockTrackers) {
                            OrbitEmerald
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        onClick = {
                            if (enabled) {
                                onDismiss()
                                onModuleClick(module)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_tool_${module.id}")
                    )
                }
                repeat(4 - rowModules.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GesturesGuideSlide() {
    val gestures = remember {
        listOf(
            "Swipe Tab icon L / R" to "Switch tab",
            "Swipe up bottom bar" to "Open Orbit menu",
            "Hold/swipe + on page" to "Go Home",
            "Swipe URL bar L / R" to "Switch tab",
            "Swipe down URL bar" to "Tab switcher",
            "Long-press Tabs" to "Private tab",
            "Pull down page" to "Reload",
            "Swipe Start grid" to "4 shortcut pages"
        )
    }

    OrbitSheetPanel(
        modifier = Modifier
            .fillMaxSize()
            .testTag("menu_gestures_guide_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            gestures.forEachIndexed { idx, (gesture, action) ->
                if (idx > 0) {
                    OrbitSheetHairlineDivider(insetStart = false)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = gesture,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
