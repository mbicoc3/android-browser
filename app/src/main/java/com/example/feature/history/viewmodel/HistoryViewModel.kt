package com.example.feature.history.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.FormatUtils
import com.example.core.database.entity.HistoryEntity
import com.example.feature.history.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val groupedHistory: Map<String, List<HistoryEntity>> = emptyMap(),
    val searchQuery: String = "",
    val totalCount: Int = 0
)

class HistoryViewModel(
    private val repository: HistoryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeHistory(),
        searchQuery
    ) { entries, query ->
        val filtered = if (query.isBlank()) {
            entries
        } else {
            entries.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true)
            }
        }
        val grouped = filtered.groupBy { FormatUtils.formatDateBucket(it.visitedAt) }
        HistoryUiState(
            groupedHistory = grouped,
            searchQuery = query,
            totalCount = entries.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun onQueryChange(query: String) {
        searchQuery.value = query
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteEntry(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
