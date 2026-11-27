package com.example.feature.sitedata.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.UrlUtils
import com.example.feature.browser.webview.OrbitWebViewPool
import com.example.feature.sitedata.model.GlobalClearSelection
import com.example.feature.sitedata.model.GlobalStorageMetrics
import com.example.feature.sitedata.model.SiteDataCategory
import com.example.feature.sitedata.model.SiteDetailedInspection
import com.example.feature.sitedata.model.SiteFilterMode
import com.example.feature.sitedata.model.SiteSortOrder
import com.example.feature.sitedata.model.SiteStorageSummary
import com.example.feature.sitedata.repository.SiteDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteDataUiState(
    val sites: List<SiteStorageSummary> = emptyList(),
    val metrics: GlobalStorageMetrics = GlobalStorageMetrics(),
    val searchQuery: String = "",
    val sortOrder: SiteSortOrder = SiteSortOrder.LARGEST_FIRST,
    val filterMode: SiteFilterMode = SiteFilterMode.ALL,
    val globalSelection: GlobalClearSelection = GlobalClearSelection(),
    val selectedOrigin: String? = null,
    val siteInspection: SiteDetailedInspection? = null,
    val isSyncing: Boolean = false,
    val isInspectingSite: Boolean = false,
    val isClearing: Boolean = false,
    val statusMessage: String? = null
)

