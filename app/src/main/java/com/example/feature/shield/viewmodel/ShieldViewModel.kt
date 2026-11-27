package com.example.feature.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feature.shield.model.RuleEvaluationPreview
import com.example.feature.shield.model.ShieldUiState
import com.example.feature.shield.repository.ShieldRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShieldViewModel(
    private val repository: ShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShieldUiState())
    val uiState: StateFlow<ShieldUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitializedAndCompile()
        }
        viewModelScope.launch {
            combine(
                repository.observeFilterLists(),
                repository.observeCustomRules(),
                repository.preferencesFlow,
                repository.engineStats
            ) { lists, customRules, prefs, stats ->
                _uiState.value.copy(
                    filterLists = lists,
                    customRules = customRules,
                    trackerShieldEnabled = prefs.blockTrackers,
                    cosmeticFilteringEnabled = prefs.cosmeticFilteringEnabled,
                    engineStats = stats
                )
            }.collect { next ->
                _uiState.value = next
            }
        }
    }

    fun setTrackerShieldEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setTrackerShieldEnabled(enabled) }
    }

    fun setCosmeticFilteringEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setCosmeticFilteringEnabled(enabled) }
    }

    fun toggleFilterList(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setFilterListEnabled(id, enabled) }
    }

    fun syncListsNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val updated = repository.performNetworkSync()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    statusBanner = if (updated > 0) {
                        "Updated $updated open-source filter list(s)"
                    } else {
                        "Using cached & built-in rules (offline or no lists enabled)"
                    }
                )
            }
        }
    }

    fun addCustomRule(rawRule: String) {
        viewModelScope.launch {
            val result = repository.addCustomRule(rawRule)
            _uiState.update {
                it.copy(
                    statusBanner = result.fold(
                        onSuccess = { "Rule added & compiled into Shield" },
                        onFailure = { err -> err.message ?: "Invalid rule" }
                    )
                )
            }
        }
    }

    fun toggleCustomRule(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setCustomRuleEnabled(id, enabled) }
    }

    fun deleteCustomRule(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomRule(id)
            _uiState.update { it.copy(statusBanner = "Custom rule removed") }
        }
    }

    fun testUrl(requestUrl: String, documentHost: String?) {
        val normalizedUrl = if (requestUrl.contains("://")) requestUrl.trim() else "https://${requestUrl.trim()}/"
        val decision = repository.evaluateTestUrl(normalizedUrl, documentHost)
        _uiState.update {
            it.copy(
                testPreview = RuleEvaluationPreview(
                    requestUrl = normalizedUrl,
                    documentHost = documentHost?.takeIf { host -> host.isNotBlank() },
                    blocked = decision.shouldBlock,
                    matchedRule = decision.matchedRule,
                    listSource = decision.listSource
                )
            )
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }
}
