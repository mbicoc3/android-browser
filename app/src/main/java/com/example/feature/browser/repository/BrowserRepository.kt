package com.example.feature.browser.repository

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.database.dao.BrowserTabDao
import com.example.core.database.dao.SiteRecordDao
import com.example.core.database.dao.SpeedDialDao
import com.example.core.database.entity.BrowserTabEntity
import com.example.core.database.entity.SiteRecordEntity
import com.example.core.database.entity.SpeedDialEntity
import com.example.core.datastore.BrowserPreferences
import com.example.core.datastore.BrowserPreferencesDataStore
import com.example.core.network.FaviconExtractor
import com.example.core.network.FaviconHtmlParser
import com.example.core.network.SearchSuggestionApi
import com.example.feature.bookmarks.repository.BookmarkRepository
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.SpeedDialShortcut
import com.example.feature.downloads.repository.DownloadRepository
import com.example.feature.history.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class BrowserRepository(
    private val appContext: Context,
    private val tabDao: BrowserTabDao,
    private val speedDialDao: SpeedDialDao,
    private val siteRecordDao: SiteRecordDao,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesDataStore: BrowserPreferencesDataStore,
    private val suggestionApi: SearchSuggestionApi,
    private val faviconExtractor: FaviconExtractor
) {
    val preferencesFlow: Flow<BrowserPreferences> = preferencesDataStore.preferencesFlow
    private val inFlightIconIds = ConcurrentHashMap.newKeySet<Long>()
    private val attemptedHosts = ConcurrentHashMap.newKeySet<String>()

    fun observeSpeedDial(): Flow<List<SpeedDialShortcut>> =
        speedDialDao.observeAll().map { entities ->
            entities.map { entity ->
                val validIconPath = entity.iconPath.takeIf { it.isNotBlank() && File(it).exists() }.orEmpty()
                SpeedDialShortcut(
                    id = entity.id,
                    title = entity.title,
                    url = entity.url,
                    shortBadge = FaviconHtmlParser.extractFirstLetterBadge(entity.title, entity.url),
                    accentColor = Color(entity.accentColorHex),
                    iconPath = validIconPath,
                    collectionIndex = entity.collectionIndex.coerceIn(0, 3)
                )
            }
        }

    suspend fun ensureDefaultSpeedDial() {
        if (speedDialDao.count() == 0) {
            val defaults = listOf(
                SpeedDialEntity(title = "DuckDuckGo", url = "https://duckduckgo.com", shortBadge = "D", accentColorHex = 0xFFDE5833, position = 0, collectionIndex = 0),
                SpeedDialEntity(title = "Wikipedia", url = "https://en.wikipedia.org", shortBadge = "W", accentColorHex = 0xFF38BDF8, position = 1, collectionIndex = 0),
                SpeedDialEntity(title = "GitHub", url = "https://github.com", shortBadge = "G", accentColorHex = 0xFF6366F1, position = 2, collectionIndex = 0),
                SpeedDialEntity(title = "Android Dev", url = "https://developer.android.com", shortBadge = "A", accentColorHex = 0xFF10B981, position = 3, collectionIndex = 0),
                SpeedDialEntity(title = "MDN Docs", url = "https://developer.mozilla.org", shortBadge = "M", accentColorHex = 0xFF2563EB, position = 4, collectionIndex = 0),
                SpeedDialEntity(title = "Pinterest", url = "https://www.pinterest.com", shortBadge = "P", accentColorHex = 0xFFE60023, position = 5, collectionIndex = 0),
                SpeedDialEntity(title = "Notion", url = "https://www.notion.so", shortBadge = "N", accentColorHex = 0xFF0EA5E9, position = 6, collectionIndex = 0),
                SpeedDialEntity(title = "Hacker News", url = "https://news.ycombinator.com", shortBadge = "H", accentColorHex = 0xFFF59E0B, position = 7, collectionIndex = 0)
            )
            speedDialDao.insertAll(defaults)
        }
    }

    suspend fun syncMissingShortcutIcons() = supervisorScope {
        val items = speedDialDao.getAllSnapshot()
        val semaphore = Semaphore(4)
        items.forEach { entity ->
            val hasFile = entity.iconPath.isNotBlank() && File(entity.iconPath).exists()
            if (hasFile) return@forEach
            val host = UrlUtils.extractHost(entity.url).lowercase()
            if (host.isBlank()) return@forEach
            val cached = faviconExtractor.getCachedIconPathIfValid(appContext, entity.url)
            if (cached != null) {
                speedDialDao.updateIconPath(entity.id, cached)
                return@forEach
            }
            if (!attemptedHosts.add(host) || !inFlightIconIds.add(entity.id)) return@forEach
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val extracted = faviconExtractor.extractAndCacheIcon(appContext, entity.url)
                        if (!extracted.isNullOrBlank()) {
                            speedDialDao.updateIconPath(entity.id, extracted)
                        }
                    } finally {
                        inFlightIconIds.remove(entity.id)
                    }
                }
            }
        }
    }

    suspend fun addSpeedDial(
        title: String,
        rawUrl: String,
        searchEngine: SearchEngine,
        collectionIndex: Int = 0
    ): Pair<Long, String> {
        val resolved = UrlUtils.resolveInputToUrl(rawUrl, searchEngine)
        val cleanTitle = title.ifBlank { UrlUtils.extractHost(resolved).removePrefix("www.") }
        val badge = FaviconHtmlParser.extractFirstLetterBadge(cleanTitle, resolved)
        val colorHex = FaviconHtmlParser.pickAccentColorHex(cleanTitle, resolved)
        val cachedIcon = faviconExtractor.getCachedIconPathIfValid(appContext, resolved).orEmpty()
        val position = speedDialDao.count()
        val insertedId = speedDialDao.insert(
            SpeedDialEntity(
                title = cleanTitle,
                url = resolved,
                shortBadge = badge,
                accentColorHex = colorHex,
                position = position,
                iconPath = cachedIcon,
                collectionIndex = collectionIndex.coerceIn(0, 3)
            )
        )
        return insertedId to resolved
    }

    suspend fun refreshSpeedDialIcon(id: Long, url: String, forceRefresh: Boolean = true): Boolean {
        if (!inFlightIconIds.add(id)) return false
        return try {
            val path = faviconExtractor.extractAndCacheIcon(appContext, url, forceRefresh = forceRefresh)
            if (!path.isNullOrBlank()) {
                speedDialDao.updateIconPath(id, path)
                true
            } else {
                false
            }
        } finally {
            inFlightIconIds.remove(id)
        }
    }

    suspend fun moveSpeedDialToCollection(id: Long, collectionIndex: Int) {
        speedDialDao.updateCollection(id, collectionIndex.coerceIn(0, 3))
    }

    suspend fun setSpeedDialCollectionName(index: Int, name: String) {
        preferencesDataStore.setSpeedDialCollectionName(index, name)
    }

    suspend fun onLiveFaviconBitmap(pageUrl: String, bitmap: Bitmap) {
        val savedPath = faviconExtractor.saveLiveBitmapForUrl(appContext, pageUrl, bitmap) ?: return
        updateShortcutsMatchingHost(pageUrl, savedPath)
    }

    suspend fun onLiveFaviconUrls(pageUrl: String, iconUrls: List<String>) {
        val host = UrlUtils.extractHost(pageUrl).lowercase()
        if (host.isBlank()) return
        val existingCached = faviconExtractor.getCachedIconPathIfValid(appContext, pageUrl)
        val resolvedPath = existingCached ?: faviconExtractor.saveFromCandidateUrls(appContext, pageUrl, iconUrls)
        if (!resolvedPath.isNullOrBlank()) {
            updateShortcutsMatchingHost(pageUrl, resolvedPath)
        }
    }

    private suspend fun updateShortcutsMatchingHost(pageUrl: String, iconPath: String) {
        val targetHost = UrlUtils.extractHost(pageUrl).lowercase()
        if (targetHost.isBlank()) return
        val shortcuts = speedDialDao.getAllSnapshot()
        shortcuts.filter { UrlUtils.extractHost(it.url).equals(targetHost, ignoreCase = true) }
            .forEach { entity ->
                if (entity.iconPath != iconPath) {
                    speedDialDao.updateIconPath(entity.id, iconPath)
                }
            }
    }

    suspend fun deleteSpeedDial(id: Long) {
        speedDialDao.deleteById(id)
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        preferencesDataStore.setSearchEngine(engine)
    }

    suspend fun setThemeMode(mode: com.example.core.datastore.AppThemeMode) {
        preferencesDataStore.setThemeMode(mode)
    }

    suspend fun setStartBackgroundMode(mode: com.example.core.datastore.StartBackgroundMode) {
        preferencesDataStore.setStartBackgroundMode(mode)
    }

    suspend fun setStartWallpaperUri(uri: String) {
        preferencesDataStore.setStartWallpaperUri(uri)
    }

    fun observeIsBookmarked(url: String): Flow<Boolean> =
        bookmarkRepository.observeIsBookmarked(url)

    suspend fun loadSavedTabs(): List<BrowserTabEntity> = tabDao.getStandardTabsSnapshot()

    suspend fun saveStandardTab(tab: BrowserTabModel, isActive: Boolean, position: Int) {
        if (tab.isIncognito) return
        tabDao.upsertTab(
            BrowserTabEntity(
                id = tab.id,
                title = tab.title,
                url = tab.url,
                isIncognito = false,
                isDesktopMode = tab.isDesktopMode,
                isActive = isActive,
                position = position,
                updatedAt = System.currentTimeMillis(),
                sandboxId = tab.sandboxId
            )
        )
    }

    suspend fun setActiveStandardTab(tabId: String) {
        tabDao.setActiveTab(tabId)
    }

    suspend fun deleteStandardTab(tabId: String) {
        tabDao.deleteTab(tabId)
    }

    suspend fun clearAllStandardTabs() {
        tabDao.clearAllStandardTabs()
    }

    suspend fun recordHistoryIfAllowed(title: String, url: String, isIncognito: Boolean) {
        if (isIncognito || UrlUtils.isStartPage(url)) return
        historyRepository.recordVisit(title, url)
        val origin = UrlUtils.extractOrigin(url) ?: return
        val existing = siteRecordDao.getByOrigin(origin)
        val cleanTitle = title.ifBlank { UrlUtils.extractHost(url) }
        siteRecordDao.upsert(
            (existing ?: SiteRecordEntity(
                origin = origin,
                host = UrlUtils.extractHost(origin),
                title = cleanTitle
            )).copy(
                title = cleanTitle.ifBlank { existing?.title.orEmpty() },
                lastVisitedAt = System.currentTimeMillis(),
                visitCount = (existing?.visitCount ?: 0) + 1
            )
        )
    }

    suspend fun toggleBookmark(title: String, url: String): Boolean {
        if (UrlUtils.isStartPage(url)) return false
        return bookmarkRepository.toggleBookmark(title, url)
    }

    suspend fun enqueueDownload(url: String, fileName: String?) {
        downloadRepository.enqueueDownload(url, fileName)
    }

    suspend fun searchLocalMatches(query: String): List<com.example.feature.browser.model.OmniboxLocalMatch> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || trimmed.startsWith("/")) return emptyList()
        val seenUrls = LinkedHashSet<String>()
        val results = mutableListOf<com.example.feature.browser.model.OmniboxLocalMatch>()

        runCatching { bookmarkRepository.searchQuick(trimmed) }.getOrDefault(emptyList()).forEach { b ->
            if (seenUrls.add(b.url)) {
                results += com.example.feature.browser.model.OmniboxLocalMatch(
                    title = b.title.ifBlank { UrlUtils.extractHost(b.url) },
                    url = b.url,
                    isBookmark = true
                )
            }
        }
        runCatching { historyRepository.searchQuick(trimmed) }.getOrDefault(emptyList()).forEach { h ->
            if (seenUrls.add(h.url) && results.size < 5) {
                results += com.example.feature.browser.model.OmniboxLocalMatch(
                    title = h.title.ifBlank { UrlUtils.extractHost(h.url) },
                    url = h.url,
                    isBookmark = false
                )
            }
        }
        return results.take(5)
    }

    suspend fun fetchSearchSuggestions(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || trimmed.startsWith("/")) return emptyList()
        return runCatching {
            suggestionApi.getSuggestions(trimmed)
                .map { it.phrase }
                .filter { it.isNotBlank() }
                .take(6)
        }.getOrDefault(emptyList())
    }
}
