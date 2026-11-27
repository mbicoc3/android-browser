package com.example.feature.vault.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feature.vault.repository.DecryptedVaultEntry
import com.example.feature.vault.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(
    private val repository: VaultRepository
) : ViewModel() {

    val entries: StateFlow<List<DecryptedVaultEntry>> = repository.observeDecryptedEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _revealedIds = MutableStateFlow<Set<Long>>(emptySet())
    val revealedIds: StateFlow<Set<Long>> = _revealedIds.asStateFlow()

    fun toggleReveal(id: Long) {
        _revealedIds.value = if (id in _revealedIds.value) {
            _revealedIds.value - id
        } else {
            _revealedIds.value + id
        }
    }

    fun addEntry(label: String, secretContent: String) {
        if (label.isBlank() || secretContent.isBlank()) return
        viewModelScope.launch {
            repository.saveEntry(label, secretContent)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteEntry(id)
        }
    }
}
