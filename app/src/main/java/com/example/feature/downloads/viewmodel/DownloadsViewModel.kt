package com.example.feature.downloads.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.DownloadEntity
import com.example.core.datastore.BrowserPreferences
import com.example.feature.downloads.model.DownloadFilter
import com.example.feature.downloads.model.DownloadProbeResult
import com.example.feature.downloads.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val repository: DownloadRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = repository.observeDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val preferences: StateFlow<BrowserPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowserPreferences()
        )

    private val _selectedFilter = MutableStateFlow(DownloadFilter.ALL)
    val selectedFilter: StateFlow<DownloadFilter> = _selectedFilter.asStateFlow()

    private val _probeResult = MutableStateFlow<DownloadProbeResult?>(null)
    val probeResult: StateFlow<DownloadProbeResult?> = _probeResult.asStateFlow()

    fun setFilter(filter: DownloadFilter) {
        _selectedFilter.value = filter
    }

    fun probeUrl(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            _probeResult.value = null
            return
        }
        _probeResult.value = DownloadProbeResult(url = trimmed, isProbing = true)
        viewModelScope.launch {
            val result = repository.probeRemoteFile(trimmed)
            _probeResult.value = result
        }
    }

    fun clearProbe() {
        _probeResult.value = null
    }

    fun startDownload(
        url: String,
        fileName: String? = null,
        mimeType: String? = null,
        chunkCount: Int? = null,
        folderUri: String? = null,
        folderLabel: String? = null,
        wifiOnly: Boolean? = null
    ) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return
        viewModelScope.launch {
            repository.enqueueDownload(
                url = trimmed,
                suggestedFileName = fileName,
                mimeType = mimeType,
                chunkCountOverride = chunkCount,
                targetFolderUriOverride = folderUri,
                targetFolderLabelOverride = folderLabel,
                wifiOnlyOverride = wifiOnly
            )
        }
    }

    fun pauseDownload(entity: DownloadEntity) {
        viewModelScope.launch { repository.pauseDownload(entity) }
    }

    fun resumeDownload(entity: DownloadEntity) {
        viewModelScope.launch { repository.resumeDownload(entity) }
    }

    fun retryDownload(entity: DownloadEntity) {
        viewModelScope.launch { repository.retryDownload(entity) }
    }

    fun cancelDownload(entity: DownloadEntity) {
        viewModelScope.launch { repository.cancelDownload(entity) }
    }

    fun deleteDownload(entity: DownloadEntity) {
        viewModelScope.launch { repository.deleteDownload(entity) }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinished() }
    }

    fun setDownloadFolder(uri: Uri) {
        viewModelScope.launch { repository.setDownloadFolder(uri) }
    }

    fun resetDownloadFolder() {
        viewModelScope.launch { repository.resetDownloadFolder() }
    }

    fun setConfirmBeforeDownload(enabled: Boolean) {
        viewModelScope.launch { repository.setConfirmBeforeDownload(enabled) }
    }

    fun setDownloadChunkCount(chunks: Int) {
        viewModelScope.launch { repository.setDownloadChunkCount(chunks) }
    }

    fun setDownloadWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { repository.setDownloadWifiOnly(wifiOnly) }
    }
}
