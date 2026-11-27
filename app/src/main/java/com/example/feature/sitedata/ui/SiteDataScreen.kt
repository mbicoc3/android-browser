package com.example.feature.sitedata.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.UrlUtils
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFilterField
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.sitedata.model.SiteFilterMode
import com.example.feature.sitedata.model.SiteSortOrder
import com.example.feature.sitedata.viewmodel.SiteDataViewModel

@Composable
fun SiteDataScreen(
    viewModel: SiteDataViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmGlobalClear by remember { mutableStateOf(false) }
    var pendingClearSiteOrigin by remember { mutableStateOf<String?>(null) }
    var pendingReturnToList by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val isInDetail = state.selectedOrigin != null

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            OrbitTopBar(
                title = if (isInDetail) {
                    UrlUtils.extractHost(state.selectedOrigin)
                } else {
                    stringResource(R.string.site_data_title)
                },
                subtitle = if (isInDetail) {
                    "Site Cookies, Storage, IndexedDB & Cache"
                } else {
                    stringResource(R.string.site_data_subtitle)
                },
                onBackClick = {
                    if (isInDetail) {
                        viewModel.closeSiteDetail()
                    } else {
                        onBack()
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                if (isInDetail) {
                    val activeOrigin = state.selectedOrigin!!
                    SiteDetailContent(
                        origin = activeOrigin,
                        inspection = state.siteInspection,
                        isInspecting = state.isInspectingSite,
                        isClearing = state.isClearing,
                        onBackToList = viewModel::closeSiteDetail,
                        onRefreshProbe = { viewModel.inspectSite(activeOrigin) },
                        onClearCategory = { category, key ->
                            viewModel.clearSiteCategory(activeOrigin, category, key)
                        },
                        onRequestClearAllSite = {
                            pendingClearSiteOrigin = activeOrigin
                            pendingReturnToList = false
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(OrbitSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                    ) {
                        item {
                            StorageTelemetryCard(
                                metrics = state.metrics,
                                isSyncing = state.isSyncing,
                                onRefresh = viewModel::refreshOverview
                            )
                        }

                        item {
                            GlobalCategoryCleanupCard(
                                selection = state.globalSelection,
                                isClearing = state.isClearing,
                                onToggleCategory = viewModel::toggleGlobalCategory,
                                onClearSingleCategory = viewModel::clearSingleGlobalCategory,
                                onRequestClearSelected = { confirmGlobalClear = true }
                            )
                        }

                        item {
                            OrbitSectionHeader(title = "Per-Site Storage & Cleanup (${state.sites.size})")
                        }

                        item {
                            OrbitFilterField(
                                query = state.searchQuery,
                                onQueryChange = viewModel::onSearchQueryChanged,
                                placeholder = stringResource(R.string.site_data_search),
                                testTag = "site_data_search_field"
                            )
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(SiteFilterMode.entries) { mode ->
                                        OrbitSegmentPill(
                                            label = mode.label,
                                            selected = state.filterMode == mode,
                                            onClick = { viewModel.onFilterModeChanged(mode) },
                                            modifier = Modifier.testTag("filter_${mode.name.lowercase()}")
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SiteSortOrder.entries.forEach { order ->
                                        OrbitSegmentPill(
                                            label = "Sort: ${order.label}",
                                            selected = state.sortOrder == order,
                                            onClick = { viewModel.onSortOrderChanged(order) },
                                            modifier = Modifier.testTag("sort_${order.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        if (state.sites.isEmpty()) {
                            item {
                                OrbitEmptyState(
                                    title = stringResource(R.string.site_data_empty_title),
                                    subtitle = stringResource(R.string.site_data_empty_subtitle)
                                )
                            }
                        } else {
                            items(state.sites, key = { it.origin }) { site ->
                                SiteSummaryRowCard(
                                    site = site,
                                    onInspectClick = { viewModel.inspectSite(site.origin) },
                                    onQuickClearAll = {
                                        pendingClearSiteOrigin = site.origin
                                        pendingReturnToList = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmGlobalClear) {
        OrbitConfirmDialog(
            title = "Clear Selected Data?",
            message = "This will permanently clear the ${state.globalSelection.selectedCount} selected storage categories across Orbit Browser.",
            confirmText = "Clear Now",
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmGlobalClear = false
                viewModel.clearSelectedGlobalCategories()
            },
            onDismiss = { confirmGlobalClear = false }
        )
    }

    pendingClearSiteOrigin?.let { targetOrigin ->
        OrbitConfirmDialog(
            title = "Clear All Site Data?",
            message = "Delete all Cookies, LocalStorage, SessionStorage, IndexedDB, and Cache data stored by ${UrlUtils.extractHost(targetOrigin)}?",
            confirmText = "Clear All",
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                pendingClearSiteOrigin = null
                viewModel.clearAllForSite(targetOrigin, returnToListAfter = pendingReturnToList)
            },
            onDismiss = { pendingClearSiteOrigin = null }
        )
    }
}
