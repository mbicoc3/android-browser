package com.example.feature.sitedata.model

enum class SiteDataCategory(
    val title: String,
    val shortLabel: String,
    val description: String
) {
    COOKIES(
        title = "Cookies",
        shortLabel = "Cookies",
        description = "HTTP session tokens, preferences & cross-site tracking cookies"
    ),
    LOCAL_STORAGE(
        title = "Local Storage",
        shortLabel = "Local",
        description = "Persistent origin key-value storage retained across restarts"
    ),
    SESSION_STORAGE(
        title = "Session Storage",
        shortLabel = "Session",
        description = "Per-tab temporary key-value state kept while tab is open"
    ),
    INDEXED_DB(
        title = "IndexedDB",
        shortLabel = "IndexedDB",
        description = "Structured client-side object stores & offline databases"
    ),
    CACHE(
        title = "Cache & Service Workers",
        shortLabel = "Cache",
        description = "Cached network assets, Cache API buckets & Service Workers"
    )
}

enum class SiteSortOrder(val label: String) {
    LARGEST_FIRST("Largest"),
    RECENT_FIRST("Recent"),
    NAME_ASC("Name")
}

enum class SiteFilterMode(val label: String) {
    ALL("All Sites"),
    ACTIVE_TABS("Open Tabs"),
    HAS_COOKIES("Cookies"),
    HAS_STORAGE("Storage / DB")
}

data class CookieEntry(
    val name: String,
    val valuePreview: String,
    val domain: String,
    val path: String,
    val isSecure: Boolean,
    val isHttpOnly: Boolean,
    val sameSite: String? = null,
    val rawAttributeLine: String = ""
)

data class StorageKeyValueEntry(
    val key: String,
    val valuePreview: String,
    val sizeBytes: Long
)

data class IndexedDbEntry(
    val name: String,
    val version: Int
)

data class CacheStorageEntry(
    val name: String,
    val itemCount: Int
)

data class ServiceWorkerEntry(
    val scope: String,
    val scriptUrl: String
)

data class SiteStorageSummary(
    val origin: String,
    val host: String,
    val title: String,
    val lastVisitedAt: Long,
    val visitCount: Int,
    val openTabsCount: Int,
    val cookieCount: Int,
    val localStorageCount: Int,
    val localStorageBytes: Long,
    val sessionStorageCount: Int,
    val sessionStorageBytes: Long,
    val indexedDbCount: Int,
    val cacheStorageCount: Int,
    val serviceWorkerCount: Int,
    val webStorageUsageBytes: Long,
    val webStorageQuotaBytes: Long,
    val lastInspectedAt: Long
) {
    val estimatedTotalBytes: Long
        get() = maxOf(webStorageUsageBytes, localStorageBytes + sessionStorageBytes)

    val hasAnyTrackedData: Boolean
        get() = cookieCount > 0 ||
            localStorageCount > 0 ||
            sessionStorageCount > 0 ||
            indexedDbCount > 0 ||
            cacheStorageCount > 0 ||
            serviceWorkerCount > 0 ||
            webStorageUsageBytes > 0L
}

data class SiteDetailedInspection(
    val origin: String,
    val host: String,
    val title: String,
    val isHttps: Boolean,
    val openTabsCount: Int,
    val cookies: List<CookieEntry>,
    val localStorageEntries: List<StorageKeyValueEntry>,
    val localStorageTotalBytes: Long,
    val sessionStorageEntries: List<StorageKeyValueEntry>,
    val sessionStorageTotalBytes: Long,
    val indexedDbs: List<IndexedDbEntry>,
    val cacheBuckets: List<CacheStorageEntry>,
    val serviceWorkers: List<ServiceWorkerEntry>,
    val webStorageUsageBytes: Long,
    val webStorageQuotaBytes: Long,
    val estimatedJsUsageBytes: Long,
    val estimatedJsQuotaBytes: Long,
    val probeSucceeded: Boolean,
    val lastInspectedAt: Long
) {
    val effectiveUsageBytes: Long
        get() = maxOf(
            webStorageUsageBytes,
            estimatedJsUsageBytes,
            localStorageTotalBytes + sessionStorageTotalBytes
        )
}

data class GlobalStorageMetrics(
    val totalWebViewDirBytes: Long = 0L,
    val httpCacheBytes: Long = 0L,
    val webStorageOriginsBytes: Long = 0L,
    val trackedSitesCount: Int = 0,
    val sitesWithCookiesCount: Int = 0,
    val openTabsCount: Int = 0
)

data class GlobalClearSelection(
    val clearCookies: Boolean = true,
    val clearLocalStorage: Boolean = true,
    val clearSessionStorage: Boolean = true,
    val clearIndexedDb: Boolean = true,
    val clearCache: Boolean = true
) {
    val selectedCount: Int
        get() = listOf(
            clearCookies,
            clearLocalStorage,
            clearSessionStorage,
            clearIndexedDb,
            clearCache
        ).count { it }
}
