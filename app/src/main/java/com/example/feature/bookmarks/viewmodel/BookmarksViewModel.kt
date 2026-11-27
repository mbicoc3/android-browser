package com.example.feature.bookmarks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.database.entity.BookmarkEntity
import com.example.feature.bookmarks.repository.BookmarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val folders: List<String> = listOf("All"),
    val selectedFolder: String = "All",
    val filterQuery: String = ""
)

class BookmarksViewModel(
    private val repository: BookmarkRepository
) : ViewModel() {

    private val filterQuery = MutableStateFlow("")
    private val selectedFolder = MutableStateFlow("All")

    val uiState: StateFlow<BookmarksUiState> = combine(
        repository.observeAll(),
        filterQuery,
        selectedFolder
    ) { all, query, folder ->
        val folders = listOf("All") + all.map { it.folder }.distinct().sorted()
        val filtered = all.filter { item ->
            val matchesFolder = folder == "All" || item.folder.equals(folder, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.url.contains(query, ignoreCase = true)
            matchesFolder && matchesQuery
        }
        BookmarksUiState(
            bookmarks = filtered,
            folders = folders,
            selectedFolder = if (folder in folders) folder else "All",
            filterQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookmarksUiState()
    )

    fun onQueryChange(query: String) {
        filterQuery.value = query
    }

    fun onFolderSelected(folder: String) {
        selectedFolder.value = folder
    }

    fun addBookmark(title: String, rawUrl: String, folder: String) {
        if (rawUrl.isBlank()) return
        val normalizedUrl = UrlUtils.resolveInputToUrl(rawUrl, SearchEngine.DUCKDUCKGO)
        viewModelScope.launch {
            repository.addOrUpdate(
                title = title.ifBlank { UrlUtils.extractHost(normalizedUrl) },
                url = normalizedUrl,
                folder = folder
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}
