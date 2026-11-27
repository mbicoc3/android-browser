package com.example.feature.offline.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.OfflinePageEntity
import com.example.feature.offline.repository.OfflinePagesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OfflinePagesUiState(
    val pages: List<OfflinePageEntity> = emptyList(),
    val searchQuery: String = ""
)

class OfflinePagesViewModel(
    private val repository: OfflinePagesRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")

    val uiState: StateFlow<OfflinePagesUiState> = combine(
        repository.observeAll(),
        searchQueryFlow
    ) { pages, query ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            pages
        } else {
            pages.filter { page ->
                page.title.contains(trimmed, ignoreCase = true) ||
                    page.originalUrl.contains(trimmed, ignoreCase = true)
            }
        }
        OfflinePagesUiState(pages = filtered, searchQuery = query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OfflinePagesUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun deletePage(page: OfflinePageEntity) {
        viewModelScope.launch {
            repository.deletePage(page)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
