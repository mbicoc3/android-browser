package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.network.DohProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "orbit_browser_settings"
)

class BrowserPreferencesDataStore(private val context: Context) {

    val preferencesFlow: Flow<BrowserPreferences> = context.browserDataStore.data.map { prefs ->
        BrowserPreferences(
            themeMode = AppThemeMode.fromName(prefs[KEY_THEME_MODE]),
            startBackgroundMode = StartBackgroundMode.fromName(prefs[KEY_START_BG_MODE]),
            startWallpaperUri = prefs[KEY_START_WALLPAPER_URI] ?: DEFAULT_START_WALLPAPER_URL,
            searchEngine = SearchEngine.fromId(prefs[KEY_SEARCH_ENGINE]),
            homepageUrl = prefs[KEY_HOMEPAGE_URL] ?: UrlUtils.START_PAGE_URL,
            javaScriptEnabled = prefs[KEY_JAVASCRIPT_ENABLED] ?: true,
            blockTrackers = prefs[KEY_BLOCK_TRACKERS] ?: true,
            cosmeticFilteringEnabled = prefs[KEY_COSMETIC_FILTERING] ?: true,
            forceDarkWeb = prefs[KEY_FORCE_DARK_WEB] ?: false,
            desktopModeDefault = prefs[KEY_DESKTOP_MODE_DEFAULT] ?: false,
            clearHistoryOnExit = prefs[KEY_CLEAR_HISTORY_ON_EXIT] ?: false,
            tabViewMode = TabViewMode.fromName(prefs[KEY_TAB_VIEW_MODE]),
            tabPreviewEnabled = prefs[KEY_TAB_PREVIEW_ENABLED] ?: true,
            downloadFolderUri = prefs[KEY_DOWNLOAD_FOLDER_URI].orEmpty(),
            downloadFolderLabel = prefs[KEY_DOWNLOAD_FOLDER_LABEL] ?: DEFAULT_DOWNLOAD_FOLDER_LABEL,
            confirmBeforeDownload = prefs[KEY_CONFIRM_BEFORE_DOWNLOAD] ?: true,
            downloadChunkCount = (prefs[KEY_DOWNLOAD_CHUNK_COUNT] ?: 4).coerceIn(1, 8),
            downloadWifiOnly = prefs[KEY_DOWNLOAD_WIFI_ONLY] ?: false,
            lastShareComponent = prefs[KEY_LAST_SHARE_COMPONENT].orEmpty(),
            lastShareAppLabel = prefs[KEY_LAST_SHARE_APP_LABEL].orEmpty(),
            speedDialCollectionNames = parseCollectionNames(prefs[KEY_SPEED_DIAL_COLLECTIONS]),
            dohProvider = DohProvider.fromId(prefs[KEY_DOH_PROVIDER])
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.browserDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setStartBackgroundMode(mode: StartBackgroundMode) {
        context.browserDataStore.edit { it[KEY_START_BG_MODE] = mode.name }
    }

    suspend fun setStartWallpaperUri(uri: String) {
        context.browserDataStore.edit {
            it[KEY_START_WALLPAPER_URI] = uri.ifBlank { DEFAULT_START_WALLPAPER_URL }
        }
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.browserDataStore.edit { it[KEY_SEARCH_ENGINE] = engine.id }
    }

    suspend fun setHomepageUrl(url: String) {
        context.browserDataStore.edit {
            it[KEY_HOMEPAGE_URL] = url.ifBlank { UrlUtils.START_PAGE_URL }
        }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_JAVASCRIPT_ENABLED] = enabled }
    }