class SiteDataViewModel(
    private val repository: SiteDataRepository,
    private val webViewPool: OrbitWebViewPool,
    initialOrigin: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SiteDataUiState(selectedOrigin = initialOrigin?.let { UrlUtils.normalizeOrigin(it) })
    )
    val uiState: StateFlow<SiteDataUiState> = _uiState.asStateFlow()

    private val openTabsByOriginFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val queryFlow = MutableStateFlow("")
    private val sortOrderFlow = MutableStateFlow(SiteSortOrder.LARGEST_FIRST)
    private val filterModeFlow = MutableStateFlow(SiteFilterMode.ALL)

    init {
        observeFilteredSites()
        refreshOverview()
        _uiState.value.selectedOrigin?.let { inspectSite(it) }
    }

    private fun observeFilteredSites() {
        viewModelScope.launch {
            combine(
                repository.observeSiteRecords(),
                openTabsByOriginFlow,
                queryFlow,
                sortOrderFlow,
                filterModeFlow
            ) { entities, openTabsMap, query, sort, filter ->
                val summaries = SiteDataRepository.mapEntitiesToSummaries(entities, openTabsMap)
                val q = query.trim().lowercase()
                val searched = if (q.isEmpty()) {
                    summaries
                } else {
                    summaries.filter {
                        it.host.lowercase().contains(q) ||
                            it.origin.lowercase().contains(q) ||
                            it.title.lowercase().contains(q)
                    }
                }
                val filtered = when (filter) {
                    SiteFilterMode.ALL -> searched
                    SiteFilterMode.ACTIVE_TABS -> searched.filter { it.openTabsCount > 0 }
                    SiteFilterMode.HAS_COOKIES -> searched.filter { it.cookieCount > 0 }
                    SiteFilterMode.HAS_STORAGE -> searched.filter {
                        it.estimatedTotalBytes > 0L ||
                            it.localStorageCount > 0 ||
                            it.sessionStorageCount > 0 ||
                            it.indexedDbCount > 0 ||
                            it.cacheStorageCount > 0
                    }
                }
                when (sort) {
                    SiteSortOrder.LARGEST_FIRST -> filtered.sortedWith(
                        compareByDescending<SiteStorageSummary> { it.estimatedTotalBytes }
                            .thenByDescending { it.cookieCount }
                            .thenByDescending { it.lastVisitedAt }
                    )
                    SiteSortOrder.RECENT_FIRST -> filtered.sortedByDescending { it.lastVisitedAt }
                    SiteSortOrder.NAME_ASC -> filtered.sortedBy { it.host.lowercase() }
                }
            }.collectLatest { visibleSites ->
                _uiState.update { it.copy(sites = visibleSites) }
            }
        }
    }

    fun refreshOverview() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            runCatching {
                repository.syncAllSiteTelemetry(webViewPool)
            }.onSuccess { (metrics, openMap) ->
                openTabsByOriginFlow.value = openMap
                _uiState.update { it.copy(metrics = metrics, isSyncing = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = "Sync issue: ${err.message ?: "Unable to read storage"}"
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        queryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortOrderChanged(order: SiteSortOrder) {
        sortOrderFlow.value = order
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun onFilterModeChanged(mode: SiteFilterMode) {
        filterModeFlow.value = mode
        _uiState.update { it.copy(filterMode = mode) }
    }

    fun toggleGlobalCategory(category: SiteDataCategory) {
        _uiState.update { state ->
            val current = state.globalSelection
            val updated = when (category) {
                SiteDataCategory.COOKIES -> current.copy(clearCookies = !current.clearCookies)
                SiteDataCategory.LOCAL_STORAGE -> current.copy(clearLocalStorage = !current.clearLocalStorage)
                SiteDataCategory.SESSION_STORAGE -> current.copy(clearSessionStorage = !current.clearSessionStorage)
                SiteDataCategory.INDEXED_DB -> current.copy(clearIndexedDb = !current.clearIndexedDb)
                SiteDataCategory.CACHE -> current.copy(clearCache = !current.clearCache)
            }
            state.copy(globalSelection = updated)
        }
    }

    fun clearSingleGlobalCategory(category: SiteDataCategory) {
        val selection = GlobalClearSelection(
            clearCookies = category == SiteDataCategory.COOKIES,
            clearLocalStorage = category == SiteDataCategory.LOCAL_STORAGE,
            clearSessionStorage = category == SiteDataCategory.SESSION_STORAGE,
            clearIndexedDb = category == SiteDataCategory.INDEXED_DB,
            clearCache = category == SiteDataCategory.CACHE
        )
        runGlobalClear(selection, "${category.title} cleared across Orbit")
    }

    fun clearSelectedGlobalCategories() {
        val selection = _uiState.value.globalSelection
        if (selection.selectedCount == 0) {
            _uiState.update { it.copy(statusMessage = "Select at least one data category to clear") }
            return
        }
        runGlobalClear(selection, "Cleared ${selection.selectedCount} selected data categories")
    }

    private fun runGlobalClear(selection: GlobalClearSelection, successText: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            runCatching {
                repository.clearGlobalSelection(selection, webViewPool)
            }.onSuccess { (metrics, openMap) ->
                openTabsByOriginFlow.value = openMap
                _uiState.update {
                    it.copy(
                        metrics = metrics,
                        isClearing = false,
                        statusMessage = successText
                    )
                }
                _uiState.value.selectedOrigin?.let { inspectSite(it) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        statusMessage = "Cleanup failed: ${err.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun inspectSite(origin: String) {
        val normalized = UrlUtils.normalizeOrigin(origin) ?: origin
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedOrigin = normalized,
                    isInspectingSite = true
                )
            }
            runCatching {
                repository.inspectSiteDetailed(normalized, webViewPool)
            }.onSuccess { inspection ->
                _uiState.update {
                    it.copy(
                        siteInspection = inspection,
                        isInspectingSite = false
                    )
                }
                refreshOverviewSilently()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isInspectingSite = false,
                        statusMessage = "Inspection error: ${err.message ?: "Unable to probe origin"}"
                    )
                }
            }
        }
    }

    fun closeSiteDetail() {
        _uiState.update {
            it.copy(
                selectedOrigin = null,
                siteInspection = null,
                isInspectingSite = false
            )
        }
        refreshOverviewSilently()
    }

    fun clearSiteCategory(
        origin: String,
        category: SiteDataCategory,
        specificItemKey: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            runCatching {
                repository.clearSiteCategory(origin, category, specificItemKey, webViewPool)
            }.onSuccess { updatedInspection ->
                val label = if (specificItemKey != null) {
                    "Removed '${specificItemKey}' from ${category.shortLabel}"
                } else {
                    "Cleared ${category.title} for ${updatedInspection.host}"
                }
                _uiState.update {
                    it.copy(
                        siteInspection = updatedInspection,
                        isClearing = false,
                        statusMessage = label
                    )
                }
                refreshOverviewSilently()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        statusMessage = "Failed to clear ${category.title}: ${err.message ?: "Error"}"
                    )
                }
            }
        }
    }

    fun clearAllForSite(origin: String, returnToListAfter: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            runCatching {
                repository.clearAllForSite(origin, webViewPool)
            }.onSuccess {
                val host = UrlUtils.extractHost(origin)
                if (returnToListAfter) {
                    _uiState.update {
                        it.copy(
                            selectedOrigin = null,
                            siteInspection = null,
                            isClearing = false,
                            statusMessage = "All data cleared for $host"
                        )
                    }
                    refreshOverviewSilently()
                } else {
                    _uiState.update {
                        it.copy(
                            isClearing = false,
                            statusMessage = "All data cleared for $host"
                        )
                    }
                    inspectSite(origin)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        statusMessage = "Site clear failed: ${err.message ?: "Error"}"
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun refreshOverviewSilently() {
        viewModelScope.launch {
            runCatching {
                repository.syncAllSiteTelemetry(webViewPool)
            }.onSuccess { (metrics, openMap) ->
                openTabsByOriginFlow.value = openMap
                _uiState.update { it.copy(metrics = metrics) }
            }
        }
    }
}
