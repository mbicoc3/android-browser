package com.example.feature.settings.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.common.SearchEngine
import com.example.core.datastore.BrowserPreferences
import com.example.core.datastore.BrowserPreferencesDataStore
import com.example.core.telemetry.OrbitCrashReporter
import com.example.core.worker.MaintenanceWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import android.net.Uri
import com.example.core.datastore.StartBackgroundMode
import com.example.core.network.DohProvider
import com.example.core.network.NetworkClientFactory
import com.example.core.network.OrbitPrivateDnsHelper
import com.example.core.network.PrivateDnsStatus
import com.example.feature.browser.repository.WallpaperStorageHelper
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CrashDiagnosticsState(
    val logCount: Int = 0,
    val latestSummary: String? = null
)

data class DohTestState(
    val isTesting: Boolean = false,
    val resultSummary: String? = null,
    val isSuccess: Boolean = false
)

class SettingsViewModel(
    private val appContext: Context,
    private val preferencesDataStore: BrowserPreferencesDataStore,
    private val workManager: WorkManager
) : ViewModel() {

    val preferences: StateFlow<BrowserPreferences> = preferencesDataStore.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BrowserPreferences()
        )

    private val _cleanupScheduledMessage = MutableStateFlow<String?>(null)
    val cleanupScheduledMessage: StateFlow<String?> = _cleanupScheduledMessage.asStateFlow()

    private val _crashDiagnostics = MutableStateFlow(CrashDiagnosticsState())
    val crashDiagnostics: StateFlow<CrashDiagnosticsState> = _crashDiagnostics.asStateFlow()

    val privateDnsStatus: StateFlow<PrivateDnsStatus> = OrbitPrivateDnsHelper.observeStatus(appContext)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OrbitPrivateDnsHelper.getCurrentStatus(appContext)
        )

    private val _dohTestState = MutableStateFlow(DohTestState())
    val dohTestState: StateFlow<DohTestState> = _dohTestState.asStateFlow()

    init {
        refreshCrashDiagnostics()
    }

    fun setThemeMode(mode: com.example.core.datastore.AppThemeMode) {
        viewModelScope.launch { preferencesDataStore.setThemeMode(mode) }
    }

    fun setStartBackgroundMode(mode: StartBackgroundMode) {
        viewModelScope.launch { preferencesDataStore.setStartBackgroundMode(mode) }
    }

    fun applySavedExternalWallpaper(savedExternalPath: String) {
        viewModelScope.launch {
            preferencesDataStore.setStartWallpaperUri(savedExternalPath)
            preferencesDataStore.setStartBackgroundMode(StartBackgroundMode.IMAGE)
            _cleanupScheduledMessage.value = "Start Page background image updated"
        }
    }

    fun reportStatusMessage(message: String) {
        _cleanupScheduledMessage.value = message
    }

    fun setLocalWallpaperFromUri(uri: Uri) {
        viewModelScope.launch {
            WallpaperStorageHelper.saveFromLocalUri(appContext, uri)
                .onSuccess { savedExternalPath ->
                    preferencesDataStore.setStartWallpaperUri(savedExternalPath)
                    preferencesDataStore.setStartBackgroundMode(StartBackgroundMode.IMAGE)
                    _cleanupScheduledMessage.value = "Local background image saved externally"
                }
                .onFailure { err ->
                    _cleanupScheduledMessage.value = "Could not load image: ${err.localizedMessage ?: "error"}"
                }
        }
    }

    fun resetWallpaperToDefault() {
        viewModelScope.launch {
            WallpaperStorageHelper.cleanupManagedWallpapers(appContext)
            preferencesDataStore.setStartWallpaperUri("")
            preferencesDataStore.setStartBackgroundMode(StartBackgroundMode.IMAGE)
            _cleanupScheduledMessage.value = "Default background restored"
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { preferencesDataStore.setSearchEngine(engine) }
    }

    fun setHomepageUrl(url: String) {
        viewModelScope.launch { preferencesDataStore.setHomepageUrl(url) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setJavaScriptEnabled(enabled) }
    }

    fun setBlockTrackers(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setBlockTrackers(enabled) }
    }

    fun setCosmeticFilteringEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setCosmeticFilteringEnabled(enabled) }
    }

    fun setForceDarkWeb(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setForceDarkWeb(enabled) }
    }

    fun setDesktopModeDefault(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setDesktopModeDefault(enabled) }
    }

    fun setTabViewMode(mode: com.example.core.datastore.TabViewMode) {
        viewModelScope.launch { preferencesDataStore.setTabViewMode(mode) }
    }

    fun setTabPreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setTabPreviewEnabled(enabled) }
    }

    fun setClearHistoryOnExit(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.setClearHistoryOnExit(enabled) }
    }

    fun setDohProvider(provider: DohProvider) {
        NetworkClientFactory.setDohProvider(provider)
        _dohTestState.value = DohTestState()
        viewModelScope.launch { preferencesDataStore.setDohProvider(provider) }
    }

    fun testDohResolution(provider: DohProvider) {
        viewModelScope.launch(Dispatchers.IO) {
            _dohTestState.value = DohTestState(isTesting = true, resultSummary = null)
            val start = System.currentTimeMillis()
            val res = NetworkClientFactory.dohDns.testProvider(provider, "example.com")
            val elapsedMs = (System.currentTimeMillis() - start).coerceAtLeast(1)
            _dohTestState.value = res.fold(
                onSuccess = { ips ->
                    DohTestState(
                        isTesting = false,
                        isSuccess = true,
                        resultSummary = "${provider.displayName} resolved in ${elapsedMs}ms: ${ips.take(2).joinToString(", ")}"
                    )
                },
                onFailure = { err ->
                    DohTestState(
                        isTesting = false,
                        isSuccess = false,
                        resultSummary = "Resolution failed: ${err.message ?: "Network unavailable"}"
                    )
                }
            )
        }
    }

    fun openPrivateDnsSettings() {
        OrbitPrivateDnsHelper.openPrivateDnsSettings(appContext)
    }

    fun triggerImmediateMaintenance() {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
        workManager.enqueueUniqueWork(
            MaintenanceWorker.UNIQUE_ONETIME_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
        _cleanupScheduledMessage.value = "Cache & stale history cleanup queued via WorkManager"
    }

    fun refreshCrashDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _crashDiagnostics.value = CrashDiagnosticsState(
                logCount = OrbitCrashReporter.getCrashLogCount(appContext),
                latestSummary = OrbitCrashReporter.getLatestCrashSummary(appContext)
            )
        }
    }

    fun clearCrashLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            OrbitCrashReporter.clearCrashLogs(appContext)
            _crashDiagnostics.value = CrashDiagnosticsState(logCount = 0, latestSummary = null)
            _cleanupScheduledMessage.value = "Local crash logs cleared"
        }
    }

    fun clearStatusMessage() {
        _cleanupScheduledMessage.value = null
    }
}