    suspend fun setBlockTrackers(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_BLOCK_TRACKERS] = enabled }
    }

    suspend fun setCosmeticFilteringEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_COSMETIC_FILTERING] = enabled }
    }

    suspend fun setForceDarkWeb(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_FORCE_DARK_WEB] = enabled }
    }

    suspend fun setDesktopModeDefault(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_DESKTOP_MODE_DEFAULT] = enabled }
    }

    suspend fun setClearHistoryOnExit(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_CLEAR_HISTORY_ON_EXIT] = enabled }
    }

    suspend fun setTabViewMode(mode: TabViewMode) {
        context.browserDataStore.edit { it[KEY_TAB_VIEW_MODE] = mode.name }
    }

    suspend fun setTabPreviewEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_TAB_PREVIEW_ENABLED] = enabled }
    }

    suspend fun setDownloadFolder(uri: String, label: String) {
        context.browserDataStore.edit {
            it[KEY_DOWNLOAD_FOLDER_URI] = uri
            it[KEY_DOWNLOAD_FOLDER_LABEL] = label.ifBlank { DEFAULT_DOWNLOAD_FOLDER_LABEL }
        }
    }

    suspend fun resetDownloadFolder() {
        context.browserDataStore.edit {
            it[KEY_DOWNLOAD_FOLDER_URI] = ""
            it[KEY_DOWNLOAD_FOLDER_LABEL] = DEFAULT_DOWNLOAD_FOLDER_LABEL
        }
    }

    suspend fun setConfirmBeforeDownload(enabled: Boolean) {
        context.browserDataStore.edit { it[KEY_CONFIRM_BEFORE_DOWNLOAD] = enabled }
    }

    suspend fun setDownloadChunkCount(chunks: Int) {
        context.browserDataStore.edit { it[KEY_DOWNLOAD_CHUNK_COUNT] = chunks.coerceIn(1, 8) }
    }

    suspend fun setDownloadWifiOnly(wifiOnly: Boolean) {
        context.browserDataStore.edit { it[KEY_DOWNLOAD_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setLastShareTarget(componentFlatten: String, appLabel: String) {
        context.browserDataStore.edit {
            it[KEY_LAST_SHARE_COMPONENT] = componentFlatten
            it[KEY_LAST_SHARE_APP_LABEL] = appLabel
        }
    }

    suspend fun setSpeedDialCollectionName(index: Int, name: String) {
        if (index !in DEFAULT_SPEED_DIAL_COLLECTIONS.indices) return
        val clean = name.replace("|", " ").trim().take(20)
            .ifBlank { DEFAULT_SPEED_DIAL_COLLECTIONS[index] }
        context.browserDataStore.edit { prefs ->
            val current = parseCollectionNames(prefs[KEY_SPEED_DIAL_COLLECTIONS]).toMutableList()
            current[index] = clean
            prefs[KEY_SPEED_DIAL_COLLECTIONS] = current.joinToString("|")
        }
    }

    suspend fun setDohProvider(provider: DohProvider) {
        context.browserDataStore.edit { it[KEY_DOH_PROVIDER] = provider.id }
    }

    private fun parseCollectionNames(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return DEFAULT_SPEED_DIAL_COLLECTIONS
        val parts = raw.split("|")
        return DEFAULT_SPEED_DIAL_COLLECTIONS.mapIndexed { idx, fallback ->
            parts.getOrNull(idx)?.trim()?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    companion object {
        const val DEFAULT_DOWNLOAD_FOLDER_LABEL = "Downloads (Default)"
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_START_BG_MODE = stringPreferencesKey("start_bg_mode")
        private val KEY_START_WALLPAPER_URI = stringPreferencesKey("start_wallpaper_uri")
        private val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        private val KEY_HOMEPAGE_URL = stringPreferencesKey("homepage_url")
        private val KEY_JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        private val KEY_BLOCK_TRACKERS = booleanPreferencesKey("block_trackers")
        private val KEY_COSMETIC_FILTERING = booleanPreferencesKey("cosmetic_filtering")
        private val KEY_FORCE_DARK_WEB = booleanPreferencesKey("force_dark_web")
        private val KEY_DESKTOP_MODE_DEFAULT = booleanPreferencesKey("desktop_mode_default")
        private val KEY_CLEAR_HISTORY_ON_EXIT = booleanPreferencesKey("clear_history_on_exit")
        private val KEY_TAB_VIEW_MODE = stringPreferencesKey("tab_view_mode")
        private val KEY_TAB_PREVIEW_ENABLED = booleanPreferencesKey("tab_preview_enabled")
        private val KEY_DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")
        private val KEY_DOWNLOAD_FOLDER_LABEL = stringPreferencesKey("download_folder_label")
        private val KEY_CONFIRM_BEFORE_DOWNLOAD = booleanPreferencesKey("confirm_before_download")
        private val KEY_DOWNLOAD_CHUNK_COUNT = intPreferencesKey("download_chunk_count")
        private val KEY_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        private val KEY_LAST_SHARE_COMPONENT = stringPreferencesKey("last_share_component")
        private val KEY_LAST_SHARE_APP_LABEL = stringPreferencesKey("last_share_app_label")
        private val KEY_SPEED_DIAL_COLLECTIONS = stringPreferencesKey("speed_dial_collections")
        private val KEY_DOH_PROVIDER = stringPreferencesKey("doh_provider")
    }
}
