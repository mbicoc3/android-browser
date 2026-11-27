package com.example.feature.sitedata.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.FormatUtils
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.sitedata.model.SiteDataCategory
import com.example.feature.sitedata.model.SiteDetailedInspection

@Composable
fun SiteDetailContent(
    origin: String,
    inspection: SiteDetailedInspection?,
    isInspecting: Boolean,
    isClearing: Boolean,
    onBackToList: () -> Unit,
    onRefreshProbe: () -> Unit,
    onClearCategory: (SiteDataCategory, String?) -> Unit,
    onRequestClearAllSite: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToList)

    if (isInspecting && inspection == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Probing $origin storage…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(OrbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
    ) {
        if (isInspecting || isClearing) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (inspection?.isHttps == true) Icons.Rounded.Lock else Icons.Rounded.Public,
                            contentDescription = null,
                            tint = if (inspection?.isHttps == true) OrbitEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = inspection?.host ?: origin,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = origin,
                                style = OrbitMonoStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onRefreshProbe,
                            enabled = !isInspecting && !isClearing,
                            modifier = Modifier.testTag("site_detail_refresh")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Re-inspect site")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OrbitBadgeChip(
                            text = "Usage: ${FormatUtils.formatByteSize(inspection?.effectiveUsageBytes ?: 0L)}"
                        )
                        if ((inspection?.openTabsCount ?: 0) > 0) {
                            OrbitBadgeChip(text = "${inspection?.openTabsCount} active tab(s)")
                        } else {
                            OrbitBadgeChip(text = "No open tab")
                        }
                    }

                    OrbitPrimaryButton(
                        text = stringResource(R.string.site_data_clear_all_site),
                        onClick = onRequestClearAllSite,
                        enabled = !isClearing,
                        isDestructive = true,
                        icon = Icons.Rounded.DeleteForever,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("site_detail_clear_all_button")
                    )
                }
            }
        }

        item {
            OrbitSectionHeader(title = "Per-Site Granular Controls")
        }

        if (inspection != null) {
            if (!inspection.probeSucceeded) {
                item {
                    OrbitSurfaceCard {
                        Text(
                            text = "Live DOM probe could not connect for this origin. Showing real CookieManager & WebStorage API metrics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(OrbitSpacing.md)
                        )
                    }
                }
            }
            item {
                SiteCategoryInspectorCard(
                    category = SiteDataCategory.COOKIES,
                    badgeSummary = "${inspection.cookies.size} cookie(s)",
                    emptyMessage = "No cookies stored for this site.",
                    isClearing = isClearing,
                    hasItems = inspection.cookies.isNotEmpty(),
                    onClearAllCategory = { onClearCategory(SiteDataCategory.COOKIES, null) }
                ) {
                    inspection.cookies.forEachIndexed { idx, cookie ->
                        GranularEntryRow(
                            title = cookie.name,
                            subtitle = buildString {
                                append("Domain: ${cookie.domain} · Path: ${cookie.path}")
                                if (cookie.isHttpOnly) append(" · HttpOnly")
                                if (cookie.isSecure) append(" · Secure")
                                if (!cookie.sameSite.isNullOrBlank()) append(" · SameSite=${cookie.sameSite}")
                                if (cookie.valuePreview.isNotBlank()) append("\n${cookie.valuePreview}")
                            },
                            onDeleteItem = { onClearCategory(SiteDataCategory.COOKIES, cookie.name) },
                            enabled = !isClearing,
                            testTag = "del_cookie_${cookie.name}"
                        )
                        if (idx < inspection.cookies.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                }
            }

            item {
                SiteCategoryInspectorCard(
                    category = SiteDataCategory.LOCAL_STORAGE,
                    badgeSummary = "${inspection.localStorageEntries.size} keys · ${FormatUtils.formatByteSize(inspection.localStorageTotalBytes)}",
                    emptyMessage = "LocalStorage is empty for this origin.",
                    isClearing = isClearing,
                    hasItems = inspection.localStorageEntries.isNotEmpty(),
                    onClearAllCategory = { onClearCategory(SiteDataCategory.LOCAL_STORAGE, null) }
                ) {
                    inspection.localStorageEntries.forEachIndexed { idx, item ->
                        GranularEntryRow(
                            title = item.key,
                            subtitle = "${FormatUtils.formatByteSize(item.sizeBytes)} · ${item.valuePreview}",
                            onDeleteItem = { onClearCategory(SiteDataCategory.LOCAL_STORAGE, item.key) },
                            enabled = !isClearing,
                            testTag = "del_local_${item.key}"
                        )
                        if (idx < inspection.localStorageEntries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                }
            }

            item {
                SiteCategoryInspectorCard(
                    category = SiteDataCategory.SESSION_STORAGE,
                    badgeSummary = if (inspection.openTabsCount == 0) {
                        "No active tab"
                    } else {
                        "${inspection.sessionStorageEntries.size} keys · ${FormatUtils.formatByteSize(inspection.sessionStorageTotalBytes)}"
                    },
                    emptyMessage = if (inspection.openTabsCount == 0) {
                        "SessionStorage is temporary per-tab memory. Open this site in a tab to inspect live keys."
                    } else {
                        "SessionStorage is empty in open tab(s)."
                    },
                    isClearing = isClearing,
                    hasItems = inspection.sessionStorageEntries.isNotEmpty(),
                    clearButtonEnabled = inspection.openTabsCount > 0 && !isClearing,
                    onClearAllCategory = { onClearCategory(SiteDataCategory.SESSION_STORAGE, null) }
                ) {
                    inspection.sessionStorageEntries.forEachIndexed { idx, item ->
                        GranularEntryRow(
                            title = item.key,
                            subtitle = "${FormatUtils.formatByteSize(item.sizeBytes)} · ${item.valuePreview}",
                            onDeleteItem = { onClearCategory(SiteDataCategory.SESSION_STORAGE, item.key) },
                            enabled = !isClearing,
                            testTag = "del_session_${item.key}"
                        )
                        if (idx < inspection.sessionStorageEntries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                }
            }

            item {
                SiteCategoryInspectorCard(
                    category = SiteDataCategory.INDEXED_DB,
                    badgeSummary = "${inspection.indexedDbs.size} database(s)",
                    emptyMessage = "No IndexedDB databases present for this origin.",
                    isClearing = isClearing,
                    hasItems = inspection.indexedDbs.isNotEmpty(),
                    onClearAllCategory = { onClearCategory(SiteDataCategory.INDEXED_DB, null) }
                ) {
                    inspection.indexedDbs.forEachIndexed { idx, db ->
                        GranularEntryRow(
                            title = db.name,
                            subtitle = "IndexedDB v${db.version}",
                            onDeleteItem = { onClearCategory(SiteDataCategory.INDEXED_DB, db.name) },
                            enabled = !isClearing,
                            testTag = "del_idb_${db.name}"
                        )
                        if (idx < inspection.indexedDbs.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                }
            }

            item {
                SiteCategoryInspectorCard(
                    category = SiteDataCategory.CACHE,
                    badgeSummary = "${inspection.cacheBuckets.size} cache(s) · ${inspection.serviceWorkers.size} SW",
                    emptyMessage = "No Cache API buckets or Service Workers registered for this origin.",
                    isClearing = isClearing,
                    hasItems = inspection.cacheBuckets.isNotEmpty() || inspection.serviceWorkers.isNotEmpty(),
                    onClearAllCategory = { onClearCategory(SiteDataCategory.CACHE, null) }
                ) {
                    inspection.cacheBuckets.forEachIndexed { idx, bucket ->
                        GranularEntryRow(
                            title = bucket.name,
                            subtitle = "CacheStorage · ${bucket.itemCount} cached response(s)",
                            onDeleteItem = { onClearCategory(SiteDataCategory.CACHE, bucket.name) },
                            enabled = !isClearing,
                            testTag = "del_cache_${bucket.name}"
                        )
                        if (idx < inspection.cacheBuckets.lastIndex || inspection.serviceWorkers.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                    inspection.serviceWorkers.forEachIndexed { idx, sw ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Service Worker",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Scope: ${sw.scope}\nScript: ${sw.scriptUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (idx < inspection.serviceWorkers.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteCategoryInspectorCard(
    category: SiteDataCategory,
    badgeSummary: String,
    emptyMessage: String,
    isClearing: Boolean,
    hasItems: Boolean,
    clearButtonEnabled: Boolean = !isClearing,
    onClearAllCategory: () -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember(category) { mutableStateOf(hasItems) }

    OrbitSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = categoryIcon(category),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = badgeSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OrbitSecondaryButton(
                    text = "Clear",
                    onClick = onClearAllCategory,
                    enabled = clearButtonEnabled,
                    isDestructive = true,
                    modifier = Modifier.testTag("site_clear_${category.name.lowercase()}")
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!hasItems) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun GranularEntryRow(
    title: String,
    subtitle: String,
    onDeleteItem: () -> Unit,
    enabled: Boolean,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = OrbitMonoStyle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onDeleteItem,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete $title",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
