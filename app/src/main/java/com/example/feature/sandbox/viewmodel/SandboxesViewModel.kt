package com.example.feature.sandbox.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feature.sandbox.model.SandboxUiState
import com.example.feature.sandbox.repository.SandboxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SandboxesViewModel(
    private val repository: SandboxRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SandboxUiState(isMultiProfileSupported = repository.isMultiProfileSupported())
    )
    val uiState: StateFlow<SandboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSandboxes().collectLatest { list ->
                _uiState.update { it.copy(sandboxes = list) }
            }
        }
    }

    fun createSandbox(
        name: String,
        siteUrl: String,
        colorHex: Long,
        iconKey: String,
        keepLongTerm: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val created = repository.createSandbox(
                name = name,
                rawSiteUrl = siteUrl,
                colorHex = colorHex,
                iconKey = iconKey,
                keepLongTerm = keepLongTerm
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = "Sandbox \"${created.name}\" created"
                )
            }
        }
    }

    fun updateSandbox(
        id: String,
        name: String,
        siteUrl: String,
        colorHex: Long,
        iconKey: String,
        keepLongTerm: Boolean
    ) {
        viewModelScope.launch {
            repository.updateSandbox(
                id = id,
                name = name,
                rawSiteUrl = siteUrl,
                colorHex = colorHex,
                iconKey = iconKey,
                keepLongTerm = keepLongTerm
            )
            _uiState.update { it.copy(statusMessage = "Sandbox updated") }
        }
    }

    fun clearSandboxData(sandboxId: String, sandboxName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val cleared = repository.clearSandboxData(sandboxId)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = if (cleared) {
                        "Cleared cookies & site storage for $sandboxName"
                    } else {
                        "Session metadata reset for $sandboxName"
                    }
                )
            }
        }
    }

    fun deleteSandbox(
        sandboxId: String,
        sandboxName: String,
        onBeforeDeleteCloseTabs: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            onBeforeDeleteCloseTabs(sandboxId)
            repository.deleteSandbox(sandboxId)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = "Deleted sandbox \"$sandboxName\""
                )
            }
        }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
