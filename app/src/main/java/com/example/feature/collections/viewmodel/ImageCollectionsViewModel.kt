package com.example.feature.collections.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.database.entity.ImageCollectionEntity
import com.example.feature.collections.repository.ImageCollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImageCollectionsUiState(
    val items: List<ImageCollectionEntity> = emptyList(),
    val collections: List<String> = listOf("All"),
    val selectedCollection: String = "All",
    val filterQuery: String = "",
    val totalCount: Int = 0
)

class ImageCollectionsViewModel(
    private val repository: ImageCollectionRepository
) : ViewModel() {

    private val filterQuery = MutableStateFlow("")
    private val selectedCollection = MutableStateFlow("All")

    val uiState: StateFlow<ImageCollectionsUiState> = combine(
        repository.observeAll(),
        filterQuery,
        selectedCollection
    ) { all, query, activeCollection ->
        val collections = listOf("All") + all.map { it.collectionName }.distinct().sorted()
        val resolvedSelection = if (activeCollection in collections) activeCollection else "All"
        val filtered = all.filter { item ->
            val matchesCollection = resolvedSelection == "All" ||
                item.collectionName.equals(resolvedSelection, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.imageUrl.contains(query, ignoreCase = true) ||
                item.sourcePageUrl.contains(query, ignoreCase = true) ||
                item.collectionName.contains(query, ignoreCase = true)
            matchesCollection && matchesQuery
        }
        ImageCollectionsUiState(
            items = filtered,
            collections = collections,
            selectedCollection = resolvedSelection,
            filterQuery = query,
            totalCount = all.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImageCollectionsUiState()
    )

    fun onQueryChange(query: String) {
        filterQuery.value = query
    }

    fun onCollectionSelected(collectionName: String) {
        selectedCollection.value = collectionName
    }

    fun addImageUrl(
        title: String,
        rawImageUrl: String,
        sourcePageUrl: String = "",
        collectionName: String = ImageCollectionRepository.DEFAULT_COLLECTION
    ) {
        val trimmed = rawImageUrl.trim()
        if (trimmed.isBlank()) return
        val normalizedUrl = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            UrlUtils.resolveInputToUrl(trimmed, SearchEngine.DUCKDUCKGO)
        }
        viewModelScope.launch {
            repository.collectImage(
                title = title,
                imageUrl = normalizedUrl,
                sourcePageUrl = sourcePageUrl,
                collectionName = collectionName
            )
        }
    }

    fun moveToCollection(id: Long, collectionName: String) {
        viewModelScope.launch {
            repository.moveToCollection(id, collectionName)
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }
}
