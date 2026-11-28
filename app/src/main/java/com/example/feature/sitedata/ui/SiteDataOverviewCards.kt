package com.example.feature.sitedata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.FormatUtils
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitCheckboxRow
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.rememberOrbitSurfacePalette
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.sitedata.model.GlobalClearSelection
import com.example.feature.sitedata.model.GlobalStorageMetrics
import com.example.feature.sitedata.model.SiteDataCategory
import com.example.feature.sitedata.model.SiteStorageSummary

@Composable
fun StorageTelemetryCard(
    metrics: GlobalStorageMetrics,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val palette = rememberOrbitSurfacePalette()
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(palette.tile),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "WebEngine Storage Footprint",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${metrics.trackedSitesCount} sites · ${metrics.sitesWithCookiesCount} with cookies · ${metrics.openTabsCount} live tabs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isSyncing,
                    modifier = Modifier.testTag("site_data_refresh_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh storage metrics"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
            ) {
                MetricPillTile(
                    label = "Total WebView",
                    value = FormatUtils.formatByteSize(metrics.totalWebViewDirBytes),
                    modifier = Modifier.weight(1f)
                )
                MetricPillTile(
                    label = "HTTP & App Cache",
                    value = FormatUtils.formatByteSize(metrics.httpCacheBytes),
                    modifier = Modifier.weight(1f)
                )
                MetricPillTile(
                    label = "Site Storage",
                    value = FormatUtils.formatByteSize(metrics.webStorageOriginsBytes),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricPillTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    OrbitSheetPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GlobalCategoryCleanupCard(
    selection: GlobalClearSelection,
    isClearing: Boolean,
    onToggleCategory: (SiteDataCategory) -> Unit,
    onClearSingleCategory: (SiteDataCategory) -> Unit,
    onRequestClearSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.xs)
        ) {
            Text(
                text = "Selective Global Cleanup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Clear each data type independently across all sites, or select multiple categories below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            SiteDataCategory.entries.forEachIndexed { idx, category ->
                val checked = when (category) {
                    SiteDataCategory.COOKIES -> selection.clearCookies
                    SiteDataCategory.LOCAL_STORAGE -> selection.clearLocalStorage
                    SiteDataCategory.SESSION_STORAGE -> selection.clearSessionStorage
                    SiteDataCategory.INDEXED_DB -> selection.clearIndexedDb
                    SiteDataCategory.CACHE -> selection.clearCache
                }
                OrbitCheckboxRow(
                    title = category.title,
                    subtitle = category.description,
                    checked = checked,
                    onCheckedChange = { onToggleCategory(category) },
                    leadingIcon = categoryIcon(category),
                    enabled = !isClearing,
                    checkboxTestTag = "global_chk_${category.name.lowercase()}",
                    trailing = {
                        OrbitGhostButton(
                            text = "Clear",
                            onClick = { onClearSingleCategory(category) },
                            enabled = !isClearing,
                            isDestructive = true,
                            modifier = Modifier.testTag("global_clear_one_${category.name.lowercase()}")
                        )
                    }
                )
                if (idx < SiteDataCategory.entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OrbitPrimaryButton(
                text = "Clear Selected (${selection.selectedCount})",
                onClick = onRequestClearSelected,
                enabled = !isClearing && selection.selectedCount > 0,
                isDestructive = true,
                icon = Icons.Rounded.CleaningServices,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_clear_selected_button")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiteSummaryRowCard(
    site: SiteStorageSummary,
    onInspectClick: () -> Unit,
    onQuickClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSurfaceCard(
        modifier = modifier.testTag("site_card_${site.host}"),
        onClick = onInspectClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.md, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val palette = rememberOrbitSurfacePalette()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(palette.tile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = site.host.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = site.host,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (site.openTabsCount > 0) {
                            OrbitBadgeChip(
                                text = "${site.openTabsCount} tab${if (site.openTabsCount > 1) "s" else ""}",
                                accentColor = OrbitEmerald
                            )
                        }
                    }
                    val visitSubtitle = if (site.lastVisitedAt > 0L) {
                        "${site.origin} · ${FormatUtils.formatDateBucket(site.lastVisitedAt)}"
                    } else {
                        "${site.origin} · Storage origin"
                    }
                    Text(
                        text = visitSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (site.estimatedTotalBytes > 0L) {
                        OrbitBadgeChip(text = FormatUtils.formatByteSize(site.estimatedTotalBytes))
                    }
                }
                IconButton(
                    onClick = onQuickClearAll,
                    modifier = Modifier.testTag("quick_clear_site_${site.host}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear all data for ${site.host}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SiteMiniTag(label = "Cookies: ${site.cookieCount}", active = site.cookieCount > 0)
                if (site.localStorageCount > 0 || site.lastInspectedAt > 0L) {
                    SiteMiniTag(
                        label = "Local: ${site.localStorageCount}",
                        active = site.localStorageCount > 0
                    )
                }
                if (site.sessionStorageCount > 0 || site.openTabsCount > 0) {
                    SiteMiniTag(
                        label = "Session: ${site.sessionStorageCount}",
                        active = site.sessionStorageCount > 0
                    )
                }
                if (site.indexedDbCount > 0 || site.lastInspectedAt > 0L) {
                    SiteMiniTag(
                        label = "IndexedDB: ${site.indexedDbCount}",
                        active = site.indexedDbCount > 0
                    )
                }
                if (site.cacheStorageCount > 0 || site.serviceWorkerCount > 0) {
                    SiteMiniTag(
                        label = "Cache: ${site.cacheStorageCount}",
                        active = true
                    )
                }
                if (site.lastInspectedAt == 0L) {
                    SiteMiniTag(label = "Tap to inspect live", active = false)
                }
            }
        }
    }
}

@Composable
private fun SiteMiniTag(label: String, active: Boolean) {
    OrbitBadgeChip(
        text = label,
        accentColor = if (active) MaterialTheme.colorScheme.primary else null,
        isSubtle = !active
    )
}

fun categoryIcon(category: SiteDataCategory): ImageVector = when (category) {
    SiteDataCategory.COOKIES -> Icons.Rounded.Cookie
    SiteDataCategory.LOCAL_STORAGE -> Icons.Rounded.Storage
    SiteDataCategory.SESSION_STORAGE -> Icons.Rounded.Timer
    SiteDataCategory.INDEXED_DB -> Icons.Rounded.Memory
    SiteDataCategory.CACHE -> Icons.Rounded.Cached
}
