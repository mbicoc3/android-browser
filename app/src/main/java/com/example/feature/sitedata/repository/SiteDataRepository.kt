package com.example.feature.sitedata.repository

import android.content.Context
import android.webkit.WebStorage
import com.example.core.common.UrlUtils
import com.example.core.database.dao.BrowserTabDao
import com.example.core.database.dao.HistoryDao
import com.example.core.database.dao.SiteRecordDao
import com.example.core.database.entity.SiteRecordEntity
import com.example.feature.browser.webview.OrbitWebViewPool
import com.example.feature.sitedata.engine.WebViewSiteDataExecutor
import com.example.feature.sitedata.model.GlobalClearSelection
import com.example.feature.sitedata.model.GlobalStorageMetrics
import com.example.feature.sitedata.model.SiteDataCategory
import com.example.feature.sitedata.model.SiteDetailedInspection
import com.example.feature.sitedata.model.SiteStorageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SiteDataRepository(
    private val appContext: Context,
    private val siteRecordDao: SiteRecordDao,
    private val historyDao: HistoryDao,
    private val browserTabDao: BrowserTabDao,
    private val executor: WebViewSiteDataExecutor
) {

    fun observeSiteRecords(): Flow<List<SiteRecordEntity>> = siteRecordDao.observeAll()

    suspend fun getOpenTabsOriginCounts(pool: OrbitWebViewPool): Map<String, Int> =
        withContext(Dispatchers.Main) {
            val counts = mutableMapOf<String, Int>()
            pool.getLiveWebViews().forEach { (_, webView) ->
                if (!pool.isTabIncognito(webView)) {
                    val origin = UrlUtils.extractOrigin(webView.url)
                    if (origin != null) {
                        counts[origin] = (counts[origin] ?: 0) + 1
                    }
                }
            }
            counts
        }

    suspend fun syncAllSiteTelemetry(pool: OrbitWebViewPool): Pair<GlobalStorageMetrics, Map<String, Int>> {
        val webStorageOrigins = executor.queryWebStorageOrigins()
        val openTabOrigins = getOpenTabsOriginCounts(pool)
        val liveTitles = getLiveTabTitlesByOrigin(pool)
        val existingByOrigin = siteRecordDao.getAllSnapshot().associateBy { it.origin }.toMutableMap()

        val discoveredOrigins = linkedSetOf<String>()
        discoveredOrigins.addAll(existingByOrigin.keys)
        discoveredOrigins.addAll(webStorageOrigins.keys)
        discoveredOrigins.addAll(openTabOrigins.keys)

        historyDao.getRecentSnapshot().forEach { item ->
            UrlUtils.extractOrigin(item.url)?.let { origin ->
                discoveredOrigins.add(origin)
                if (!existingByOrigin.containsKey(origin)) {
                    existingByOrigin[origin] = SiteRecordEntity(
                        origin = origin,
                        host = UrlUtils.extractHost(origin),
                        title = item.title.ifBlank { UrlUtils.extractHost(origin) },
                        lastVisitedAt = item.visitedAt,
                        visitCount = 1
                    )
                }
            }
        }

        browserTabDao.getStandardTabsSnapshot().forEach { tab ->
            UrlUtils.extractOrigin(tab.url)?.let { origin ->
                discoveredOrigins.add(origin)
                if (!existingByOrigin.containsKey(origin)) {
                    existingByOrigin[origin] = SiteRecordEntity(
                        origin = origin,
                        host = UrlUtils.extractHost(origin),
                        title = tab.title.ifBlank { UrlUtils.extractHost(origin) },
                        lastVisitedAt = tab.updatedAt,
                        visitCount = 1
                    )
                }
            }
        }

        val now = System.currentTimeMillis()
        var sitesWithCookies = 0
        for (origin in discoveredOrigins) {
            val existing = existingByOrigin[origin]
            val cookieCount = executor.countCookiesQuick(origin)
            if (cookieCount > 0) sitesWithCookies++
            val storagePair = webStorageOrigins[origin]
            val updated = (existing ?: SiteRecordEntity(
                origin = origin,
                host = UrlUtils.extractHost(origin),
                title = liveTitles[origin].orEmpty().ifBlank { UrlUtils.extractHost(origin) },
                lastVisitedAt = 0L,
                visitCount = 0
            )).copy(
                title = liveTitles[origin]?.takeIf { it.isNotBlank() } ?: existing?.title.orEmpty().ifBlank { UrlUtils.extractHost(origin) },
                cookieCount = cookieCount,
                webStorageUsageBytes = storagePair?.first ?: 0L,
                webStorageQuotaBytes = storagePair?.second ?: 0L
            )
            siteRecordDao.upsert(updated)
        }

        val (totalWebViewBytes, httpCacheBytes) = executor.computeWebViewDiskTelemetry(appContext)
        val totalOriginsBytes = webStorageOrigins.values.sumOf { it.first }
        val openTabsCount = withContext(Dispatchers.Main) { pool.getOpenTabCount() }

        val metrics = GlobalStorageMetrics(
            totalWebViewDirBytes = totalWebViewBytes,
            httpCacheBytes = httpCacheBytes,
            webStorageOriginsBytes = totalOriginsBytes,
            trackedSitesCount = discoveredOrigins.size,
            sitesWithCookiesCount = sitesWithCookies,
            openTabsCount = openTabsCount
        )
        return metrics to openTabOrigins
    }

    suspend fun inspectSiteDetailed(origin: String, pool: OrbitWebViewPool): SiteDetailedInspection {
        val normalized = UrlUtils.normalizeOrigin(origin) ?: origin
        val existing = siteRecordDao.getByOrigin(normalized)
        val cookies = executor.inspectCookies(normalized)
        val webStorageOrigins = executor.queryWebStorageOrigins()
        val storagePair = webStorageOrigins[normalized]
        val (openTabsCount, liveSnapshot) = executor.inspectOriginLive(appContext, normalized, pool)
        val now = System.currentTimeMillis()
        val host = UrlUtils.extractHost(normalized)
        val liveTitle = getLiveTabTitlesByOrigin(pool)[normalized]
        val title = liveTitle?.takeIf { it.isNotBlank() } ?: existing?.title.orEmpty().ifBlank { host }

        val updatedEntity = (existing ?: SiteRecordEntity(
            origin = normalized,
            host = host,
            title = title,
            lastVisitedAt = 0L,
            visitCount = 0
        )).copy(
            title = title,
            cookieCount = cookies.size,
            localStorageCount = if (liveSnapshot.ok) liveSnapshot.localStorageCount else existing?.localStorageCount ?: 0,
            localStorageBytes = if (liveSnapshot.ok) liveSnapshot.localStorageBytes else existing?.localStorageBytes ?: 0L,
            sessionStorageCount = if (liveSnapshot.ok) liveSnapshot.sessionStorageCount else 0,
            sessionStorageBytes = if (liveSnapshot.ok) liveSnapshot.sessionStorageBytes else 0L,
            indexedDbCount = if (liveSnapshot.ok) liveSnapshot.indexedDbs.size else existing?.indexedDbCount ?: 0,
            cacheStorageCount = if (liveSnapshot.ok) liveSnapshot.cacheBuckets.size else existing?.cacheStorageCount ?: 0,
            serviceWorkerCount = if (liveSnapshot.ok) liveSnapshot.serviceWorkers.size else existing?.serviceWorkerCount ?: 0,
            webStorageUsageBytes = storagePair?.first ?: if (liveSnapshot.ok) liveSnapshot.estimatedUsageBytes else 0L,
            webStorageQuotaBytes = storagePair?.second ?: if (liveSnapshot.ok) liveSnapshot.estimatedQuotaBytes else 0L,
            lastInspectedAt = now
        )
        siteRecordDao.upsert(updatedEntity)

        return SiteDetailedInspection(
            origin = normalized,
            host = host,
            title = title,
            isHttps = UrlUtils.isSecureHttps(normalized),
            openTabsCount = openTabsCount,
            cookies = cookies,
            localStorageEntries = liveSnapshot.localStorageEntries,
            localStorageTotalBytes = liveSnapshot.localStorageBytes,
            sessionStorageEntries = liveSnapshot.sessionStorageEntries,
            sessionStorageTotalBytes = liveSnapshot.sessionStorageBytes,
            indexedDbs = liveSnapshot.indexedDbs,
            cacheBuckets = liveSnapshot.cacheBuckets,
            serviceWorkers = liveSnapshot.serviceWorkers,
            webStorageUsageBytes = storagePair?.first ?: 0L,
            webStorageQuotaBytes = storagePair?.second ?: 0L,
            estimatedJsUsageBytes = liveSnapshot.estimatedUsageBytes,
            estimatedJsQuotaBytes = liveSnapshot.estimatedQuotaBytes,
            probeSucceeded = liveSnapshot.ok,
            lastInspectedAt = now
        )
    }

    suspend fun clearSiteCategory(
        origin: String,
        category: SiteDataCategory,
        specificItemKey: String?,
        pool: OrbitWebViewPool
    ): SiteDetailedInspection {
        val normalized = UrlUtils.normalizeOrigin(origin) ?: origin
        executor.clearSiteCategory(appContext, normalized, category, specificItemKey, pool)
        return inspectSiteDetailed(normalized, pool)
    }

    suspend fun clearAllForSite(origin: String, pool: OrbitWebViewPool) {
        val normalized = UrlUtils.normalizeOrigin(origin) ?: origin
        executor.clearAllForSite(appContext, normalized, pool)
        val openTabs = getOpenTabsOriginCounts(pool)[normalized] ?: 0
        if (openTabs > 0) {
            val existing = siteRecordDao.getByOrigin(normalized)
            if (existing != null) {
                siteRecordDao.upsert(
                    existing.copy(
                        cookieCount = 0,
                        localStorageCount = 0,
                        localStorageBytes = 0L,
                        sessionStorageCount = 0,
                        sessionStorageBytes = 0L,
                        indexedDbCount = 0,
                        cacheStorageCount = 0,
                        serviceWorkerCount = 0,
                        webStorageUsageBytes = 0L,
                        lastInspectedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            siteRecordDao.deleteByOrigin(normalized)
        }
    }

    suspend fun clearGlobalSelection(
        selection: GlobalClearSelection,
        pool: OrbitWebViewPool
    ): Pair<GlobalStorageMetrics, Map<String, Int>> {
        if (selection.clearCookies &&
            selection.clearLocalStorage &&
            selection.clearIndexedDb &&
            selection.clearCache
        ) {
            executor.clearAllBrowsingDataCompat(appContext)
            executor.clearAllCookies()
            executor.clearHttpAndAppCacheGlobally(appContext, pool)
            if (selection.clearSessionStorage) {
                executor.clearSessionStorageAllTabs(pool)
            }
            siteRecordDao.resetAllStorageTelemetry(System.currentTimeMillis())
            return syncAllSiteTelemetry(pool)
        }

        if (selection.clearCookies) {
            executor.clearAllCookies()
        }
        if (selection.clearSessionStorage) {
            executor.clearSessionStorageAllTabs(pool)
        }
        if (selection.clearLocalStorage && selection.clearIndexedDb) {
            withContext(Dispatchers.Main) {
                runCatching { WebStorage.getInstance().deleteAllData() }
            }
            pool.getLiveWebViews().forEach { (_, webView) ->
                val origin = withContext(Dispatchers.Main) { UrlUtils.extractOrigin(webView.url) }
                if (origin != null) {
                    executor.clearSiteCategory(appContext, origin, SiteDataCategory.LOCAL_STORAGE, null, pool)
                    executor.clearSiteCategory(appContext, origin, SiteDataCategory.INDEXED_DB, null, pool)
                }
            }
        } else {
            val allKnownOrigins = collectOriginsForSelectiveGlobalClear(pool)
            if (selection.clearLocalStorage) {
                allKnownOrigins.forEach { origin ->
                    executor.clearSiteCategory(appContext, origin, SiteDataCategory.LOCAL_STORAGE, null, pool)
                }
            }
            if (selection.clearIndexedDb) {
                allKnownOrigins.forEach { origin ->
                    executor.clearSiteCategory(appContext, origin, SiteDataCategory.INDEXED_DB, null, pool)
                }
            }
        }
        if (selection.clearCache) {
            executor.clearHttpAndAppCacheGlobally(appContext, pool)
            val cacheOrigins = siteRecordDao.getAllSnapshot()
                .filter { it.cacheStorageCount > 0 || it.serviceWorkerCount > 0 || it.webStorageUsageBytes > 0L }
                .map { it.origin }
                .toMutableSet()
            getOpenTabsOriginCounts(pool).keys.forEach { cacheOrigins.add(it) }
            cacheOrigins.forEach { origin ->
                executor.clearSiteCategory(appContext, origin, SiteDataCategory.CACHE, null, pool)
            }
        }

        val now = System.currentTimeMillis()
        siteRecordDao.getAllSnapshot().forEach { row ->
            siteRecordDao.upsert(
                row.copy(
                    cookieCount = if (selection.clearCookies) 0 else row.cookieCount,
                    localStorageCount = if (selection.clearLocalStorage) 0 else row.localStorageCount,
                    localStorageBytes = if (selection.clearLocalStorage) 0L else row.localStorageBytes,
                    sessionStorageCount = if (selection.clearSessionStorage) 0 else row.sessionStorageCount,
                    sessionStorageBytes = if (selection.clearSessionStorage) 0L else row.sessionStorageBytes,
                    indexedDbCount = if (selection.clearIndexedDb) 0 else row.indexedDbCount,
                    cacheStorageCount = if (selection.clearCache) 0 else row.cacheStorageCount,
                    serviceWorkerCount = if (selection.clearCache) 0 else row.serviceWorkerCount,
                    lastInspectedAt = now
                )
            )
        }

        return syncAllSiteTelemetry(pool)
    }

    private suspend fun collectOriginsForSelectiveGlobalClear(pool: OrbitWebViewPool): Set<String> {
        val targets = linkedSetOf<String>()
        getOpenTabsOriginCounts(pool).keys.forEach { targets.add(it) }
        executor.queryWebStorageOrigins().keys.forEach { targets.add(it) }
        siteRecordDao.getAllSnapshot()
            .filter { it.webStorageUsageBytes > 0L || it.localStorageCount > 0 || it.indexedDbCount > 0 }
            .forEach { targets.add(it.origin) }
        return targets
    }

    private suspend fun getLiveTabTitlesByOrigin(pool: OrbitWebViewPool): Map<String, String> =
        withContext(Dispatchers.Main) {
            val map = mutableMapOf<String, String>()
            pool.getLiveWebViews().forEach { (_, webView) ->
                if (!pool.isTabIncognito(webView)) {
                    val origin = UrlUtils.extractOrigin(webView.url)
                    val title = webView.title?.trim().orEmpty()
                    if (origin != null && title.isNotBlank()) {
                        map[origin] = title
                    }
                }
            }
            map
        }

    companion object {
        fun mapEntitiesToSummaries(
            entities: List<SiteRecordEntity>,
            openTabsByOrigin: Map<String, Int>
        ): List<SiteStorageSummary> = entities.map { entity ->
            SiteStorageSummary(
                origin = entity.origin,
                host = entity.host,
                title = entity.title,
                lastVisitedAt = entity.lastVisitedAt,
                visitCount = entity.visitCount,
                openTabsCount = openTabsByOrigin[entity.origin] ?: 0,
                cookieCount = entity.cookieCount,
                localStorageCount = entity.localStorageCount,
                localStorageBytes = entity.localStorageBytes,
                sessionStorageCount = entity.sessionStorageCount,
                sessionStorageBytes = entity.sessionStorageBytes,
                indexedDbCount = entity.indexedDbCount,
                cacheStorageCount = entity.cacheStorageCount,
                serviceWorkerCount = entity.serviceWorkerCount,
                webStorageUsageBytes = entity.webStorageUsageBytes,
                webStorageQuotaBytes = entity.webStorageQuotaBytes,
                lastInspectedAt = entity.lastInspectedAt
            )
        }
    }
}
